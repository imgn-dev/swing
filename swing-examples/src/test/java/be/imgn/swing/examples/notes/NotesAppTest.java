package be.imgn.swing.examples.notes;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.toml.Toml;
import java.awt.Component;
import java.awt.EventQueue;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The notes: their text, saved and loaded back, and the editor's settings, applied and saved. */
class NotesAppTest {

    private static Path notes(AppTester<NotesApp> app) {
        return app.bean(ApplicationDirectories.class).data().resolve("notes.txt");
    }

    private static Path editorToml(AppTester<NotesApp> app) {
        return app.bean(ApplicationDirectories.class).config().resolve("editor.toml");
    }

    private static EditorSettings settings(AppTester<NotesApp> app) {
        return app.bean(SettingsStore.class).get(EditorSettings.class).get();
    }

    private static String text(AppTester<NotesApp> app) {
        JTextArea editor = app.component(JTextArea.class);
        return app.call(editor::getText);
    }

    private static int fontSize(AppTester<NotesApp> app) {
        JTextArea editor = app.component(JTextArea.class);
        return app.call(() -> editor.getFont().getSize());
    }

    private static boolean lineWrap(AppTester<NotesApp> app) {
        JTextArea editor = app.component(JTextArea.class);
        return app.call(editor::getLineWrap);
    }

    private static boolean saveEnabled(AppTester<NotesApp> app) {
        return app.commands(NotesAppCommands.class).save().asAction().isEnabled();
    }

    @Test
    void savesTheTextAndLoadsItBack() throws Exception {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<NotesApp> app =
                    AppTester.builder(NotesApp.class).fileSystem(fileSystem).start()) {
                JTextArea editor = app.component(JTextArea.class);
                app.run(() -> editor.setText("Buy milk"));

                assertThat(app.commands(NotesAppCommands.class).save().execute())
                        .isTrue();

                Path file = notes(app);
                assertThat(Files.readString(file)).isEqualTo("Buy milk");
                JLabel status = app.component(JLabel.class);
                assertThat(app.call(status::getText)).isEqualTo("Saved to " + file);
            }

            try (AppTester<NotesApp> app =
                    AppTester.builder(NotesApp.class).fileSystem(fileSystem).start()) {
                assertThat(text(app)).isEqualTo("Buy milk");
                assertThat(app.bean(NoteStore.class).text().get()).isEqualTo("Buy milk");
                assertThat(saveEnabled(app)).isFalse();
            }
        }
    }

    @Test
    void saveIsDisabledUntilTheTextChangesAndAgainAfterSaving() throws Exception {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            NotesAppCommands commands = app.commands(NotesAppCommands.class);
            JTextArea editor = app.component(JTextArea.class);
            assertThat(saveEnabled(app)).isFalse();
            assertThat(commands.save().execute()).isFalse();

            app.run(() -> editor.setText("Call Ada"));
            assertThat(saveEnabled(app)).isTrue();

            assertThat(commands.save().execute()).isTrue();
            assertThat(saveEnabled(app)).isFalse();
            assertThat(Files.readString(notes(app))).isEqualTo("Call Ada");

            app.run(() -> editor.append(" and Lin"));
            assertThat(saveEnabled(app)).isTrue();

            // Edited back to the saved text, the notes have nothing to save.
            app.run(() -> editor.setText("Call Ada"));
            assertThat(saveEnabled(app)).isFalse();
        }
    }

    @Test
    void saveBlocksTheMainWindowWhileItRuns() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        List<Component> glassPanes = new ArrayList<>();
        List<Boolean> visible = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            NotesAppCommands commands = app.commands(NotesAppCommands.class);
            JTextArea editor = app.component(JTextArea.class);
            JRootPane rootPane = SwingUtilities.getRootPane(editor);
            Component unblocked = app.call(rootPane::getGlassPane);
            app.run(() -> editor.setText("Buy milk"));
            app.bean(NoteStore.class).hold(release);

            app.activate(commands.save());
            // The tester's calls wait for the held save, so the window is read on the EDT directly.
            EventQueue.invokeAndWait(() -> {
                glassPanes.add(rootPane.getGlassPane());
                visible.add(rootPane.getGlassPane().isVisible());
            });
            release.countDown();
            // A call runs its action before it settles, so this one waits for the released save to end first.
            app.run(() -> {});

            assertThat(glassPanes).singleElement().isNotSameAs(unblocked);
            assertThat(visible).containsExactly(true);
            assertThat(app.call(rootPane::getGlassPane)).isSameAs(unblocked);
            assertThat(Files.readString(notes(app))).isEqualTo("Buy milk");
        }
    }

    @Test
    void loadsTheNotesOffTheEdt() {
        try (AppTester<NotesApp> app = AppTester.builder(NotesApp.class)
                .file(directories -> directories.data().resolve("notes.txt"), "Buy milk")
                .start()) {
            Thread edt = app.call(Thread::currentThread);

            assertThat(app.bean(NoteStore.class).loader()).isNotNull().isNotSameAs(edt);
            assertThat(text(app)).isEqualTo("Buy milk");
        }
    }

    @Test
    void startsEmptyWithoutNotesTxt() {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            assertThat(text(app)).isEmpty();
            assertThat(notes(app)).doesNotExist();
            assertThat(saveEnabled(app)).isFalse();
        }
    }

    @Test
    void loadsTheTextFromNotesTxt() {
        try (AppTester<NotesApp> app = AppTester.builder(NotesApp.class)
                .file(directories -> directories.data().resolve("notes.txt"), "Water the plants\nFeed the cat")
                .start()) {
            assertThat(text(app)).isEqualTo("Water the plants\nFeed the cat");
            assertThat(saveEnabled(app)).isFalse();
        }
    }

    @Test
    void startsEmptyWhenNotesTxtCannotBeReadAndLeavesItAsItIs() {
        // A directory named notes.txt is a file that exists and can't be read as text.
        try (AppTester<NotesApp> app = AppTester.builder(NotesApp.class)
                .file(directories -> directories.data().resolve("notes.txt").resolve("kept"), "Kept")
                .start()) {
            assertThat(text(app)).isEmpty();
            assertThat(notes(app)).isDirectory();
            assertThat(notes(app).resolve("kept")).hasContent("Kept");
            assertThat(saveEnabled(app)).isFalse();
        }
    }

    @Test
    void appliesTheDefaultSettingsAtStartup() {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            assertThat(fontSize(app)).isEqualTo(14);
            assertThat(lineWrap(app)).isTrue();
        }
    }

    @Test
    void appliesTheSettingsGivenToTheBuilderAtStartup() {
        try (AppTester<NotesApp> app = AppTester.builder(NotesApp.class)
                .settings(new EditorSettings(12, false))
                .start()) {
            assertThat(fontSize(app)).isEqualTo(12);
            assertThat(lineWrap(app)).isFalse();
        }
    }

    @Test
    void applyChangesTheFontWhileTheDialogStaysOpen() {
        try (AppTester<NotesApp> app = AppTester.builder(NotesApp.class)
                .settings(new EditorSettings(12, true))
                .start()) {
            List<Object> whileOpen = new ArrayList<>();
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                dialog.component(JTextField.class).setText("18");
                dialog.commands(StandardCommands.class).apply().execute();
                // The editor follows the settings through Ui.bind, whose updates are posted.
                dialog.settle();
                whileOpen.add(dialog.isOpen());
                whileOpen.add(app.component(JTextArea.class).getFont().getSize());
                dialog.commands(StandardCommands.class).cancel().execute();
            });

            app.commands(NotesAppCommands.class).preferences().execute();

            assertThat(whileOpen).containsExactly(true, 18);

            assertThat(app.bean(SettingsStore.class)
                            .get(EditorSettings.class)
                            .get()
                            .fontSize())
                    .isEqualTo(18);
            assertThat(app.component(JTextArea.class).getFont().getSize()).isEqualTo(18);
        }
    }

    @Test
    void savesBothValuesInEditorTomlAfterAChange() throws Exception {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            assertThat(editorToml(app)).doesNotExist();
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                dialog.component(JTextField.class).setText("20");
                dialog.component(JCheckBox.class).setSelected(false);
                dialog.commands(StandardCommands.class).ok().execute();
            });

            app.commands(NotesAppCommands.class).preferences().execute();

            assertThat(settings(app)).isEqualTo(new EditorSettings(20, false));
            assertThat(Files.readString(editorToml(app))).contains("font-size = 20", "word-wrap = false");
            assertThat(Toml.parse(editorToml(app), EditorSettings.class)).isEqualTo(new EditorSettings(20, false));
            assertThat(fontSize(app)).isEqualTo(20);
            assertThat(lineWrap(app)).isFalse();
        }
    }

    @Test
    void loadsAnEditorTomlWithAnUnknownKey() {
        try (AppTester<NotesApp> app = AppTester.builder(NotesApp.class)
                .file(
                        directories -> directories.config().resolve("editor.toml"),
                        "font-size = 16\nword-wrap = false\ntheme = \"dark\"\n")
                .start()) {
            assertThat(settings(app)).isEqualTo(new EditorSettings(16, false));
            assertThat(fontSize(app)).isEqualTo(16);
            assertThat(lineWrap(app)).isFalse();
        }
    }

    @Test
    void movesAnEditorTomlThatDoesNotParseToEditorTomlInvalid() throws Exception {
        String content = "font-size = sixteen\n";
        try (AppTester<NotesApp> app = AppTester.builder(NotesApp.class)
                .file(directories -> directories.config().resolve("editor.toml"), content)
                .start()) {
            Path invalid = app.bean(ApplicationDirectories.class).config().resolve("editor.toml.invalid");

            assertThat(settings(app)).isEqualTo(new EditorSettings());
            assertThat(fontSize(app)).isEqualTo(14);
            assertThat(editorToml(app)).doesNotExist();
            assertThat(Files.readString(invalid)).isEqualTo(content);
        }
    }
}
