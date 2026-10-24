package forge.neo.match;

import java.nio.file.Files;
import java.util.List;

import forge.game.card.CardView;
import forge.game.zone.ZoneType;
import forge.neo.NeoSettings;
import forge.trackable.TrackableProperty;

/** Focused regression checks; run this main after compiling the Neo module. */
public final class NeoMacroSystemCheck {
    private NeoMacroSystemCheck() {
    }

    private static NeoMacroSystem.Pick card(final int id, final String name, final int owner,
                                           final int controller, final ZoneType zone,
                                           final boolean token, final boolean faceDown) {
        return new NeoMacroSystem.Pick(id, name, name, owner, controller, zone, token, faceDown);
    }

    public static void main(final String[] args) throws Exception {
        final var hand = card(10, "Forest", 1, 1, ZoneType.Hand, false, false);
        final var returned = card(20, "Forest", 1, 1, ZoneType.Hand, false, false);
        final var duplicate = card(30, "Forest", 1, 1, ZoneType.Hand, false, false);
        final var island = card(40, "Island", 1, 1, ZoneType.Hand, false, false);
        check(NeoMacroSystem.matchingIndex(hand, List.of(returned, hand, duplicate)) == 1,
                "original legal object takes priority over equivalent copies");
        check(NeoMacroSystem.matchingIndex(hand, List.of(island, returned)) == 1,
                "a land returned to hand with a new identity can be matched");
        check(NeoMacroSystem.matchingIndex(hand, List.of(returned, duplicate)) == -1
                        && NeoMacroSystem.matchingIndex(hand, List.of(duplicate, returned)) == -1,
                "multiple copies require a human regardless of legal option order");
        check(NeoMacroSystem.matchingIndex(hand, List.of(island)) == -1
                        && NeoMacroSystem.matchingIndex(hand, List.of()) == -1,
                "no arbitrary fallback when the recorded choice is unavailable");
        check(NeoMacroSystem.matchingIndex(hand, List.of(
                card(20, "Forest", 2, 1, ZoneType.Hand, false, false),
                card(21, "Forest", 1, 2, ZoneType.Hand, false, false),
                card(22, "Forest", 1, 1, ZoneType.Battlefield, false, false),
                card(23, "Forest", 1, 1, ZoneType.Hand, true, false),
                card(24, "Forest", 1, 1, ZoneType.Hand, false, true))) == -1,
                "owner, controller, zone, tokens and hidden identity constrain equivalence");
        final var token = card(50, "Soldier", 1, 1, ZoneType.Battlefield, true, false);
        check(NeoMacroSystem.matchingIndex(token, List.of(
                card(51, "Soldier", 1, 1, ZoneType.Battlefield, true, false))) == -1,
                "token names do not establish equivalent characteristics");
        check(NeoMacroSystem.matchingIndex(token, List.of(token)) == 0,
                "the original token can still be selected");

        final CardView view = new CardView(10, null, "Forest");
        view.set(TrackableProperty.Zone, ZoneType.Hand);
        final var snapshot = NeoMacroSystem.Pick.of(view);
        view.set(TrackableProperty.Zone, ZoneType.Graveyard);
        view.set(TrackableProperty.Name, "Island");
        check(snapshot.zone() == ZoneType.Hand && snapshot.name().equals("Forest"),
                "recording preserves characteristics before the live view changes");

        final var settings = Files.createTempFile("neo-macro-settings", ".properties");
        final String before = System.getProperty("neo.settingsFile");
        try {
            System.setProperty("neo.settingsFile", settings.toString());
            NeoSettings.reload();
            check(!NeoSettings.lenientMacroPlayback(), "strict playback is the default");
            NeoSettings.setBool(NeoSettings.LENIENT_MACRO_PLAYBACK, true);
            NeoSettings.save();
            NeoSettings.reload();
            check(NeoSettings.lenientMacroPlayback(), "lenient preference persists");
        } finally {
            Files.deleteIfExists(settings);
            if (before == null) {
                System.clearProperty("neo.settingsFile");
            } else {
                System.setProperty("neo.settingsFile", before);
            }
        }
        System.out.println("NeoMacroSystem checks passed");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
