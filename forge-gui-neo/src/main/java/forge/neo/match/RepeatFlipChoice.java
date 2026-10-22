package forge.neo.match;

/** An explicitly chosen coin-flip result, scoped to the current stack. */
final class RepeatFlipChoice {
    private RepeatTargetChoice.Key active;
    private RepeatTargetChoice.Key rememberedFor;
    private Boolean remembered;
    private boolean enabled;

    synchronized void begin(final RepeatTargetChoice.Key key) {
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

    synchronized Boolean matching(final RepeatTargetChoice.Key key) {
        return enabled && key != null && key.equals(active) && key.equals(rememberedFor)
                ? remembered : null;
    }

    synchronized void finish(final RepeatTargetChoice.Key key, final Boolean selected) {
        if (key != null && key.equals(active) && selected != null) {
            rememberedFor = key;
            remembered = selected;
        } else {
            disable();
        }
        active = null;
    }

    synchronized boolean isEnabled() {
        return enabled;
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
