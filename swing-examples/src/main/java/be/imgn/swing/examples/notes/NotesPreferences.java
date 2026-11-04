package be.imgn.swing.examples.notes;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.PreferencesHandler;
import be.imgn.swing.ui.Ui;
import java.util.function.Consumer;

/** The preferences of the notes: the {@link PreferencesDialog}, opened by the view whose {@code preferences()} runs. */
@Singleton
public final class NotesPreferences implements PreferencesHandler {

    // Lets the tests see which Ui each call receives, which the dialog it opens doesn't show headless.
    private volatile Consumer<Ui> calls = ui -> {};

    @Inject
    NotesPreferences() {}

    @Override
    public void showPreferences(Ui ui) {
        calls.accept(ui);
        ui.showDialog(PreferencesDialog.class);
    }

    void onShowPreferences(Consumer<Ui> listener) {
        calls = listener;
    }
}
