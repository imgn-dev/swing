package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;
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
            assertThat(app.call(image::getIcon)).isNotNull();
            assertThat(toolTip(app, image)).isEqualTo("Image from resources/PIA03171.jpg  [6144 x 1536]");
            assertThat(commands.previousImage().asAction().isEnabled()).isFalse();
            assertThat(commands.stopLoading().asAction().isEnabled()).isFalse();

            commands.nextImage().execute();

            assertThat(toolTip(app, image)).isEqualTo("Image from resources/PIA02652.jpg  [4096 x 4096]");
            assertThat(commands.nextImage().asAction().isEnabled()).isTrue();
            assertThat(commands.previousImage().asAction().isEnabled()).isTrue();

            commands.previousImage().execute();

            assertThat(toolTip(app, image)).isEqualTo("Image from resources/PIA03171.jpg  [6144 x 1536]");
            assertThat(commands.previousImage().asAction().isEnabled()).isFalse();
        }
    }

    @Test
    void blockingExample1DisablesTheCommandWhileItsTaskRuns() throws Exception {
        try (AppTester<BlockingExample1> app = AppTester.start(BlockingExample1.class)) {
            app.application().step(Duration.ofMillis(20));
            BlockingExample1Commands commands = app.commands(BlockingExample1Commands.class);

            app.activate(commands.blockAction());

            assertThat(fromEdt(() -> commands.blockAction().asAction().isEnabled()))
                    .isFalse();
            awaitStatus(app, "Done");
            assertThat(app.call(() -> commands.blockAction().asAction().isEnabled()))
                    .isTrue();
        }
    }

    @Test
    void blockingExample1CoversTheWindowWhileItsTaskRuns() throws Exception {
        try (AppTester<BlockingExample1> app = AppTester.start(BlockingExample1.class)) {
            app.application().step(Duration.ofMillis(20));
            BlockingExample1Commands commands = app.commands(BlockingExample1Commands.class);
            JRootPane rootPane = app.call(() ->
                    SwingUtilities.getRootPane(app.components(JButton.class).getFirst()));

            app.activate(commands.blockWindow());
            Component glassPane = fromEdt(rootPane::getGlassPane);

            assertThat(fromEdt(glassPane::isVisible)).isTrue();
            assertThat(glassPane.getClass().getSimpleName()).isEqualTo("BlockingGlassPane");
            awaitStatus(app, "Done");
            assertThat(app.call(() -> rootPane.getGlassPane().isVisible())).isFalse();
        }
    }

    @Test
    void blockingExample1ConsumesTheKeysOfTheBlockedWindow() throws Exception {
        try (AppTester<BlockingExample1> app = AppTester.start(BlockingExample1.class)) {
            app.application().step(Duration.ofMillis(20));
            BlockingExample1Commands commands = app.commands(BlockingExample1Commands.class);
            JButton button = app.components(JButton.class).getFirst();

            app.activate(commands.blockWindow());

            assertThat(fromEdt(() -> dispatch(button))).isTrue();
            awaitStatus(app, "Done");
            assertThat(app.call(() -> dispatch(button))).isFalse();
        }
    }

    @Test
    void blockingExample1DisablesTheButtonThatStartedTheTask() throws Exception {
        try (AppTester<BlockingExample1> app = AppTester.start(BlockingExample1.class)) {
            app.application().step(Duration.ofMillis(20));
            BlockingExample1Commands commands = app.commands(BlockingExample1Commands.class);
            JButton button = app.components(JButton.class).stream()
                    .filter(candidate -> "Block Component".equals(candidate.getText()))
                    .findFirst()
                    .orElseThrow();

            // A click, so that the command knows the component that started it, and the tester doesn't wait for it.
            onEdt(button::doClick);

            // The button shares the command's Action, so the command is blocked, which the Action can't undo.
            assertThat(fromEdt(button::isEnabled)).isFalse();
            assertThat(fromEdt(() -> commands.blockComponent().asAction().isEnabled()))
                    .isFalse();
            awaitStatus(app, "Done");
            assertThat(app.call(button::isEnabled)).isTrue();
            assertThat(app.call(() -> commands.blockComponent().asAction().isEnabled()))
                    .isTrue();
        }
    }

    @Test
    void blockingExample1BuildsItsButtonRowWithTheButtonBar() {
        try (AppTester<BlockingExample1> app = AppTester.start(BlockingExample1.class)) {
            BlockingExample1Commands commands = app.commands(BlockingExample1Commands.class);

            ButtonRows.assertButtonBar(
                    app,
                    commands.blockAction(),
                    commands.blockComponent(),
                    commands.blockWindow(),
                    commands.blockApplication());
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
            app.run(() -> app.component(JTextField.class).setText(directory.toString()));

            app.commands(ActionExample4Commands.class).go().execute();

            assertThat(app.call(() -> app.component(JList.class).getModel().getSize()))
                    .isEqualTo(2);
            assertThat(app.call(() -> app.components(JLabel.class).getLast().getText()))
                    .isEqualTo("2 files listed");
            assertThat(app.commands(ActionExample4Commands.class)
                            .stop()
                            .asAction()
                            .isEnabled())
                    .isFalse();
        }
    }

    @Test
    void singleFrameExample5ShowsTheLastImageAskedFor() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<SingleFrameExample5> app = AppTester.start(SingleFrameExample5.class)) {
            SingleFrameExample5Commands commands = app.commands(SingleFrameExample5Commands.class);
            JLabel image = app.component(JLabel.class);
            app.application().hold(percentageDone -> release.await());

            app.activate(commands.nextImage());
            app.activate(commands.nextImage());
            assertThat(fromEdt(() -> commands.load().running().get())).isTrue();
            release.countDown();
            app.run(() -> {});

            assertThat(toolTip(app, image)).isEqualTo("Image from resources/PIA05108.jpg  [4088 x 2925]");
            assertThat(app.call(image::getIcon)).isNotNull();
            assertThat(commands.load().running().get()).isFalse();
        }
    }

    @Test
    void singleFrameExample5StopsLoadingWithTheCancelledMessage() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<SingleFrameExample5> app = AppTester.start(SingleFrameExample5.class)) {
            SingleFrameExample5Commands commands = app.commands(SingleFrameExample5Commands.class);
            JLabel image = app.component(JLabel.class);
            app.application().hold(percentageDone -> release.await());

            app.activate(commands.refreshImage());
            assertThat(fromEdt(() -> commands.stopLoading().asAction().isEnabled()))
                    .isTrue();
            assertThat(fromEdt(image::getText)).isEqualTo("Loading please wait ...");

            assertThat(commands.stopLoading().execute()).isTrue();
            release.countDown();

            assertThat(app.call(image::getText)).isEqualTo("Cancelled");
            assertThat(app.call(() -> image.getIcon() == null)).isTrue();
            assertThat(commands.stopLoading().asAction().isEnabled()).isFalse();
        }
    }

    @Test
    void singleFrameExample5KeepsTheLoadingMessageWhileTheLoadThatReplacesAnotherRuns() throws Exception {
        Semaphore reading = new Semaphore(0);
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<SingleFrameExample5> app = AppTester.start(SingleFrameExample5.class)) {
            SingleFrameExample5Commands commands = app.commands(SingleFrameExample5Commands.class);
            JLabel image = app.component(JLabel.class);
            // The cancelled load stops at its first hold, so that each permit after the first is the new load's.
            app.application().hold(percentageDone -> {
                reading.release();
                release.await();
            });

            app.activate(commands.refreshImage());
            assertThat(reading.tryAcquire(10, TimeUnit.SECONDS)).isTrue();
            app.activate(commands.nextImage());
            // The new load starts only once the one it replaces has run its hooks.
            assertThat(reading.tryAcquire(10, TimeUnit.SECONDS)).isTrue();

            String text = fromEdt(image::getText);
            release.countDown();
            app.run(() -> {});

            assertThat(text).isEqualTo("Loading please wait ...");
            assertThat(toolTip(app, image)).isEqualTo("Image from resources/PIA02652.jpg  [4096 x 4096]");
        }
    }

    @Test
    void singleFrameExample5AbortsTheReadOnceItsLoadIsCancelled() throws Exception {
        AtomicInteger steps = new AtomicInteger();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        try (AppTester<SingleFrameExample5> app = AppTester.start(SingleFrameExample5.class)) {
            SingleFrameExample5Commands commands = app.commands(SingleFrameExample5Commands.class);
            JLabel image = app.component(JLabel.class);
            // Held at the first step of the read; the reader ignores the interrupt of the cancellation, so that it
            // would report its next steps, the later holds returning at once, unless the listener aborts it.
            app.application().hold(percentageDone -> {
                steps.incrementAndGet();
                held.countDown();
                never.await();
            });

            app.activate(commands.refreshImage());
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(commands.stopLoading().execute()).isTrue();

            assertThat(steps).hasValue(1);
            assertThat(app.call(image::getText)).isEqualTo("Cancelled");
            assertThat(app.call(() -> image.getIcon() == null)).isTrue();
        }
    }

    @Test
    void periodicTaskExampleUpdatesTheLabelEveryHalfSecondOfTheTicker() {
        try (AppTester<PeriodicTaskExample> app = AppTester.start(PeriodicTaskExample.class)) {
            JLabel label = app.component(JLabel.class);
            assertThat(app.call(label::getText)).isEqualTo("Ready...");

            // The tester runs the periodic actions that are due as it settles, after the call's own action.
            app.ticker().advance(PeriodicTaskExample.PERIOD.minusMillis(1));
            app.run(() -> {});
            assertThat(app.call(label::getText)).isEqualTo("Ready...");

            app.ticker().advance(Duration.ofMillis(1));
            app.run(() -> {});
            assertThat(app.call(label::getText)).isEqualTo("Elapsed time: 500");

            app.ticker().advance(PeriodicTaskExample.PERIOD);
            app.run(() -> {});
            assertThat(app.call(label::getText)).isEqualTo("Elapsed time: 1,000");
        }
    }

    @Test
    void actionExample4ShowsOnlyTheSecondListingWhenGoIsActivatedTwice() throws Exception {
        Path first = Files.createDirectories(directory.resolve("first"));
        Files.writeString(first.resolve("a.txt"), "a");
        Files.writeString(first.resolve("b.txt"), "b");
        Path second = Files.createDirectories(directory.resolve("second"));
        Files.writeString(second.resolve("c.txt"), "c");
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<ActionExample4> app = AppTester.start(ActionExample4.class)) {
            ActionExample4Commands commands = app.commands(ActionExample4Commands.class);
            JTextField root = app.component(JTextField.class);
            JList<?> list = app.component(JList.class);
            app.application().hold(release);
            app.run(() -> root.setText(first.toString()));
            app.activate(commands.go());
            awaitOnEdt(() -> list.getModel().getSize() == 1);

            onEdt(() -> root.setText(second.toString()));
            app.activate(commands.go());
            release.countDown();
            app.run(() -> {});

            assertThat(app.call(() -> elements(list)))
                    .containsExactly(second.resolve("c.txt").toFile());
            assertThat(app.call(() -> app.components(JLabel.class).getLast().getText()))
                    .isEqualTo("One file listed");
        }
    }

    @Test
    void actionExample4AddsTheFilesWhileItListsAndNoneOnceStopped() throws Exception {
        Files.writeString(directory.resolve("a.txt"), "a");
        Files.writeString(directory.resolve("b.txt"), "b");
        Files.writeString(directory.resolve("c.txt"), "c");
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<ActionExample4> app = AppTester.start(ActionExample4.class)) {
            ActionExample4Commands commands = app.commands(ActionExample4Commands.class);
            JTextField root = app.component(JTextField.class);
            JList<?> list = app.component(JList.class);
            assertThat(commands.stop().asAction().isEnabled()).isFalse();
            app.application().hold(release);
            app.run(() -> root.setText(directory.toString()));

            app.activate(commands.go());
            awaitOnEdt(() -> list.getModel().getSize() == 1);
            assertThat(fromEdt(() -> commands.stop().asAction().isEnabled())).isTrue();

            assertThat(commands.stop().execute()).isTrue();
            release.countDown();
            app.run(() -> {});

            assertThat(app.call(() -> elements(list))).hasSize(1);
            assertThat(commands.stop().asAction().isEnabled()).isFalse();
            assertThat(commands.go().running().get()).isFalse();
            assertThat(app.call(() -> app.components(JLabel.class).getLast().getText()))
                    .isEqualTo("Listing files in " + directory);
        }
    }

    private static String toolTip(AppTester<?> app, JLabel image) {
        return app.call(image::getToolTipText);
    }

    private static List<Object> elements(JList<?> list) {
        List<Object> elements = new ArrayList<>();
        ListModel<?> model = list.getModel();
        for (int index = 0; index < model.getSize(); index++) {
            elements.add(model.getElementAt(index));
        }
        return elements;
    }

    private static void awaitOnEdt(BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 500 && !fromEdt(condition::getAsBoolean); attempt++) {
            Thread.sleep(10);
        }
        assertThat(fromEdt(condition::getAsBoolean)).isTrue();
    }

    private static void awaitStatus(AppTester<BlockingExample1> app, String status) throws InterruptedException {
        for (int attempt = 0;
                attempt < 100 && !status.equals(app.application().status().get());
                attempt++) {
            Thread.sleep(50);
        }
        assertThat(app.application().status().get()).isEqualTo(status);
    }

    // Dispatches a key press on a component as the keyboard focus manager does, and returns whether it was consumed.
    private static boolean dispatch(Component component) {
        KeyEvent key = new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_A, 'a');
        KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(key);
        return key.isConsumed();
    }

    // Runs on the EDT without settling, while a command's task must still run: the tester's calls wait for it.
    private static void onEdt(Runnable code) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(code);
    }

    // Reads on the EDT without settling, while a command's task must still run: the tester's calls wait for it.
    private static <T> T fromEdt(Supplier<T> code) throws InterruptedException, InvocationTargetException {
        AtomicReference<@Nullable T> result = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> result.set(code.get()));
        return Objects.requireNonNull(result.get());
    }
}
