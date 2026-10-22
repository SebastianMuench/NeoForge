package forge.neo.match;

/** Focused state checks; run with java -ea after compiling the Neo module. */
public final class RepeatFlipChoiceCheck {
    private RepeatFlipChoiceCheck() {
    }

    public static void main(final String[] args) {
        final RepeatTargetChoice.Key headsOrTails = new RepeatTargetChoice.Key(
                "name:Coin Flipper", 0, "FlipCoin", "effect|flip-result|true");
        final RepeatTargetChoice.Key winOrLose = new RepeatTargetChoice.Key(
                "name:Coin Flipper", 0, "FlipCoin", "effect|flip-result|false");
        final RepeatFlipChoice repeat = new RepeatFlipChoice();

        repeat.begin(headsOrTails);
        check(repeat.matching(headsOrTails) == null, "disabled by default");
        repeat.finish(headsOrTails, false);
        repeat.begin(headsOrTails);
        repeat.setEnabled(true);
        check(Boolean.FALSE.equals(repeat.matching(headsOrTails)),
                "the exact previous result can be enabled on the next prompt");
        repeat.finish(headsOrTails, false);
        repeat.begin(headsOrTails);
        check(Boolean.FALSE.equals(repeat.matching(headsOrTails)),
                "tails repeats on an equivalent flip");
        repeat.setEnabled(false);
        check(repeat.matching(headsOrTails) == null, "unchecking stops repetition");

        repeat.begin(headsOrTails);
        repeat.setEnabled(true);
        repeat.finish(headsOrTails, true);
        repeat.begin(winOrLose);
        check(repeat.matching(winOrLose) == null && !repeat.isEnabled(),
                "a win/lose choice is not the same as a heads/tails choice");

        repeat.begin(headsOrTails);
        repeat.setEnabled(true);
        repeat.finish(headsOrTails, true);
        final RepeatTargetChoice.Key otherAbility = new RepeatTargetChoice.Key(
                "name:Another Coin Flipper", 0, "FlipCoin", "effect|flip-result|true");
        repeat.begin(otherAbility);
        check(repeat.matching(otherAbility) == null && !repeat.isEnabled(),
                "a similar prompt from another ability does not repeat");

        repeat.begin(headsOrTails);
        repeat.setEnabled(true);
        repeat.finish(headsOrTails, true);
        repeat.clear();
        repeat.begin(headsOrTails);
        check(repeat.matching(headsOrTails) == null && !repeat.isEnabled(),
                "the next stack starts without a remembered result");
        System.out.println("RepeatFlipChoice checks passed");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
