package be.imgn.swing.examples.memo;

import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.desktop.Document;
import be.imgn.swing.desktop.DocumentCommands;
import be.imgn.swing.desktop.DocumentFormat;
import be.imgn.swing.desktop.DocumentView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FileType;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FrameView;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * A note, in a frame of its own: an editor bound with a form, whose document gives it new, open, save and save as, the
 * save prompt, a title that follows its file, and recent files; and a status that counts its words, or tells that it
 * was saved until the next change.
 */
public final class MemoView implements FrameView, DocumentView<String> {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    @Exposed
    final JTextArea editor = new JTextArea();

    private final JLabel status = new JLabel();
    private final Property<String> text = Property.of("");
    // The name of the file just saved, until the next change of the text; empty otherwise.
    private final Property<String> savedTo = Property.of("");
    private final Form form;
    private final MemoMessages messages;
    private final DocumentCommands commands;
    private final Ui ui;
    private final Document document;
    private final ModifiedNotes modified;
    private final Scope scope;

    @Inject
    MemoView(
            Form form,
            MemoMessages messages,
            DocumentCommands commands,
            Ui ui,
            Document document,
            ModifiedNotes modified,
            Scope scope) {
        this.form = form;
        this.messages = messages;
        this.commands = commands;
        this.ui = ui;
        this.document = document;
        this.modified = modified;
        this.scope = scope;
    }

    @Override
    public JComponent content() {
        form.bind(editor, text);
        ui.bind(isModified -> modified.changed(document, isModified), document.modified());
        scope.onClose(() -> modified.closed(document));
        ui.bind(ignored -> savedTo.set(""), text);
        ui.bind(
                status::setText,
                Observable.combine(
                        text,
                        savedTo,
                        (note, saved) -> saved.isEmpty() ? messages.words(words(note)) : messages.saved(saved)));
        JScrollPane scroll = new JScrollPane(editor);
        scroll.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(status, BorderLayout.PAGE_END);
        return panel;
    }

    private static int words(String note) {
        return note.isBlank() ? 0 : note.strip().split("\\s+").length;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(
                ui.menus()
                        .file()
                        .with(
                                commands.newDocument(),
                                commands.open(),
                                commands.recentFiles(),
                                MenuEntry.SEPARATOR,
                                commands.save(),
                                commands.saveAs(),
                                MenuEntry.SEPARATOR,
                                commands.closeWindow(),
                                commands.exit()),
                ui.menus()
                        .edit()
                        .with(
                                commands.undo(),
                                commands.redo(),
                                MenuEntry.SEPARATOR,
                                commands.cut(),
                                commands.copy(),
                                commands.paste(),
                                commands.selectAll()),
                ui.menus().help().with(commands.about()));
    }

    @Override
    public DocumentFormat<String> format() {
        return DocumentFormat.text(new FileType(messages.textFiles(), List.of("txt")));
    }

    @Override
    public String toSave() {
        return text.get();
    }

    @Override
    public void loaded(String contents) {
        text.set(contents);
    }

    @Override
    public void saved(Path file) {
        savedTo.set(Objects.toString(file.getFileName(), file.toString()));
    }
}
