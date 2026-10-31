package be.imgn.swing.ui;

/** What is blocked while a task runs. */
public enum Block {
    /** Nothing is blocked. */
    NONE,
    /** The command that started the task is disabled. */
    COMMAND,
    /**
     * The component the command was activated from is disabled: the button for a button click, the invoker for a popup
     * menu selection, the focus owner for a shortcut. For the other triggers, the command is disabled instead.
     */
    COMPONENT,
    /**
     * A glass pane covers the window: it consumes mouse and key events, shows a busy cursor and, after 300 ms, a
     * progress indicator with a Cancel button.
     */
    WINDOW,
    /** The same glass pane on every window. */
    APPLICATION
}
