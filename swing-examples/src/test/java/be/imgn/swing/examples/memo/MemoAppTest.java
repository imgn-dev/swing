package be.imgn.swing.examples.memo;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.desktop.Document;
import be.imgn.swing.desktop.DocumentCommands;
import be.imgn.swing.desktop.RecentFiles;
import be.imgn.swing.examples.counter.CounterApp;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.FileChoice;
import be.imgn.swing.testing.OpenFrame;
import be.imgn.swing.ui.AboutDialog;
import be.imgn.swing.ui.SaveChoice;
import be.imgn.swing.ui.StandardCommands;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

/** Memo's documents: new, open, save and save as, the save prompt, the title, recent files and undo. */
class MemoAppTest {

    private static Path write(FileSystem fileSystem, String path, String content) throws IOException {
        Path file = fileSystem.getPath(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static void type(AppTester<?> app, OpenFrame<MemoView> note, String text) {
        app.run(() -> note.component(JTextArea.class).setText(text));
    }

    @Test
    void startsWithOneUntitledNote() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            assertThat(app.frames()).hasSize(1);
            assertThat(app.frame(MemoView.class).title()).isEqualTo("Untitled – Memo");
            assertThat(app.frame(MemoView.class).component(JLabel.class).getText())
                    .isEqualTo("no words");
        }
    }

    @Test
    void startsWithTheFilesOfItsArguments() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            write(fileSystem, "/notes/shopping.txt", "Buy milk");
            write(fileSystem, "/notes/todo.txt", "Call Bob");
            try (AppTester<MemoApp> app = AppTester.builder(MemoApp.class)
                    .fileSystem(fileSystem)
                    .arguments("/notes/shopping.txt", "/notes/todo.txt")
                    .start()) {
                List<String> titles =
                        app.frames().stream().map(OpenFrame::title).toList();
                List<String> texts = app.frames().stream()
                        .map(frame -> frame.component(JTextArea.class).getText())
                        .toList();

                assertThat(titles).containsExactly("shopping.txt – Memo", "todo.txt – Memo");
                assertThat(texts).containsExactly("Buy milk", "Call Bob");
            }
        }
    }

    @Test
    void savesAnUntitledNoteUnderTheChosenFileWithTheExtensionAdded() throws IOException {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);
            Path data = app.info().directories().data();
            Files.createDirectories(data);
            type(app, note, "Buy milk");

            assertThat(note.title()).isEqualTo("*Untitled – Memo");

            app.dialogs().answerSaveFile(data.resolve("shopping"));
            note.commands(DocumentCommands.class).save().execute();

            assertThat(Files.readString(data.resolve("shopping.txt"))).isEqualTo("Buy milk");
            assertThat(note.title()).isEqualTo("shopping.txt – Memo");
            assertThat(note.component(JLabel.class).getText()).isEqualTo("Saved shopping.txt.");
            assertThat(app.dialogs().fileChoices()).singleElement().satisfies(choice -> {
                assertThat(choice.kind()).isEqualTo(FileChoice.Kind.SAVE);
                assertThat(choice.suggestedName()).isEqualTo("Untitled");
                assertThat(choice.types().getFirst().extensions()).containsExactly("txt");
            });

            type(app, note, "Buy milk and eggs");

            assertThat(note.component(JLabel.class).getText()).isEqualTo("4 words");

            note.commands(DocumentCommands.class).save().execute();

            assertThat(Files.readString(data.resolve("shopping.txt"))).isEqualTo("Buy milk and eggs");
            assertThat(app.dialogs().fileChoices()).hasSize(1);
        }
    }

    @Test
    void asksBeforeReplacingAnExistingFile() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path existing = write(fileSystem, "/notes/shopping.txt", "Old");
            try (AppTester<MemoApp> app =
                    AppTester.builder(MemoApp.class).fileSystem(fileSystem).start()) {
                OpenFrame<MemoView> note = app.frame(MemoView.class);
                type(app, note, "New");
                app.dialogs().answerConfirm(false);
                app.dialogs().answerSaveFile(existing);

                note.commands(DocumentCommands.class).save().execute();

                assertThat(app.dialogs().confirmed())
                        .containsExactly("shopping.txt already exists. Do you want to replace it?");
                assertThat(Files.readString(existing)).isEqualTo("Old");

                app.dialogs().answerConfirm(true);
                app.dialogs().answerSaveFile(existing);
                note.commands(DocumentCommands.class).saveAs().execute();

                assertThat(Files.readString(existing)).isEqualTo("New");
            }
        }
    }

    @Test
    void opensAFileInANewFrameAndBringsItToTheFrontWhenOpenAgain() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path file = write(fileSystem, "/notes/todo.txt", "Call Bob");
            try (AppTester<MemoApp> app =
                    AppTester.builder(MemoApp.class).fileSystem(fileSystem).start()) {
                DocumentCommands commands = app.frame(MemoView.class).commands(DocumentCommands.class);
                app.dialogs().answerOpenFile(file);

                commands.open().execute();

                assertThat(app.frames()).hasSize(2);
                OpenFrame<?> opened = app.frames().getLast();
                assertThat(opened.title()).isEqualTo("todo.txt – Memo");
                assertThat(app.dialogs().fileChoices().getFirst().kind()).isEqualTo(FileChoice.Kind.OPEN);

                app.dialogs().answerOpenFile(file);
                commands.open().execute();

                assertThat(app.frames()).hasSize(2);
                assertThat(opened.toFrontCount()).isEqualTo(1);
            }
        }
    }

    @Test
    void keepsTheRecentFilesAcrossARestartAndForgetsOneThatIsMissing() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path todo = write(fileSystem, "/notes/todo.txt", "Call Bob");
            Path shopping = write(fileSystem, "/notes/shopping.txt", "Buy milk");
            try (AppTester<MemoApp> app = AppTester.builder(MemoApp.class)
                    .fileSystem(fileSystem)
                    .arguments("/notes/todo.txt", "/notes/shopping.txt")
                    .start()) {
                assertThat(app.bean(RecentFiles.class).files().get()).containsExactly(shopping, todo);
            }
            Files.delete(todo);
            try (AppTester<MemoApp> app =
                    AppTester.builder(MemoApp.class).fileSystem(fileSystem).start()) {
                RecentFiles recent = app.bean(RecentFiles.class);
                assertThat(recent.files().get()).containsExactly(shopping, todo);

                JMenu file = app.frame(MemoView.class).component(JMenuBar.class).getMenu(0);
                app.run(() -> Arrays.stream(file.getMenuComponents())
                        .filter(JMenuItem.class::isInstance)
                        .map(JMenuItem.class::cast)
                        .filter(item -> item.getText().endsWith("todo.txt"))
                        .findFirst()
                        .orElseThrow()
                        .doClick());

                assertThat(app.dialogs().errors()).singleElement().asString().startsWith("Could not open todo.txt");
                assertThat(recent.files().get()).containsExactly(shopping);
                assertThat(app.frames()).hasSize(1);
            }
        }
    }

    @Test
    void asksTheSavePromptWhenAModifiedNoteCloses() throws IOException {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            DocumentCommands commands = app.frame(MemoView.class).commands(DocumentCommands.class);
            commands.newDocument().execute();
            OpenFrame<?> second = app.frames().getLast();
            assertThat(second.title()).isEqualTo("Untitled 2 – Memo");
            app.run(() -> second.component(JTextArea.class).setText("Draft"));

            app.dialogs().answerSave(SaveChoice.CANCEL);
            assertThat(second.close()).isFalse();

            Path data = app.info().directories().data();
            Files.createDirectories(data);
            app.dialogs().answerSave(SaveChoice.SAVE);
            app.dialogs().answerSaveFile(data.resolve("draft.txt"));
            second.close();

            assertThat(second.isOpen()).isFalse();
            assertThat(Files.readString(data.resolve("draft.txt"))).isEqualTo("Draft");
            assertThat(app.dialogs().savePrompts()).containsExactly("Untitled 2", "Untitled 2");
            assertThat(app.isRunning()).isTrue();
        }
    }

    @Test
    void discardsAModifiedNoteWhenTheUserSaysSo() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            app.frame(MemoView.class)
                    .commands(DocumentCommands.class)
                    .newDocument()
                    .execute();
            OpenFrame<?> second = app.frames().getLast();
            app.run(() -> second.component(JTextArea.class).setText("Draft"));
            app.dialogs().answerSave(SaveChoice.DISCARD);

            assertThat(second.close()).isTrue();
            assertThat(app.frames()).hasSize(1);
        }
    }

    @Test
    void asksForEachModifiedNoteOnExitAndExitsWhenTheLastNoteCloses() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            app.frame(MemoView.class)
                    .commands(DocumentCommands.class)
                    .newDocument()
                    .execute();
            for (OpenFrame<?> note : app.frames()) {
                app.run(() -> note.component(JTextArea.class).setText("Draft"));
            }
            app.dialogs().answerSave(SaveChoice.DISCARD);
            app.dialogs().answerSave(SaveChoice.DISCARD);

            app.frames().getFirst().commands(StandardCommands.class).exit().execute();

            assertThat(app.dialogs().savePrompts()).containsExactlyInAnyOrder("Untitled", "Untitled 2");
            assertThat(app.isRunning()).isFalse();
        }
    }

    @Test
    void closingTheLastNoteExits() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            app.frame(MemoView.class).close();

            assertThat(app.isRunning()).isFalse();
        }
    }

    @Test
    void undoesTypingBackToACleanNoteAndRedoesIt() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);
            Document document = note.bean(Document.class);
            StandardCommands commands = note.commands(StandardCommands.class);
            app.run(() -> note.component(JTextArea.class).append("Hello"));

            assertThat(app.call(() -> document.modified().get())).isTrue();

            commands.undo().execute();

            assertThat(note.component(JTextArea.class).getText()).isEmpty();
            assertThat(app.call(() -> document.modified().get())).isFalse();

            commands.redo().execute();

            assertThat(note.component(JTextArea.class).getText()).isEqualTo("Hello");
            assertThat(app.call(() -> document.modified().get())).isTrue();
        }
    }

    @Test
    void givesTheSaveCommandTheVisionsIconAndTooltipAndCountsWordsInFrench() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class, Locale.FRENCH)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);
            var save = note.commands(DocumentCommands.class).save();
            app.run(() -> note.component(JTextArea.class).setText("un deux"));

            assertThat(app.call(() -> save.asAction().getValue(Action.SHORT_DESCRIPTION)))
                    .isEqualTo("Enregistrer la note");
            assertThat(app.call(() -> save.asAction().getValue(Action.SMALL_ICON)))
                    .isNotNull();
            assertThat(note.component(JLabel.class).getText()).isEqualTo("2 mots");
            assertThat(note.title()).isEqualTo("*Sans titre – Memo");
            assertThat(note.iconImage()).isPresent();
        }
    }

    @Test
    void savesTheBytesAsUtf8() throws IOException {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            OpenFrame<MemoView> note = app.frame(MemoView.class);
            Path data = app.info().directories().data();
            Files.createDirectories(data);
            type(app, note, "Été");
            app.dialogs().answerSaveFile(data.resolve("summer.txt"));

            note.commands(DocumentCommands.class).save().execute();

            assertThat(Files.readAllBytes(data.resolve("summer.txt")))
                    .isEqualTo("Été".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void opensAnUntitledNoteOrTheFilesOfASecondLaunchAndBringsTheActiveFrameToTheFront() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            write(fileSystem, "/notes/todo.txt", "Call Bob");
            try (AppTester<MemoApp> app =
                    AppTester.builder(MemoApp.class).fileSystem(fileSystem).start()) {
                app.launchAgain();

                assertThat(app.frames()).hasSize(2);
                assertThat(app.frames().getLast().title()).isEqualTo("Untitled 2 – Memo");
                assertThat(app.frames().getLast().toFrontCount()).isEqualTo(1);

                app.launchAgain("/notes/todo.txt");

                assertThat(app.frames()).hasSize(3);
                assertThat(app.frames().getLast().title()).isEqualTo("todo.txt – Memo");
            }
        }
    }

    @Test
    void opensTheFilesTheSystemOpensWithIt() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path todo = write(fileSystem, "/notes/todo.txt", "Call Bob");
            try (AppTester<MemoApp> app =
                    AppTester.builder(MemoApp.class).fileSystem(fileSystem).start()) {
                app.openFiles(todo);

                assertThat(app.frames()).hasSize(2);
                assertThat(app.frames().getLast().component(JTextArea.class).getText())
                        .isEqualTo("Call Bob");
            }
        }
    }

    @Test
    void countsTheModifiedNotesOnTheTaskbarsBadge() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            app.frame(MemoView.class)
                    .commands(DocumentCommands.class)
                    .newDocument()
                    .execute();

            assertThat(app.taskbar().badge()).isEmpty();

            for (OpenFrame<?> note : app.frames()) {
                app.run(() -> note.component(JTextArea.class).setText("Draft"));
            }

            assertThat(app.taskbar().badge()).hasValue("2");

            app.dialogs().answerSave(SaveChoice.DISCARD);
            app.frames().getLast().close();

            assertThat(app.taskbar().badge()).hasValue("1");

            app.run(() -> app.frames().getFirst().component(JTextArea.class).setText(""));

            assertThat(app.taskbar().badge()).isEmpty();
        }
    }

    @Test
    void browsesTheWebsiteFromTheAboutDialog() {
        try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
            app.dialogs()
                    .answerDialog(
                            AboutDialog.class,
                            dialog -> dialog.components(JButton.class).stream()
                                    .filter(button -> button.getText().startsWith("https://"))
                                    .findFirst()
                                    .orElseThrow()
                                    .doClick());

            app.frame(MemoView.class).commands(StandardCommands.class).about().execute();

            assertThat(app.shell().browsed()).containsExactly(URI.create("https://github.com/imgn-dev/swing"));
        }
    }

    // The examples share one module, so Memo's handlers are every example's beans: they act in Memo only.
    @Test
    void ignoresTheSecondLaunchesAndOpenedFilesOfAnotherExample() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            app.launchAgain("notes.txt");
            app.openFiles();

            assertThat(app.frames()).hasSize(1);
            assertThat(app.notifications().notified()).isEmpty();
        }
    }
}
