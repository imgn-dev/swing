package be.imgn.swing.testing;

import static java.util.Objects.requireNonNull;

import be.imgn.swing.ui.TaskbarStatus;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/** The tester's {@link TaskbarStatus}: it records the current state, and checks its arguments as the real one does. */
public final class FakeTaskbar implements TaskbarStatus {

    private static final int PERCENT = 100;
    private static final int NONE = -1;

    private int progress = NONE;
    private @Nullable String badge;
    private int attentionRequests;

    FakeTaskbar() {}

    @Override
    public synchronized void progress(int percent) {
        if (percent < 0 || percent > PERCENT) {
            throw new IllegalArgumentException("The progress must be between 0 and 100: " + percent);
        }
        progress = percent;
    }

    /**
     * Returns the progress shown.
     *
     * @return the progress, empty when none is shown
     */
    public synchronized OptionalInt progress() {
        return progress == NONE ? OptionalInt.empty() : OptionalInt.of(progress);
    }

    @Override
    public synchronized void clearProgress() {
        progress = NONE;
    }

    @Override
    public synchronized void badge(String text) {
        requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("The badge must not be blank");
        }
        badge = text;
    }

    /**
     * Returns the badge shown.
     *
     * @return the badge, empty when none is shown
     */
    public synchronized Optional<String> badge() {
        return Optional.ofNullable(badge);
    }

    @Override
    public synchronized void clearBadge() {
        badge = null;
    }

    @Override
    public synchronized void requestAttention() {
        attentionRequests++;
    }

    /**
     * Returns how many times the application asked for the user's attention.
     *
     * @return the count
     */
    public synchronized int attentionRequests() {
        return attentionRequests;
    }
}
