package be.imgn.swing.ui.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Inject;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.FileType;
import be.imgn.swing.ui.SaveChoice;
import be.imgn.swing.ui.Window;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jspecify.annotations.Nullable;

/** The real {@link Dialogs}: option panes, file choosers and the error dialog, owned by its window. */
public final class SwingDialogs implements Dialogs {

    private final Window window;
    private final ApplicationInfo application;
    private final UiTexts texts;
    private final WindowStateSupport state;
    private final boolean macOS;

    @Inject
    SwingDialogs(
            Window window, ApplicationInfo application, UiTexts texts, WindowStateSupport state, Platform platform) {
        this.window = window;
        this.application = application;
        this.texts = texts;
        this.state = state;
        this.macOS = platform.isMacOS();
    }

    private Component owner() {
        return window instanceof AwtWindow real ? real.awt() : window.rootPane();
    }

    @Override
    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(owner(), message, application.displayedName(), JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    @Override
    public void inform(String message) {
        JOptionPane.showMessageDialog(owner(), message, application.displayedName(), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void error(String message, Throwable cause) {
        ErrorDialog.show(owner(), texts, texts.error(), message, cause);
    }

    @Override
    public Optional<Path> openFile(List<FileType> types) {
        JFileChooser chooser = filters(new JFileChooser(start()), types);
        if (chooser.showOpenDialog(owner()) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        return chosen(chooser.getSelectedFile());
    }

    @Override
    public Optional<Path> saveFile(List<FileType> types, String suggestedName) {
        requireNonNull(suggestedName, "suggestedName");
        JFileChooser chooser = filters(new ReplacingChooser(start()), types);
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        if (chooser.showSaveDialog(owner()) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        return chosen(chooser.getSelectedFile());
    }

    private Optional<Path> chosen(File file) {
        Path path = file.toPath().toAbsolutePath();
        Path parent = path.getParent();
        if (parent != null) {
            state.directory(parent);
        }
        return Optional.of(path);
    }

    // The directory last chosen in a chooser of this window, else the user's home directory, the chooser's default.
    private @Nullable File start() {
        return state.directory().map(Path::toFile).orElse(null);
    }

    private static JFileChooser filters(JFileChooser chooser, List<FileType> types) {
        requireNonNull(types, "types");
        @Nullable FileFilter first = null;
        for (FileType type : types) {
            FileFilter filter = new FileNameExtensionFilter(
                    requireNonNull(type, "type").description(),
                    type.extensions().toArray(String[]::new));
            chooser.addChoosableFileFilter(filter);
            if (first == null) {
                first = filter;
            }
        }
        if (first != null) {
            chooser.setFileFilter(first);
        }
        return chooser;
    }

    /**
     * Returns a file with the first extension of the selected type unless it has one of its extensions; with the "All
     * files" filter, the file as typed.
     *
     * @param file the file
     * @param filter the selected filter
     * @return the file to save to
     */
    static Path withExtension(Path file, FileFilter filter) {
        if (!(filter instanceof FileNameExtensionFilter typed)) {
            return file;
        }
        String name = Objects.toString(file.getFileName(), "").toLowerCase(Locale.ROOT);
        for (String extension : typed.getExtensions()) {
            if (name.endsWith("." + extension.toLowerCase(Locale.ROOT))) {
                return file;
            }
        }
        return file.resolveSibling(file.getFileName() + "." + typed.getExtensions()[0]);
    }

    @Override
    public SaveChoice askSave(String documentName) {
        requireNonNull(documentName, "documentName");
        JButton[] buttons = saveButtons(List.of(texts.save(), texts.discard(), texts.cancel()), macOS);
        JOptionPane pane = new JOptionPane(
                texts.saveChanges(documentName),
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                buttons,
                buttons[0]);
        for (JButton button : buttons) {
            button.addActionListener(event -> pane.setValue(button));
        }
        JDialog dialog = pane.createDialog(owner(), application.displayedName());
        // Enter is Save; Escape and closing the dialog leave no value, which is Cancel.
        dialog.getRootPane().setDefaultButton(buttons[0]);
        dialog.setVisible(true);
        dialog.dispose();
        Object value = pane.getValue();
        if (buttons[0].equals(value)) {
            return SaveChoice.SAVE;
        }
        return buttons[1].equals(value) ? SaveChoice.DISCARD : SaveChoice.CANCEL;
    }

    /**
     * Returns the buttons of the save prompt: Save, Don't Save and Cancel, with their mnemonics but on macOS.
     *
     * @param texts the texts of the buttons, with their {@code &} mnemonics, in that order
     * @param macOS whether the application runs on macOS, which sets no mnemonics
     * @return the buttons, in that order
     */
    static JButton[] saveButtons(List<String> texts, boolean macOS) {
        return texts.stream().map(text -> button(text, macOS)).toArray(JButton[]::new);
    }

    private static JButton button(String marked, boolean macOS) {
        Mnemonics text = Mnemonics.parse(marked);
        JButton button = new JButton(text.text());
        if (!macOS && text.hasMnemonic()) {
            button.setMnemonic(text.keyCode());
            button.setDisplayedMnemonicIndex(text.index());
        }
        return button;
    }

    // A file that exists asks whether to replace it before the chooser closes; answering no leaves it open.
    private final class ReplacingChooser extends JFileChooser {

        private static final long serialVersionUID = 1L;

        ReplacingChooser(@Nullable File directory) {
            super(directory);
        }

        @Override
        public void approveSelection() {
            // JFileChooser speaks java.io.File: the conversion stays here.
            Path file = withExtension(getSelectedFile().toPath(), getFileFilter());
            setSelectedFile(file.toFile());
            if (Files.exists(file) && !confirm(texts.replace(Objects.toString(file.getFileName(), "")))) {
                return;
            }
            super.approveSelection();
        }
    }
}
