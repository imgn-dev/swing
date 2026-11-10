package be.imgn.swing.examples.memo;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.Appearance;
import be.imgn.swing.desktop.DocumentCommands;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.Inspection;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.testing.OpenFrame;
import be.imgn.swing.ui.CommandPalette;
import be.imgn.swing.ui.KeymapDialog;
import be.imgn.swing.ui.PaletteEntry;
import be.imgn.swing.ui.StandardCommands;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

/** Memo's polish: the command palette, the keymap, the Window menu, the appearance, the locale and the inspector. */
class MemoPolishTest {

    private static final KeyStroke CTRL_S = KeyStroke.getKeyStroke("ctrl S");
    private static final KeyStroke CTRL_SHIFT_S = KeyStroke.getKeyStroke("ctrl shift S");

    private static List<String> search(AppTester<MemoApp> app, OpenFrame<MemoView> note, String query) {
        return app.call(() -> note.bean(CommandPalette.class).search(query).stream()
                .map(PaletteEntry::text)
                .toList());
    }

    private static Optional<JMenuItem> item(AppTester<MemoApp> app, OpenFrame<?> note, String text) {
        return app.call(() -> {
            JMenuBar bar = note.component(JMenuBar.class);
            for (int menu = 0; menu < bar.getMenuCount(); menu++) {
                for (var component : bar.getMenu(menu).getMenuComponents()) {
                    if (component instanceof JMenuItem item && text.equals(item.getText())) {
                        return Optional.of(item);
                    }
                }
            }
            return Optional.empty();
        });
    }

    private static Object accelerator(AppTester<MemoApp> app, OpenFrame<?> note, String text) {
        return app.call(() -> {
            KeyStroke accelerator = item(app, note, text).orElseThrow().getAccelerator();
            return accelerator == null ? "none" : accelerator;
        });
    }

    // Selects a command's row in the keymap dialog.
    private static void select(OpenDialog<?> dialog, String command) {
        JTable table = dialog.component(JTable.class);
        int row = 0;
        while (!table.getValueAt(row, 0).equals(command)) {
            row++;
        }
        table.setRowSelectionInterval(row, row);
    }

    // To the field's listeners, since the focus manager drops a key event for a component that has no focus.
    private static void press(OpenDialog<?> dialog, int modifiers, int key) {
        JTextField field = dialog.component(JTextField.class, "Shortcut:");
        KeyEvent press = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : field.getKeyListeners()) {
            listener.keyPressed(press);
        }
    }

    private static void ok(OpenDialog<?> dialog) {
        dialog.commands(StandardCommands.class).ok().execute();
    }

    @Test
    void findsSaveInThePaletteAndRunsIt() throws IOException {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);
            Path data = app.info().directories().data();
            Files.createDirectories(data);
            app.run(() -> note.component(JTextArea.class).setText("Buy milk"));
            app.dialogs().answerCommand("sav");
            app.dialogs().answerSaveFile(data.resolve("shopping"));

            note.commands(DocumentCommands.class).commandPalette().execute();

            assertThat(Files.readString(data.resolve("shopping.txt"))).isEqualTo("Buy milk");
            assertThat(note.title()).isEqualTo("shopping.txt – Memo");
            assertThat(app.dialogs().commandQueries()).containsExactly("sav");
        }
    }

    @Test
    void ranksAPrefixBeforeAWordBeforeASubstringBeforeASubsequence() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);

            // The reverse of the texts' order: prefixes, a word, then substrings.
            assertThat(search(app, note, "s"))
                    .containsExactly("Save", "Save As…", "Keyboard Shortcuts…", "Close", "Preferences...");
            app.dialogs().answerCommand("keyboard");

            note.commands(DocumentCommands.class).commandPalette().execute();

            // A substring before a subsequence, though the subsequence was run last.
            assertThat(search(app, note, "ou")).containsExactly("About", "Keyboard Shortcuts…");
        }
    }

    @Test
    void listsTheLastCommandRunFirstForAnEmptyQuery() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);
            assertThat(search(app, note, "")).startsWith("About");
            app.dialogs().answerCommand("keyboard");

            note.commands(DocumentCommands.class).commandPalette().execute();

            assertThat(search(app, note, "")).startsWith("Keyboard Shortcuts…", "About");
        }
    }

    @Test
    void listsNoDisabledCommand() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);

            assertThat(search(app, note, ""))
                    .contains("Save", "New", "Keyboard Shortcuts…")
                    .doesNotContain("Undo", "Redo", "Cut", "Copy", "Paste");
        }
    }

    @Test
    void hasThePalettesShortcutInEachFrame() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            app.frame(MemoView.class)
                    .commands(DocumentCommands.class)
                    .newDocument()
                    .execute();

            assertThat(app.frames()).hasSize(2);
            for (OpenFrame<?> note : app.frames()) {
                assertThat(accelerator(app, note, "Command Palette…"))
                        .isEqualTo(KeyStroke.getKeyStroke("ctrl shift P"));
                assertThat(app.call(() -> note.component(JRootPane.class)
                                .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                                .get(KeyStroke.getKeyStroke("ctrl shift P"))))
                        .isEqualTo("be.imgn.swing.command:be.imgn.swing.ui.StandardCommands#commandPalette");
            }
        }
    }

    @Test
    void assignsAShortcutInTheKeymapDialogToEveryFrameTakesItFromSaveAsAndKeepsIt() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<MemoApp> app =
                    AppTester.builder(MemoApp.class).fileSystem(fileSystem).start()) {
                OpenFrame<MemoView> first = app.frame(MemoView.class);
                first.commands(DocumentCommands.class).newDocument().execute();
                assertThat(accelerator(app, first, "Save As…")).isEqualTo(CTRL_SHIFT_S);
                app.dialogs().answerDialog(KeymapDialog.class, dialog -> {
                    select(dialog, "Save");
                    press(dialog, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_S);
                    ok(dialog);
                });

                first.commands(DocumentCommands.class).keymap().execute();

                assertThat(app.frames()).hasSize(2);
                for (OpenFrame<?> other : app.frames()) {
                    assertThat(accelerator(app, other, "Save")).isEqualTo(CTRL_SHIFT_S);
                    assertThat(accelerator(app, other, "Save As…")).isEqualTo("none");
                }
            }
            try (AppTester<MemoApp> app =
                    AppTester.builder(MemoApp.class).fileSystem(fileSystem).start()) {
                assertThat(accelerator(app, app.frame(MemoView.class), "Save")).isEqualTo(CTRL_SHIFT_S);
            }
        }
    }

    @Test
    void removesAndResetsAShortcutInTheKeymapDialog() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);
            app.dialogs().answerDialog(KeymapDialog.class, dialog -> {
                select(dialog, "Save");
                dialog.component(JButton.class, "Remove").doClick();
                ok(dialog);
            });

            note.commands(DocumentCommands.class).keymap().execute();

            assertThat(accelerator(app, note, "Save")).isEqualTo("none");

            app.dialogs().answerDialog(KeymapDialog.class, dialog -> {
                select(dialog, "Save");
                dialog.component(JButton.class, "Reset").doClick();
                ok(dialog);
            });

            note.commands(DocumentCommands.class).keymap().execute();

            assertThat(accelerator(app, note, "Save")).isEqualTo(CTRL_S);
        }
    }

    private static JMenu windowMenu(AppTester<MemoApp> app, OpenFrame<MemoView> note) {
        return app.call(() -> {
            JMenuBar bar = note.component(JMenuBar.class);
            for (int menu = 0; menu < bar.getMenuCount(); menu++) {
                if (bar.getMenu(menu).getText().equals("Window")) {
                    return bar.getMenu(menu);
                }
            }
            throw new AssertionError("No Window menu");
        });
    }

    private static List<String> items(AppTester<MemoApp> app, JMenu menu) {
        return app.call(() -> Arrays.stream(menu.getMenuComponents())
                .filter(JMenuItem.class::isInstance)
                .map(item -> ((JMenuItem) item).getText())
                .toList());
    }

    @Test
    void listsTheOpenNotesInTheWindowMenuFollowsASaveAndBringsTheChosenOneToTheFront() throws IOException {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> first = app.frame(MemoView.class);
            first.commands(DocumentCommands.class).newDocument().execute();
            JMenu menu = windowMenu(app, first);

            assertThat(items(app, menu)).containsExactly("1 Untitled – Memo", "2 Untitled 2 – Memo");

            Path data = app.info().directories().data();
            Files.createDirectories(data);
            app.dialogs().answerSaveFile(data.resolve("shopping"));
            first.commands(DocumentCommands.class).save().execute();

            assertThat(items(app, menu)).containsExactly("1 shopping.txt – Memo", "2 Untitled 2 – Memo");

            int before = first.toFrontCount();
            app.run(() -> ((JMenuItem) menu.getMenuComponent(0)).doClick());

            assertThat(first.toFrontCount()).isEqualTo(before + 1);
        }
    }

    @Test
    void colorsTheStatusForTheAppearance() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            JLabel status = app.frame(MemoView.class).view().status;
            assertThat(app.call(status::getForeground)).isEqualTo(new Color(0x5f6368));

            app.appearance(Appearance.DARK);

            assertThat(app.call(status::getForeground)).isEqualTo(new Color(0x9aa0a6));

            app.appearance(Appearance.LIGHT);

            assertThat(app.call(status::getForeground)).isEqualTo(new Color(0x5f6368));
        }
    }

    @Test
    void countsTheWordsInTheNewLocale() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);
            app.run(() -> note.component(JTextArea.class).setText("Buy milk"));

            app.locale(Locale.FRENCH);

            assertThat(app.call(() -> note.view().status.getText())).isEqualTo("2 mots");
        }
    }

    @Test
    void inspectsTheEditorsFieldAndTheSaveItemsCommand() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);

            assertThat(app.inspect(note.view().editor))
                    .isEqualTo(new Inspection(JTextArea.class, MemoView.class, "editor", null));
            assertThat(app.inspect(item(app, note, "Save").orElseThrow()).command())
                    .isEqualTo("be.imgn.swing.desktop.DocumentCommands#save");
        }
    }
}
