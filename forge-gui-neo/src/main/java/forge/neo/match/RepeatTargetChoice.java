package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;

/** One explicitly chosen card or player target, for the current stack only. */
final class RepeatTargetChoice {

    record Key(String source, long sourceTimestamp, String ability, String targeting) {
    }

    record Pick(boolean player, int id, long timestamp, ZoneType zone) {
    }

    private Key active;
    private Key rememberedFor;
    private Pick remembered;
    private boolean enabled;

    /** Copies of a spell use the original spell card as their source. */
    static Key key(final SpellAbility sa, final boolean retarget) {
        if (sa == null || !sa.usesTargeting() || sa.isDividedAsYouChoose()
                || sa.getMinTargets() != 1 || sa.getMaxTargets() != 1) {
            return null;
        }
        final TargetRestrictions restrictions = sa.getTargetRestrictions();
        if (restrictions == null || restrictions.isRandomTarget()
                || restrictions.getZone().contains(ZoneType.Stack)) {
            return null;
        }
        int step = 0;
        for (SpellAbility parent = sa.getParent(); parent != null; parent = parent.getParent()) {
            step++;
        }
        final String targeting = Arrays.toString(restrictions.getValidTgts())
                + "|" + restrictions.getZone() + "|" + restrictions.getVTSelection()
                + "|" + step + "|" + retarget;
        return key(sa, targeting, sa.getRootAbility().getTrigger() != null);
    }

    /** Non-targeting choices retain their own prompt semantics. */
    static Key choiceKey(final SpellAbility sa, final String prompt) {
        return sa == null || prompt == null ? null : key(sa, "effect|" + prompt, true);
    }

    private static Key key(final SpellAbility sa, final String selection,
                           final boolean equivalentSources) {
        Card source = sa.getHostCard();
        if (source == null) {
            return null;
        }
        while (source.isCopiedSpell() && source.getCopiedPermanent() != null
                && source.getCopiedPermanent() != source) {
            source = source.getCopiedPermanent();
        }
        final String ability = Objects.toString(sa.getRootAbility().getOriginalDescription(), "")
                + "|" + Objects.toString(sa.getOriginalDescription(), "")
                + "|" + sa.getApi() + "|" + new TreeMap<>(sa.getMapParams())
                + "|" + (sa.getRootAbility().getTrigger() == null ? ""
                        : new TreeMap<>(sa.getRootAbility().getTrigger().getMapParams()));
        return new Key(equivalentSources ? "name:" + source.getName() : "id:" + source.getId(),
                equivalentSources ? 0 : source.getGameTimestamp(), ability, selection);
    }

    static Pick pick(final GameEntity entity) {
        if (entity instanceof Card card) {
            return new Pick(false, card.getId(), card.getGameTimestamp(),
                    card.getZone() == null ? null : card.getZone().getZoneType());
        }
        if (entity instanceof Player player) {
            return new Pick(true, player.getId(), 0, null);
        }
        return null;
    }

    synchronized void begin(final Key key) {
        if (key == null || rememberedFor != null && !rememberedFor.equals(key)) {
            disable();
        }
        active = key;
    }

    synchronized void setEnabled(final boolean on) {
        if (on && active != null) {
            enabled = true;
        } else {
            disable();
        }
    }

    synchronized void finish(final Key key, final Pick selected) {
        if (key != null && key.equals(active) && selected != null) {
            // Keep the last manual pick ready; it is never replayed until opted in.
            rememberedFor = key;
            remembered = selected;
        } else if (selected == null) {
            disable();
        }
        active = null;
    }

    /** An exact match only; a different option never replaces the chosen one. */
    synchronized Pick matching(final Key key, final List<Pick> candidates) {
        if (enabled && remembered != null && key != null && key.equals(active)
                && key.equals(rememberedFor)) {
            if (candidates.contains(remembered)) {
                return remembered;
            }
            disable();
        }
        return null;
    }

    synchronized boolean isOffered() {
        return active != null;
    }

    synchronized boolean isEnabled() {
        return enabled;
    }

    synchronized boolean hasRemembered() {
        return enabled && remembered != null;
    }

    synchronized Key activeKey() {
        return active;
    }

    synchronized void disable() {
        enabled = false;
        rememberedFor = null;
        remembered = null;
    }

    synchronized void clear() {
        disable();
        active = null;
    }
}
