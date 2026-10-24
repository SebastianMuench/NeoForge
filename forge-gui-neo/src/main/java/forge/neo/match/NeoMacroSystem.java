package forge.neo.match;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.actions.ActivateAbilityAction;
import forge.game.player.actions.PlayerAction;
import forge.game.player.actions.SelectCardAction;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.Input;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gamemodes.match.input.InputSelectEntitiesFromList;
import forge.gamemodes.match.input.InputSelectTargets;
import forge.gui.FThreads;
import forge.neo.NeoSettings;
import forge.player.PlayerControllerHuman;
import forge.player.RecordActionsMacroSystem;

/** Extends Forge's selection resolution, retaining its recorder and playback scheduler. */
final class NeoMacroSystem extends RecordActionsMacroSystem {
    private final PlayerControllerHuman controller;
    private final Map<PlayerAction, Pick> recorded = new IdentityHashMap<>();
    private boolean resolving;
    private boolean cancelRequested;
    private Runnable cancelDialog;

    /** Snapshot: CardView is mutable when a card changes zones. */
    record Pick(int id, String name, String oracle, Integer owner, Integer controller,
                ZoneType zone, boolean token, boolean faceDown) {
        static Pick of(final CardView view) {
            return new Pick(view.getId(), view.getName(), view.getOracleName(),
                    view.getOwner() == null ? null : view.getOwner().getId(),
                    view.getController() == null ? null : view.getController().getId(),
                    view.getZone(), view.isToken(), view.isFaceDown());
        }

        boolean equivalent(final Pick other) {
            return !faceDown && !other.faceDown && !token && !other.token
                    && name != null && !name.isBlank()
                    && name.equals(other.name) && Objects.equals(oracle, other.oracle)
                    && Objects.equals(owner, other.owner) && Objects.equals(controller, other.controller)
                    && zone != null && zone == other.zone;
        }
    }

    /** Exact object first, otherwise one equivalent candidate. Never use list order as a tie breaker. */
    static int matchingIndex(final Pick wanted, final List<Pick> legal) {
        for (int i = 0; i < legal.size(); i++) {
            if (wanted.id == legal.get(i).id) {
                return i;
            }
        }
        int found = -1;
        for (int i = 0; i < legal.size(); i++) {
            if (wanted.equivalent(legal.get(i))) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return found;
    }

    private NeoMacroSystem(final PlayerControllerHuman controller) {
        super(controller);
        this.controller = controller;
    }

    /**
     * Like the shared YieldController in SafeActions, install on the original controller:
     * engine inputs and Neo's relay must see the same macro, including private engine checks.
     * Never replace an existing recorder (and its recorded actions).
     */
    static void install(final PlayerControllerHuman owner) {
        try {
            final Field field = PlayerControllerHuman.class.getDeclaredField("macros");
            field.setAccessible(true);
            if (field.get(owner) == null && !owner.isRemoteClient()) {
                field.set(owner, new NeoMacroSystem(owner));
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.err.println("[neo] lenient macro playback unavailable: " + e.getMessage());
        }
    }

    @Override
    public boolean startRecording() {
        final boolean started = super.startRecording();
        if (started) {
            recorded.clear();
        }
        return started;
    }

    @Override
    public void addRememberedAction(final PlayerAction action) {
        if (isRecording() && action.getGameEntityView() instanceof CardView view
                && (action instanceof SelectCardAction || action instanceof ActivateAbilityAction)) {
            recorded.put(action, Pick.of(view));
        }
        super.addRememberedAction(action);
    }

    @Override
    public void cancelPlayback() {
        if (resolving) {
            // The engine removes the accepted action when processAction returns. Clearing
            // its list inside a nested dialog would invalidate that index.
            cancelRequested = true;
            if (cancelDialog != null) {
                cancelDialog.run();
            }
        } else {
            super.cancelPlayback();
        }
    }

    @Override
    public boolean processAction(final PlayerAction action) {
        final Input input = controller.getInputProxy().getInput();
        if (!NeoSettings.lenientMacroPlayback()
                || !(action instanceof SelectCardAction || action instanceof ActivateAbilityAction)
                || !(action.getGameEntityView() instanceof CardView view)
                || !(input instanceof InputSelectEntitiesFromList<?> || input instanceof InputSelectTargets
                        || input instanceof InputPassPriority)) {
            return super.processAction(action);
        }
        resolving = true;
        cancelRequested = false;
        try {
            final List<Card> legal = legalCards(input, action);
            final Pick wanted = recorded.getOrDefault(action, Pick.of(view));
            final List<Pick> picks = new ArrayList<>();
            for (final Card card : legal) {
                picks.add(Pick.of(card.getView()));
            }
            final int match = matchingIndex(wanted, picks);
            if (match >= 0 && select(input, action, legal.get(match))) {
                return true;
            }
            if (cancelRequested) {
                return true;
            }
            final NeoMatchUI ui = NeoMatchUI.of(controller.getGui());
            if (ui == null) {
                cancelRequested = true;
                return true;
            }
            final Card chosen = ui.chooseMacroCard(legal, cancel -> cancelDialog = cancel);
            if (!cancelRequested && chosen != null && controller.getInputProxy().getInput() == input
                    && legalCards(input, action).contains(chosen) && select(input, action, chosen)) {
                return true;
            }
            // A cancelled/stale prompt cannot safely consume further recorded actions.
            cancelRequested = true;
            return true;
        } finally {
            resolving = false;
            cancelDialog = null;
            if (cancelRequested) {
                FThreads.delayInEDT(0, super::cancelPlayback);
            }
        }
    }

    private List<Card> legalCards(final Input input, final PlayerAction action) {
        final List<Card> out = new ArrayList<>();
        if (input instanceof InputSelectEntitiesFromList<?> list) {
            for (final var entity : list.getValidChoices()) {
                if (entity instanceof Card card && !list.getSelected().contains(card)) {
                    out.add(card);
                }
            }
        } else {
            for (final Card card : controller.getGame().getCardsInGame()) {
                if (input.getActivateAction(card) != null && (!(input instanceof InputPassPriority)
                        || !abilities(card, action).isEmpty())) {
                    out.add(card);
                }
            }
        }
        return out;
    }

    private List<SpellAbility> abilities(final Card card, final PlayerAction action) {
        final List<SpellAbility> out = new ArrayList<>();
        for (final SpellAbility ability : card.getAllPossibleAbilities(controller.getPlayer(), true)) {
            if (ability.canPlay() && (!(action instanceof ActivateAbilityAction activate)
                    || ability.toUnsuppressedString().equals(activate.getAbilityDescription()))) {
                out.add(ability);
            }
        }
        return out;
    }

    private boolean select(final Input input, final PlayerAction action, final Card card) {
        if (input instanceof InputSelectTargets targets) {
            return targets.selectCardForMacro(card, null);
        }
        if (input instanceof InputPassPriority priority) {
            final List<SpellAbility> abilities = abilities(card, action);
            if (abilities.size() == 1) {
                return priority.selectAbility(abilities.get(0));
            }
            // Forge/Neo's existing ability chooser handles multiple abilities.
            return !(action instanceof ActivateAbilityAction)
                    && controller.selectCard(card.getView(), null, null) && priority.getChosenSa() != null;
        }
        return controller.selectCard(card.getView(), null, null);
    }
}
