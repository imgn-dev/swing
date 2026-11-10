package be.imgn.swing.examples.notes;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.Task;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Notes, a single-frame take on the vision's sample application: a text area that edits the notes of the
 * {@link NoteStore}, saved by a command enabled while the text differs from what was loaded or saved, and shown with
 * the font size and line wrap of the {@link EditorSettings}, which the {@link PreferencesDialog} changes.
 */
@Application(name = "Notes", vendor = "imgn", qualifier = "be")
public final class NotesApp implements SingleFrame {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    @Exposed
    final Observable<Boolean> canSave;

    @Exposed
    final JTextArea editor = new JTextArea();

    private final JLabel status = new JLabel();
    private final NoteStore store;
    private final Property<EditorSettings> settings;
    private final NotesAppCommands commands;
    private final Ui ui;
    private final Form form;
    private final NotesMessages messages;

    @Inject
    NotesApp(
            NoteStore store,
            SettingsStore settings,
            NotesAppCommands commands,
            Ui ui,
            Form form,
            NotesMessages messages) {
        this.store = store;
        this.settings = settings.get(EditorSettings.class);
        this.commands = commands;
        this.ui = ui;
        this.form = form;
        this.messages = messages;
        this.canSave = form.dirty();
    }

    /**
     * Launches the notes.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(NotesApp.class, args);
    }

    @Override
    public JComponent content() {
        editor.setWrapStyleWord(true);
        form.bind(editor, store.text());
        ui.bind(editor::setFont, settings.map(s -> new Font(Font.MONOSPACED, Font.PLAIN, s.fontSize())));
        ui.bind(editor::setLineWrap, settings.map(EditorSettings::wordWrap));
        JScrollPane scroll = new JScrollPane(editor);
        scroll.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(status, BorderLayout.PAGE_END);
        return panel;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(
                ui.menus()
                        .file()
                        .with(
                                commands.save(),
                                MenuEntry.SEPARATOR,
                                commands.preferences(),
                                MenuEntry.SEPARATOR,
                                commands.exit()),
                ui.menus().help().with(commands.about()));
    }

    @Command(enabled = "canSave", block = Block.WINDOW)
    Task<Path> save() {
        String text = store.text().get();
        return Task.of(() -> store.save(text)).onSuccess(file -> {
            form.markClean();
            status.setText(messages.saved(file));
        });
    }
}
