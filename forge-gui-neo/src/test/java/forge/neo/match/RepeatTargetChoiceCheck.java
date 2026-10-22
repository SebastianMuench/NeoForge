package forge.neo.match;

import java.util.List;

import forge.game.zone.ZoneType;

/** Focused state checks; run with java -ea after compiling the Neo module. */
public final class RepeatTargetChoiceCheck {
    private RepeatTargetChoiceCheck() {
    }

    private static final RepeatTargetChoice.Key BRAIN_FREEZE =
            new RepeatTargetChoice.Key("id:10", 100, "Mill|NumCards=3", "Player|new targets");
    private static final RepeatTargetChoice.Key POPULATE =
            new RepeatTargetChoice.Key("name:Nesting Dovehawk", 0, "CopyPermanent|Populate",
                    "effect|Choose a creature token");
    private static final RepeatTargetChoice.Pick OPPONENT =
            new RepeatTargetChoice.Pick(true, 2, 0, null);
    private static final RepeatTargetChoice.Pick TOKEN =
            new RepeatTargetChoice.Pick(false, 30, 300, ZoneType.Battlefield);

    public static void main(final String[] args) {
        final RepeatTargetChoice memory = new RepeatTargetChoice();
        memory.begin(BRAIN_FREEZE);
        check(!memory.isEnabled() && memory.matching(BRAIN_FREEZE, List.of(OPPONENT)) == null,
                "disabled by default");
        memory.finish(BRAIN_FREEZE, OPPONENT);
        check(!memory.hasRemembered(), "manual choice without opt-in does not repeat");

        memory.begin(BRAIN_FREEZE);
        memory.setEnabled(true);
        check(OPPONENT.equals(memory.matching(BRAIN_FREEZE, List.of(OPPONENT))),
                "the previous manual pick can be enabled on the next prompt");
        memory.setEnabled(false);

        memory.begin(BRAIN_FREEZE);
        memory.setEnabled(true);
        memory.finish(BRAIN_FREEZE, OPPONENT);
        memory.begin(BRAIN_FREEZE); // another copy of the same spell
        check(OPPONENT.equals(memory.matching(BRAIN_FREEZE,
                List.of(new RepeatTargetChoice.Pick(true, 3, 0, null), OPPONENT))),
                "player matched by identity, regardless of option order");
        memory.setEnabled(false);
        check(memory.matching(BRAIN_FREEZE, List.of(OPPONENT)) == null,
                "unchecking stops an active sequence");

        memory.begin(POPULATE);
        memory.setEnabled(true);
        memory.finish(POPULATE, TOKEN);
        memory.begin(POPULATE);
        check(TOKEN.equals(memory.matching(POPULATE, List.of(TOKEN))),
                "same permanent can be repeated");
        check(memory.matching(POPULATE, List.of(new RepeatTargetChoice.Pick(
                false, 31, 301, ZoneType.Battlefield))) == null && !memory.isEnabled(),
                "another permanent with the same name is not substituted");

        memory.begin(POPULATE);
        memory.setEnabled(true);
        memory.finish(POPULATE, TOKEN);
        memory.begin(POPULATE);
        check(memory.matching(POPULATE, List.of(new RepeatTargetChoice.Pick(
                false, 30, 302, ZoneType.Battlefield))) == null && !memory.isEnabled(),
                "a permanent that left and returned is a new object");

        memory.begin(BRAIN_FREEZE);
        memory.setEnabled(true);
        memory.finish(BRAIN_FREEZE, OPPONENT);
        memory.begin(BRAIN_FREEZE);
        check(memory.matching(BRAIN_FREEZE, List.of()) == null && !memory.isEnabled(),
                "illegal or absent target falls back to manual choice");

        memory.begin(BRAIN_FREEZE);
        memory.setEnabled(true);
        memory.finish(BRAIN_FREEZE, OPPONENT);
        memory.begin(new RepeatTargetChoice.Key("id:10", 100, "Different ability", "Player|new targets"));
        check(!memory.isEnabled(), "similar prompt from another ability stops repetition");

        memory.begin(POPULATE);
        memory.setEnabled(true);
        memory.finish(POPULATE, TOKEN);
        memory.clear(); // last item left the stack
        memory.begin(POPULATE);
        check(!memory.isEnabled() && memory.matching(POPULATE, List.of(TOKEN)) == null,
                "later stacks and turns start without a remembered choice");
        System.out.println("RepeatTargetChoice checks passed");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
