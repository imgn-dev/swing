package be.imgn.swing.ui;

/**
 * Shows the application's preferences: a singleton bean of the application. Without one, the standard
 * {@code preferences} command is disabled.
 */
public interface PreferencesHandler {

    /**
     * Shows the preferences; called on the EDT. The handler uses {@code ui} during the call, such as to open a dialog
     * view with {@link Ui#showDialog(Class)}, and must not keep it.
     *
     * @param ui the {@link Ui} of the view whose {@code preferences()} command runs; on macOS's application menu, the
     *     main view's
     */
    void showPreferences(Ui ui);
}
