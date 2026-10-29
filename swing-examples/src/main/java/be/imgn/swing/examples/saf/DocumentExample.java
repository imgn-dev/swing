package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.StandardMenus;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.ToolBarEntry;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.Window;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * A text editor that opens and saves files in tasks, whose save command is enabled only once the text is modified, and
 * whose exit veto asks before losing modifications. The window's title follows the file.
 */
@Application(name = "DocumentExample")
public final class DocumentExample implements SingleFrame, ExitVeto {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    @Exposed
    final Property<Boolean> modified = Property.of(false);

    private final JTextArea textArea = new JTextArea();
    private final Property<Path> file = Property.of(Path.of("untitled.txt"));
    private final DocumentExampleCommands commands;
    private final StandardMenus menus;
    private final Ui ui;
    private final Window window;
    private final Dialogs dialogs;
    private final DocumentMessages messages;
    private final ApplicationInfo application;

    @Inject
    @SuppressWarnings("checkstyle:ParameterNumber")
    DocumentExample(
            DocumentExampleCommands commands,
            StandardMenus menus,
            Ui ui,
            Window window,
            Dialogs dialogs,
            DocumentMessages messages,
            ApplicationInfo application) {
        this.commands = commands;
        this.menus = menus;
        this.ui = ui;
        this.window = window;
        this.dialogs = dialogs;
        this.messages = messages;
        this.application = application;
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
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                modified.set(true);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                modified.set(true);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                modified.set(true);
            }
        });
        ui.bind(path -> window.setTitle(messages.title(path.getFileName().toString(), application.name())), file);
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        return scroll;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(
                menus.file()
                        .with(
                                commands.open(),
                                commands.save(),
                                commands.saveAs(),
                                MenuEntry.SEPARATOR,
                                commands.exit()),
                menus.edit().with(commands.cut(), commands.copy(), commands.paste()),
                menus.help().with(commands.about()));
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
    public boolean canExit() {
        return !modified.get()
                || dialogs.confirm(messages.confirmExit(file.get().getFileName().toString()));
    }

    @Command(block = Block.WINDOW)
    Task<String> open() {
        Optional<Path> chosen = choose(true);
        return chosen.isPresent() ? load(chosen.get()) : Task.of(() -> textArea.getText());
    }

    @Command(enabled = "modified", block = Block.WINDOW)
    Task<Path> save() {
        return saveTo(file.get());
    }

    @Command(block = Block.WINDOW)
    Task<Path> saveAs() {
        return saveTo(choose(false).orElse(file.get()));
    }

    /**
     * Returns a task that loads a file into the editor.
     *
     * @param path the file
     * @return the task
     */
    Task<String> load(Path path) {
        return Task.of(() -> Files.readString(path, StandardCharsets.UTF_8))
                .onSuccess(text -> {
                    textArea.setText(text);
                    file.set(path);
                    modified.set(false);
                })
                .onFailure(failure -> dialogs.error(
                        messages.loadFailed(path.toString(), String.valueOf(failure.getMessage())), failure));
    }

    private Task<Path> saveTo(Path path) {
        String text = textArea.getText();
        return Task.of(() -> Files.writeString(path, text, StandardCharsets.UTF_8))
                .onSuccess(saved -> {
                    file.set(saved);
                    modified.set(false);
                })
                .onFailure(failure -> dialogs.error(
                        messages.saveFailed(path.toString(), String.valueOf(failure.getMessage())), failure));
    }

    private Optional<Path> choose(boolean open) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(messages.txtExtDescription(), "txt"));
        Component owner = SwingUtilities.getWindowAncestor(window.rootPane());
        int option = open ? chooser.showOpenDialog(owner) : chooser.showSaveDialog(owner);
        return option == JFileChooser.APPROVE_OPTION
                ? Optional.of(chooser.getSelectedFile().toPath())
                : Optional.empty();
    }

    /**
     * Sets the file the editor saves to, for tests, which choose no file.
     *
     * @param path the file
     */
    void file(Path path) {
        file.set(path);
    }
}
