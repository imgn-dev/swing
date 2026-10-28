package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskExamplesTest {

    @TempDir
    Path directory;

    @Test
    void singleFrameExample5LoadsTheFirstImageOnceReadyAndMovesBetweenImages() {
        try (AppTester<SingleFrameExample5> app = AppTester.start(SingleFrameExample5.class)) {
            SingleFrameExample5Commands commands = app.commands(SingleFrameExample5Commands.class);
            JLabel image = app.component(JLabel.class);
            assertThat(image.getIcon()).isNotNull();
            assertThat(image.getToolTipText()).isEqualTo("Image from resources/mars-1.png  [320 x 240]");
            assertThat(commands.previousImage().asAction().isEnabled()).isFalse();
            assertThat(commands.stopLoading().asAction().isEnabled()).isFalse();

            commands.nextImage().execute();
            commands.nextImage().execute();

            assertThat(image.getToolTipText()).isEqualTo("Image from resources/mars-3.png  [320 x 240]");
            assertThat(commands.nextImage().asAction().isEnabled()).isFalse();
            assertThat(commands.previousImage().asAction().isEnabled()).isTrue();
        }
    }

    @Test
    void blockingExample1DisablesTheCommandWhileItsTaskRuns() throws Exception {
        try (AppTester<BlockingExample1> app = AppTester.start(BlockingExample1.class)) {
            app.application().step(Duration.ofMillis(20));
            BlockingExample1Commands commands = app.commands(BlockingExample1Commands.class);

            onEdt(() -> commands.blockAction()
                    .asAction()
                    .actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "test")));
            AtomicBoolean enabled = new AtomicBoolean(true);
            onEdt(() -> enabled.set(commands.blockAction().asAction().isEnabled()));

            assertThat(enabled).isFalse();
            assertThat(app.isRunning()).isTrue();
            awaitStatus(app, "Done");
            assertThat(commands.blockAction().asAction().isEnabled()).isTrue();
        }
    }

    @Test
    void blockingExample1CoversTheWindowWhileItsTaskRuns() throws Exception {
        try (AppTester<BlockingExample1> app = AppTester.start(BlockingExample1.class)) {
            app.application().step(Duration.ofMillis(20));
            BlockingExample1Commands commands = app.commands(BlockingExample1Commands.class);
            JRootPane rootPane =
                    SwingUtilities.getRootPane(app.components(JButton.class).getFirst());

            onEdt(() -> commands.blockWindow()
                    .asAction()
                    .actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "test")));
            AtomicReference<Component> glassPane = new AtomicReference<>(rootPane);
            onEdt(() -> glassPane.set(rootPane.getGlassPane()));

            assertThat(glassPane.get())
                    .matches(Component::isVisible)
                    .extracting(pane -> pane.getClass().getSimpleName())
                    .isEqualTo("BlockingGlassPane");
            awaitStatus(app, "Done");
            AtomicBoolean visible = new AtomicBoolean(true);
            onEdt(() -> visible.set(rootPane.getGlassPane().isVisible()));
            assertThat(visible).isFalse();
        }
    }

    @Test
    void blockingExample1DisablesTheButtonThatStartedTheTask() throws Exception {
        try (AppTester<BlockingExample1> app = AppTester.start(BlockingExample1.class)) {
            app.application().step(Duration.ofMillis(20));
            JButton button = app.components(JButton.class).stream()
                    .filter(candidate -> "Block Component".equals(candidate.getText()))
                    .findFirst()
                    .orElseThrow();

            onEdt(button::doClick);
            AtomicBoolean enabled = new AtomicBoolean(true);
            onEdt(() -> enabled.set(button.isEnabled()));

            assertThat(enabled).isFalse();
            awaitStatus(app, "Done");
            assertThat(button.isEnabled()).isTrue();
        }
    }

    @Test
    void periodicTaskExampleShowsTheElapsedTime() throws InterruptedException {
        try (AppTester<PeriodicTaskExample> app = AppTester.start(PeriodicTaskExample.class)) {
            app.ticker().advance(Duration.ofSeconds(3));
            String text = "";
            for (int attempt = 0; attempt < 50 && !text.startsWith("Elapsed time"); attempt++) {
                Thread.sleep(100);
                text = app.component(JLabel.class).getText();
            }

            assertThat(text).isEqualTo("Elapsed time: 3,000");
        }
    }

    @Test
    void actionExample4ListsTheFilesOfADirectory() throws Exception {
        Files.createDirectories(directory.resolve("sub"));
        Files.writeString(directory.resolve("a.txt"), "a");
        Files.writeString(directory.resolve("sub").resolve("b.txt"), "b");
        try (AppTester<ActionExample4> app = AppTester.start(ActionExample4.class)) {
            ActionExample4 example = app.application();
            onEdt(() -> example.root.setText(directory.toString()));

            app.commands(ActionExample4Commands.class).go().execute();

            assertThat(example.files.size()).isEqualTo(2);
            assertThat(app.components(JLabel.class).getLast().getText()).isEqualTo("2 files listed");
            assertThat(app.commands(ActionExample4Commands.class)
                            .stop()
                            .asAction()
                            .isEnabled())
                    .isFalse();
        }
    }

    private static void awaitStatus(AppTester<BlockingExample1> app, String status) throws InterruptedException {
        for (int attempt = 0;
                attempt < 100 && !status.equals(app.application().status().get());
                attempt++) {
            Thread.sleep(50);
        }
        assertThat(app.isRunning()).isTrue();
        assertThat(app.application().status().get()).isEqualTo(status);
    }

    private static void onEdt(Runnable code) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(code);
    }
}
