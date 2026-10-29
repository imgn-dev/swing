package be.imgn.swing.ui.internal;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * How strictly the framework reports the mistakes the build cannot see, such as duplicate mnemonics and shortcuts: with
 * an exception in development mode ({@code -Dbe.imgn.swing.dev=true}) and in tests, with a warning otherwise.
 *
 * @param strict whether mistakes throw
 * @param failures told of every mistake a strict mode throws, before the throw: a test harness records them there,
 *     since a mistake found while rebuilding a menu is thrown on the EDT, where no caller waits for it
 */
public record UiMode(boolean strict, Consumer<? super IllegalStateException> failures) {

    /** The system property of development mode. */
    public static final String DEV_PROPERTY = "be.imgn.swing.dev";

    /**
     * Creates a mode.
     *
     * @param strict whether mistakes throw
     * @param failures told of every mistake a strict mode throws
     */
    public UiMode {
        Objects.requireNonNull(failures, "failures");
    }

    /**
     * Creates a mode that tells nobody of the mistakes it throws.
     *
     * @param strict whether mistakes throw
     */
    public UiMode(boolean strict) {
        this(strict, failure -> {});
    }

    /**
     * Returns the mode of the running JVM.
     *
     * @return strict in development mode
     */
    public static UiMode fromSystemProperties() {
        return new UiMode(Boolean.getBoolean(DEV_PROPERTY));
    }

    /**
     * Reports a mistake: throws if strict, after telling {@link #failures()}; logs a warning otherwise.
     *
     * @param logger the logger of the class that reports
     * @param message the message
     * @throws IllegalStateException if strict
     */
    public void report(System.Logger logger, String message) {
        if (strict) {
            IllegalStateException failure = new IllegalStateException(message);
            failures.accept(failure);
            throw failure;
        }
        logger.log(System.Logger.Level.WARNING, message);
    }
}
