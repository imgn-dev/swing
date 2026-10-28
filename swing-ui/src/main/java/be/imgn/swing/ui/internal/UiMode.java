package be.imgn.swing.ui.internal;

/**
 * How strictly the framework reports the mistakes the build cannot see, such as duplicate mnemonics and shortcuts: with
 * an exception in development mode ({@code -Dbe.imgn.swing.dev=true}) and in tests, with a warning otherwise.
 *
 * @param strict whether mistakes throw
 */
public record UiMode(boolean strict) {

    /** The system property of development mode. */
    public static final String DEV_PROPERTY = "be.imgn.swing.dev";

    /**
     * Returns the mode of the running JVM.
     *
     * @return strict in development mode
     */
    public static UiMode fromSystemProperties() {
        return new UiMode(Boolean.getBoolean(DEV_PROPERTY));
    }

    /**
     * Reports a mistake: throws if strict, logs a warning otherwise.
     *
     * @param logger the logger of the class that reports
     * @param message the message
     * @throws IllegalStateException if strict
     */
    public void report(System.Logger logger, String message) {
        if (strict) {
            throw new IllegalStateException(message);
        }
        logger.log(System.Logger.Level.WARNING, message);
    }
}
