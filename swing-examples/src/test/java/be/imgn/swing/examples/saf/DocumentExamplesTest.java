package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.desktop.DocumentCommands;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.SaveChoice;
import be.imgn.swing.ui.StandardCommands;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentExamplesTest {

    @TempDir
    Path directory;

    @Test
    void documentExampleOpensTheChosenFileAndSavesToIt() throws Exception {
        Path file = directory.resolve("letter.txt");
        Files.writeString(file, "Dear reader");
        try (AppTester<DocumentExample> app = AppTester.start(DocumentExample.class)) {
            DocumentCommands commands = app.commands(DocumentCommands.class);
            assertThat(app.title()).isEqualTo("Untitled – DocumentExample");
            app.dialogs().answerOpenFile(file);

            commands.open().execute();

            assertThat(app.component(JTextArea.class).getText()).isEqualTo("Dear reader");
            assertThat(app.title()).isEqualTo("letter.txt – DocumentExample");
            assertThat(commands.save().asAction().isEnabled()).isFalse();

            app.run(() -> app.component(JTextArea.class).append("!"));

            assertThat(app.title()).isEqualTo("*letter.txt – DocumentExample");
            assertThat(commands.save().asAction().isEnabled()).isTrue();

            commands.save().execute();

            assertThat(Files.readString(file)).isEqualTo("Dear reader!");
            assertThat(app.dialogs().fileChoices()).hasSize(1);
            assertThat(commands.save().asAction().isEnabled()).isFalse();
        }
    }

    @Test
    void documentExampleSavesAnUntitledTextUnderTheChosenFile() throws Exception {
        Path file = directory.resolve("notes.txt");
        try (AppTester<DocumentExample> app = AppTester.start(DocumentExample.class)) {
            DocumentCommands commands = app.commands(DocumentCommands.class);
            assertThat(commands.save().asAction().isEnabled()).isTrue();
            app.run(() -> app.component(JTextArea.class).setText("Hello"));
            app.dialogs().answerSaveFile(directory.resolve("notes"));

            commands.save().execute();

            assertThat(Files.readString(file)).isEqualTo("Hello");
            assertThat(app.title()).isEqualTo("notes.txt – DocumentExample");
        }
    }

    @Test
    void documentExampleAsksTheSavePromptOnExitOnlyWhileModified() {
        try (AppTester<DocumentExample> app = AppTester.start(DocumentExample.class)) {
            app.run(() -> app.component(JTextArea.class).setText("Hello"));
            app.dialogs().answerSave(SaveChoice.CANCEL);

            app.commands(StandardCommands.class).exit().execute();

            assertThat(app.isRunning()).isTrue();
            assertThat(app.dialogs().savePrompts()).containsExactly("Untitled");

            app.run(() -> app.component(JTextArea.class).setText(""));
            app.commands(StandardCommands.class).exit().execute();

            assertThat(app.isRunning()).isFalse();
            assertThat(app.dialogs().savePrompts()).hasSize(1);
        }
    }

    @Test
    void documentExampleBringsItsFrameToTheFrontWhenItsFileIsOpenedAgain() throws Exception {
        Path file = directory.resolve("letter.txt");
        Files.writeString(file, "Dear reader");
        try (AppTester<DocumentExample> app = AppTester.start(DocumentExample.class)) {
            DocumentCommands commands = app.commands(DocumentCommands.class);
            app.dialogs().answerOpenFile(file);
            commands.open().execute();
            app.run(() -> app.component(JTextArea.class).append("!"));
            app.dialogs().answerOpenFile(file);

            commands.open().execute();

            assertThat(app.dialogs().savePrompts()).isEmpty();
            assertThat(app.component(JTextArea.class).getText()).isEqualTo("Dear reader!");
            assertThat(app.frames().getFirst().toFrontCount()).isEqualTo(1);
        }
    }

    @Test
    void actionMapExampleRunsTheCommandsOfEachSceneOnItsOwnScene() throws Exception {
        try (AppTester<ActionMapExample> app = AppTester.start(ActionMapExample.class)) {
            ActionMapExample example = app.application();
            BaseSceneCommands base = app.commands(BaseSceneCommands.class);
            DerivedSceneACommands sceneA = app.commands(DerivedSceneACommands.class);
            DerivedSceneBCommands sceneB = app.commands(DerivedSceneBCommands.class);
            assertThat(example.scenes())
                    .extracting(scene -> ((TitledBorder) scene.getBorder()).getTitle())
                    .containsExactly("BaseScenePanel", "DerivedScenePanelA", "DerivedScenePanelB");
            assertThat(example.scenes())
                    .allSatisfy(scene -> assertThat(scene.getIcon()).isNotNull());

            base.create().execute();
            sceneA.create().execute();
            sceneA.create().execute();
            sceneB.create().execute();
            sceneB.create().execute();
            sceneB.create().execute();

            assertThat(example.scenes()).extracting(ScenePanel::nodeCount).containsExactly(1, 2, 3);
            assertThat(sceneB.remove().asAction().isEnabled()).isFalse();

            ScenePanel panel = example.scenes().get(2);
            onEdt(() -> panel.select(panel.nodeBounds(1).getLocation()));
            onEdt(() -> {});

            assertThat(sceneA.remove().asAction().isEnabled()).isFalse();
            assertThat(sceneB.remove().asAction().isEnabled()).isTrue();
            assertThat(sceneB.remove().execute()).isTrue();
            assertThat(example.scenes()).extracting(ScenePanel::nodeCount).containsExactly(1, 2, 2);
            assertThat(popupTexts(panel)).containsExactly("Create", "Remove");
        }
    }

    private static List<String> popupTexts(ScenePanel panel) {
        return Arrays.stream(panel.getComponentPopupMenu().getComponents())
                .map(item -> ((JMenuItem) item).getText())
                .toList();
    }

    private static void onEdt(Runnable action) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(action);
    }
}
