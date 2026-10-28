package be.imgn.swing.ui;

/** A modifier key held when a command was activated. */
public enum Modifier {
    /** Shift. */
    SHIFT,
    /** Control. */
    CONTROL,
    /** Alt, or Option on macOS. */
    ALT,
    /** Meta, or Command on macOS. */
    META,
    /** The platform's menu shortcut modifier, set with {@link #CONTROL} or {@link #META}, whichever it is. */
    SHORTCUT
}
