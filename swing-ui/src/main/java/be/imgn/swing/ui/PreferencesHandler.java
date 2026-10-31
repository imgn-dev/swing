package be.imgn.swing.ui;

/**
 * Shows the application's preferences: a singleton bean of the application. Without one, the standard
 * {@code preferences} command is disabled.
 */
public interface PreferencesHandler {

    /** Shows the preferences; called on the EDT. */
    void showPreferences();
}
