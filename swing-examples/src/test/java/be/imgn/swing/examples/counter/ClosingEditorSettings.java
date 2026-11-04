package be.imgn.swing.examples.counter;

import be.imgn.inject.Inject;
import be.imgn.inject.PreDestroy;
import be.imgn.swing.Property;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.examples.notes.EditorSettings;
import be.imgn.swing.ui.ViewScoped;

/** Sets the editor settings when its view closes, as a view that keeps what the user chose until then does. */
@ViewScoped
final class ClosingEditorSettings {

    /** The settings set when the view closes. */
    static final EditorSettings CLOSED = new EditorSettings(20, false);

    private final Property<EditorSettings> editor;

    @Inject
    ClosingEditorSettings(SettingsStore settings) {
        this.editor = settings.get(EditorSettings.class);
    }

    @PreDestroy
    void closed() {
        editor.set(CLOSED);
    }
}
