package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.desktop.DocumentCommands;
import be.imgn.swing.desktop.DocumentFormat;
import be.imgn.swing.desktop.DocumentView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FileType;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.ToolBarEntry;
import be.imgn.swing.ui.Ui;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;

/**
 * A text editor whose document opens and saves files in tasks, enables its save command while the text is modified,
 * asks whether to save the modifications before exiting, and titles the window after the file.
 */
@Application
public final class DocumentExample implements SingleFrame, DocumentView<String> {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    @Exposed
    final JTextArea textArea = new JTextArea();

    private final Property<String> text = Property.of("");
    private final DocumentCommands commands;
    private final Ui ui;
    private final Form form;
    private final DocumentMessages messages;

    @Inject
    DocumentExample(DocumentCommands commands, Ui ui, Form form, DocumentMessages messages) {
        this.commands = commands;
        this.ui = ui;
        this.form = form;
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(DocumentExample.class, args);
    }

    @Override
    public JComponent content() {
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        form.bind(textArea, text);
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        return scroll;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(
                ui.menus()
                        .file()
                        .with(
                                commands.open(),
                                commands.save(),
                                commands.saveAs(),
                                MenuEntry.SEPARATOR,
                                commands.exit()),
                ui.menus().edit().with(commands.cut(), commands.copy(), commands.paste()),
                ui.menus().help().with(commands.about()));
    }

    @Override
    public JToolBar toolBar() {
        return ui.toolBar(
                commands.open(),
                commands.save(),
                ToolBarEntry.SEPARATOR,
                commands.cut(),
                commands.copy(),
                commands.paste());
    }

    @Override
    public DocumentFormat<String> format() {
        return DocumentFormat.text(new FileType(messages.txtExtDescription(), List.of("txt")));
    }

    @Override
    public String toSave() {
        return text.get();
    }

    @Override
    public void loaded(String contents) {
        text.set(contents);
    }
}
