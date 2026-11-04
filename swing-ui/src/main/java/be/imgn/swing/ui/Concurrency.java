package be.imgn.swing.ui;

/**
 * What an activation of a command does while a task the command started still runs. The handler is called at every
 * activation, whatever the concurrency. A concurrency other than {@link #PARALLEL} requires a handler that returns a
 * {@code Task}, and excludes {@link Block#COMMAND} and {@link Block#COMPONENT}.
 */
public enum Concurrency {
    /** The new activation starts its task at once. */
    PARALLEL,
    /**
     * The new activation cancels the command's tasks that haven't ended, then starts its own once their
     * {@code onCancel} and {@code onEnd} hooks have run. A handler that returns {@code Task.none()} or throws cancels
     * nothing.
     */
    RESTART,
    /**
     * The new activation starts its task once every task the command started before it has ended, in activation order.
     * A waiting task holds no thread and blocks no input; cancelled while it waits, it never starts, and runs its
     * {@code onCancel} and {@code onEnd} hooks.
     */
    SERIAL
}
