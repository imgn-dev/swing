package be.imgn.swing.examples.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.Keymap;
import be.imgn.swing.ui.KeymapDialog;
import be.imgn.swing.ui.StandardCommands;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class KeymapTest {

    private static final KeyStroke CTRL_G = KeyStroke.getKeyStroke("ctrl G");
    private static final KeyStroke CTRL_SHIFT_G = KeyStroke.getKeyStroke("ctrl shift G");
    private static final String GREET = "be.imgn.swing.examples.refresh.RefreshApp#greet";
    private static final String CLEAR = "be.imgn.swing.examples.refresh.RefreshApp#clear";

    private static Object accelerator(AppTester<RefreshApp> app, String command) {
        return app.call(() -> {
            RefreshAppCommands commands = app.commands(RefreshAppCommands.class);
            Action action = switch (command) {
                case "greet" -> commands.greet().asAction();
                case "clear" -> commands.clear().asAction();
                default -> commands.copy().asAction();
            };
            Object value = action.getValue(Action.ACCELERATOR_KEY);
            return value == null ? "none" : value;
        });
    }

    private static Object bound(AppTester<RefreshApp> app, KeyStroke keyStroke) {
        return app.call(() -> {
            Object key = app.component(JRootPane.class)
                    .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(keyStroke);
            return key == null ? "none" : key;
        });
    }

    @Test
    void givesAStandardCommandsShortcutToTheApplicationsCommand() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            assertThat(accelerator(app, "copy")).isEqualTo("none");
            assertThat(accelerator(app, "clear")).isEqualTo(KeyStroke.getKeyStroke("ctrl C"));
            assertThat(bound(app, KeyStroke.getKeyStroke("ctrl C")))
                    .isEqualTo("be.imgn.swing.command:be.imgn.swing.examples.refresh.RefreshApp#clear");
        }
    }

    @Test
    void assignsAShortcutToTheOpenWindowsAndSavesIt() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<RefreshApp> app =
                    AppTester.builder(RefreshApp.class).fileSystem(fileSystem).start()) {
                app.run(() -> app.bean(Keymap.class)
                        .assign(app.commands(RefreshAppCommands.class).greet(), CTRL_SHIFT_G));

                assertThat(accelerator(app, "greet")).isEqualTo(CTRL_SHIFT_G);
                assertThat(bound(app, CTRL_G)).isEqualTo("none");
                assertThat(bound(app, CTRL_SHIFT_G)).isNotEqualTo("none");
                assertThat(Files.readString(
                                app.bean(ApplicationDirectories.class).config().resolve("keymap.toml")))
                        .contains("\"be.imgn.swing.examples.refresh.RefreshApp#greet\" = \"shortcut shift G\"");
            }
            try (AppTester<RefreshApp> app =
                    AppTester.builder(RefreshApp.class).fileSystem(fileSystem).start()) {
                assertThat(accelerator(app, "greet")).isEqualTo(CTRL_SHIFT_G);
            }
        }
    }

    @Test
    void takesAShortcutFromTheCommandMappedToItAndRemovesAndResets() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.run(() -> {
                Keymap keymap = app.bean(Keymap.class);
                RefreshAppCommands commands = app.commands(RefreshAppCommands.class);
                keymap.assign(commands.greet(), CTRL_SHIFT_G);
                keymap.assign(commands.clear(), CTRL_SHIFT_G);
            });

            assertThat(accelerator(app, "clear")).isEqualTo(CTRL_SHIFT_G);
            assertThat(accelerator(app, "greet")).isEqualTo(CTRL_G);

            app.run(() -> app.bean(Keymap.class)
                    .remove(app.commands(RefreshAppCommands.class).greet()));
            assertThat(accelerator(app, "greet")).isEqualTo("none");

            app.run(() -> app.bean(Keymap.class)
                    .reset(app.commands(RefreshAppCommands.class).greet()));
            assertThat(accelerator(app, "greet")).isEqualTo(CTRL_G);
        }
    }

    @Test
    void recordsAShortcutInTheKeymapDialog() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.dialogs().answerDialog(KeymapDialog.class, dialog -> {
                JTable table = dialog.component(JTable.class);
                int row = 0;
                while (!table.getValueAt(row, 0).equals("Greet")) {
                    row++;
                }
                table.setRowSelectionInterval(row, row);
                JTextField field = dialog.component(JTextField.class, "Shortcut:");
                // To its listeners, since the focus manager drops a key event for a component that has no focus.
                KeyEvent press = new KeyEvent(
                        field,
                        KeyEvent.KEY_PRESSED,
                        0,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                        KeyEvent.VK_G,
                        'G');
                for (KeyListener listener : field.getKeyListeners()) {
                    listener.keyPressed(press);
                }
                assertThat(table.getValueAt(row, 1)).isEqualTo("Ctrl+Shift+G");
                dialog.commands(StandardCommands.class).ok().execute();
            });

            app.commands(StandardCommands.class).keymap().execute();

            assertThat(accelerator(app, "greet")).isEqualTo(CTRL_SHIFT_G);
        }
    }

    private static AppTester<RefreshApp> startWithKeymap(String keymap) {
        return AppTester.builder(RefreshApp.class)
                .file(directories -> directories.config().resolve("keymap.toml"), keymap)
                .start();
    }

    @Test
    void readsTheKeymapFileTheFirstOfTwoMappingsWinningAndKeepsUnknownCommands() throws IOException {
        try (AppTester<RefreshApp> app = startWithKeymap("""
                [shortcuts]
                "%s" = "shortcut shift G"
                "%s" = "shortcut shift G"
                "p.Gone#vanished" = "shortcut K"
                """.formatted(GREET, CLEAR))) {
            assertThat(accelerator(app, "greet")).isEqualTo(CTRL_SHIFT_G);
            assertThat(accelerator(app, "clear")).isEqualTo(KeyStroke.getKeyStroke("ctrl C"));

            app.run(() -> app.bean(Keymap.class)
                    .remove(app.commands(RefreshAppCommands.class).clear()));

            assertThat(Files.readString(
                            app.bean(ApplicationDirectories.class).config().resolve("keymap.toml")))
                    .contains("\"p.Gone#vanished\" = \"shortcut K\"")
                    .contains("\"" + CLEAR + "\" = \"\"");
        }
    }

    @Test
    void dropsAValueThatDoesNotParseAndReadsARemovedShortcut() {
        try (AppTester<RefreshApp> app = startWithKeymap("""
                [shortcuts]
                "%s" = "nope"
                "%s" = ""
                """.formatted(GREET, CLEAR))) {
            assertThat(accelerator(app, "greet")).isEqualTo(CTRL_G);
            assertThat(accelerator(app, "clear")).isEqualTo("none");
        }
    }

    @Test
    void discardsAFileThatDoesNotBind() {
        try (AppTester<RefreshApp> app = startWithKeymap("shortcuts = 3\n")) {
            assertThat(accelerator(app, "greet")).isEqualTo(CTRL_G);
        }
    }

    @Test
    void givesAMappedShortcutTheWinOverAnotherCommandsDefault() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.run(() -> app.bean(Keymap.class)
                    .assign(app.commands(RefreshAppCommands.class).greet(), KeyStroke.getKeyStroke("ctrl C")));

            assertThat(accelerator(app, "greet")).isEqualTo(KeyStroke.getKeyStroke("ctrl C"));
            assertThat(accelerator(app, "clear")).isEqualTo("none");
            assertThat(bound(app, KeyStroke.getKeyStroke("ctrl C"))).isEqualTo("be.imgn.swing.command:" + GREET);
        }
    }

    @Test
    void resetsEveryRowAndDropsTheEditsOnCancel() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.run(() -> app.bean(Keymap.class)
                    .assign(app.commands(RefreshAppCommands.class).greet(), CTRL_SHIFT_G));
            app.dialogs().answerDialog(KeymapDialog.class, dialog -> {
                dialog.component(JButton.class, "Reset All").doClick();
                dialog.commands(StandardCommands.class).cancel().execute();
            });
            app.commands(StandardCommands.class).keymap().execute();
            assertThat(accelerator(app, "greet")).isEqualTo(CTRL_SHIFT_G);

            app.dialogs().answerDialog(KeymapDialog.class, dialog -> {
                dialog.component(JButton.class, "Reset All").doClick();
                dialog.commands(StandardCommands.class).ok().execute();
            });
            app.commands(StandardCommands.class).keymap().execute();

            assertThat(accelerator(app, "greet")).isEqualTo(CTRL_G);
        }
    }

    @Test
    void filtersTheCommandsAsThePaletteSearches() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.dialogs().answerDialog(KeymapDialog.class, dialog -> {
                JTable table = dialog.component(JTable.class);
                assertThat(table.getRowCount()).isGreaterThan(2);

                dialog.component(JTextField.class, "Filter:").setText("gre");

                assertThat(table.getRowCount()).isEqualTo(1);
                assertThat(table.getValueAt(0, 0)).isEqualTo("Greet");
                dialog.commands(StandardCommands.class).cancel().execute();
            });
            app.commands(StandardCommands.class).keymap().execute();
        }
    }
}
