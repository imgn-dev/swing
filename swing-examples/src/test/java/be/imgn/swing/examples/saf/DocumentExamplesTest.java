package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
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
    void documentExampleSavesOnlyModifiedText() throws Exception {
        try (AppTester<DocumentExample> app = AppTester.start(DocumentExample.class)) {
            DocumentExample example = app.application();
            DocumentExampleCommands commands = app.commands(DocumentExampleCommands.class);
            Path file = directory.resolve("notes.txt");
            assertThat(app.title()).isEqualTo("untitled.txt - DocumentExample");
            assertThat(commands.save().asAction().isEnabled()).isFalse();

            onEdt(() -> {
                example.file(file);
                app.component(JTextArea.class).setText("Hello");
            });

            assertThat(example.modified.get()).isTrue();
            assertThat(commands.save().execute()).isTrue();
            assertThat(Files.readString(file)).isEqualTo("Hello");
            assertThat(example.modified.get()).isFalse();
            onEdt(() -> {});
            assertThat(app.title()).isEqualTo("notes.txt - DocumentExample");
            assertThat(commands.save().execute()).isFalse();
        }
    }

    @Test
    void documentExampleAsksBeforeExitingWithModifications() throws Exception {
        try (AppTester<DocumentExample> app = AppTester.start(DocumentExample.class)) {
            onEdt(() -> app.component(JTextArea.class).setText("Hello"));
            app.dialogs().answerConfirm(false);

            app.commands(StandardCommands.class).exit().execute();

            assertThat(app.isRunning()).isTrue();
            assertThat(app.dialogs().confirmed())
                    .containsExactly("The text in the untitled.txt file has changed.\nDo you really want to exit?");

            app.dialogs().answerConfirm(true);
            app.commands(StandardCommands.class).exit().execute();

            assertThat(app.isRunning()).isFalse();
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
