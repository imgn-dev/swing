package be.imgn.swing.examples.frames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenFrame;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FrameView;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.UndoStack;
import java.time.DayOfWeek;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.Test;

/** Multi-frame applications, and secondary frames of single-frame ones. */
class FramesTest {

    private static final int ATTEMPTS = 500;
    private static final long PAUSE_MILLIS = 10;

    @Test
    void startsAMultiFrameApplicationWithTheFramesItsStartOpens() {
        try (AppTester<BoardApp> app =
                AppTester.builder(BoardApp.class).arguments("2").start()) {
            List<OpenFrame<?>> frames = app.frames();

            assertThat(frames).hasSize(2);
            assertThat(frames).allSatisfy(frame -> assertThat(frame.view()).isInstanceOf(BoardView.class));
            assertThat(frames.getFirst().title()).isEqualTo("Board");
            assertThat(app.application()).isSameAs(app.application());
            assertThat(app.bean(BoardLog.class).events()).containsExactly("ready");
        }
    }

    @Test
    void failsTheStartupOfAMultiFrameApplicationThatOpensNoFrame() {
        assertThatThrownBy(() -> AppTester.start(EmptyApp.class).close())
                .isInstanceOf(AssertionError.class)
                .cause()
                .hasMessageContaining("EmptyApp.start opened no frame");
    }

    @Test
    void namesAnApplicationWithoutANameAfterItsClass() {
        try (AppTester<HostApp> app = AppTester.start(HostApp.class)) {
            assertThat(app.info().name()).isEqualTo("HostApp");
            assertThat(app.title()).isEqualTo("HostApp");
        }
    }

    @Test
    void actsOnTheFrameOpenedLastUntilAnotherIsBroughtToTheFront() {
        try (AppTester<BoardApp> app =
                AppTester.builder(BoardApp.class).arguments("2").start()) {
            List<OpenFrame<?>> frames = app.frames();
            BoardView first = (BoardView) frames.getFirst().view();
            BoardView second = (BoardView) frames.getLast().view();
            Frames opened = app.bean(Frames.class);

            assertThat(app.call(() -> opened.active().orElseThrow())).isSameAs(second);
            assertThat(app.component(JTabbedPane.class)).isSameAs(second.tabs);

            app.run(() -> opened.toFront(first));

            assertThat(app.call(() -> opened.active().orElseThrow())).isSameAs(first);
            assertThat(app.component(JTabbedPane.class)).isSameAs(first.tabs);
            assertThat(frames.getFirst().toFrontCount()).isEqualTo(1);
            assertThat(app.call(opened::views)).containsExactly(first, second);
        }
    }

    @Test
    void closesAFrameOnceTheVetoesOfItsScopesAgree() {
        try (AppTester<BoardApp> app =
                AppTester.builder(BoardApp.class).arguments("2").start()) {
            OpenFrame<?> frame = app.frames().getFirst();
            BoardView board = (BoardView) frame.view();
            app.run(board::guard);
            app.dialogs().answerConfirm(false);

            assertThat(frame.close()).isFalse();
            assertThat(frame.isOpen()).isTrue();

            app.dialogs().answerConfirm(true);

            assertThat(frame.close()).isTrue();
            assertThat(frame.isOpen()).isFalse();
            assertThat(app.frames()).hasSize(1);
            assertThat(app.isRunning()).isTrue();
            assertThat(app.bean(BoardLog.class).events())
                    .containsExactly("ready", "board asked", "board asked", "board closed");
        }
    }

    @Test
    void exitsWhenTheLastFrameOfAMultiFrameApplicationCloses() {
        try (AppTester<BoardApp> app = AppTester.start(BoardApp.class)) {
            BoardLog log = app.bean(BoardLog.class);

            app.frames().getFirst().close();

            assertThat(app.isRunning()).isFalse();
            assertThat(app.exitStatus()).isZero();
            assertThat(log.events()).containsExactly("ready", "board asked", "application asked", "board closed");
        }
    }

    @Test
    void asksTheApplicationClassOfAMultiFrameApplicationOnExit() {
        try (AppTester<BoardApp> app = AppTester.start(BoardApp.class)) {
            app.run(() -> app.application().allowExit(false));

            app.frames().getFirst().close();

            assertThat(app.isRunning()).isTrue();
            assertThat(app.frames()).hasSize(1);
        }
    }

    @Test
    void closesASecondaryFrameOfASingleFrameApplicationWithoutExiting() {
        try (AppTester<HostApp> app = AppTester.start(HostApp.class)) {
            BoardView board = app.call(() -> app.application().openBoard());

            assertThat(app.frames()).hasSize(2);
            assertThat(app.frames().getFirst().view()).isSameAs(app.application());
            assertThat(app.frame(BoardView.class).view()).isSameAs(board);
            assertThat(app.frame(BoardView.class).title()).isEqualTo("Board");

            assertThat(app.frame(BoardView.class).close()).isTrue();

            assertThat(app.isRunning()).isTrue();
            assertThat(app.frames()).hasSize(1);

            app.frames().getFirst().close();

            assertThat(app.isRunning()).isFalse();
        }
    }

    @Test
    void opensTheVariantsOfAFrameView() {
        try (AppTester<HostApp> app = AppTester.start(HostApp.class)) {
            app.run(() -> app.application().openPalette(PaletteView.Shade.RED));
            app.run(() -> app.application().openPalette(PaletteView.Shade.BLUE));

            List<OpenFrame<?>> frames = app.frames();

            assertThat(frames.get(1).title()).isEqualTo("Red palette");
            assertThat(frames.get(1).component(JLabel.class).getText()).isEqualTo("Red");
            assertThat(frames.get(2).title()).isEqualTo("Blue palette");
            assertThat(frames.get(2).component(JLabel.class).getText()).isEqualTo("Plain");
        }
    }

    @Test
    void rejectsAVariantOfAnotherClassAndOneWithoutResources() {
        try (AppTester<HostApp> app = AppTester.start(HostApp.class)) {
            Frames frames = app.call(() -> app.application().frames());

            assertThatThrownBy(() -> app.call(() -> frames.open(PaletteView.class, DayOfWeek.MONDAY)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not a constant of an enum nested in");
            assertThatThrownBy(() -> app.call(() -> frames.open(PaletteView.class, PaletteView.Other.ANY)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("[variants.any]");
        }
    }

    @Test
    void rejectsClosingOrRaisingAViewThatIsNotInAnOpenFrame() {
        try (AppTester<HostApp> app = AppTester.start(HostApp.class)) {
            Frames frames = app.call(() -> app.application().frames());
            FrameView stranger = app.call(PaletteView::new);

            assertThatThrownBy(() -> app.call(() -> frames.close(stranger)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not in an open frame");
            assertThatThrownBy(() -> app.run(() -> frames.toFront(stranger)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void restoresTheStateOfAFrameUnderItsViewClass() {
        try (AppTester<BoardApp> app = AppTester.builder(BoardApp.class)
                .file(directories -> directories.state().resolve("window-state.toml"), """
                        [windows."be.imgn.swing.examples.frames.BoardView".components.tabs]
                        selected-index = 2
                        """)
                .start()) {
            assertThat(app.frame(BoardView.class).component(JTabbedPane.class).getSelectedIndex())
                    .isEqualTo(2);
        }
    }

    @Test
    void undoesAndRedoesTheUsersEditsOfABoundTextUntilTheFormIsCleanAgain() {
        try (AppTester<BoardApp> app = AppTester.start(BoardApp.class)) {
            OpenFrame<BoardView> frame = app.frame(BoardView.class);
            BoardView board = frame.view();
            StandardCommands commands = frame.commands(StandardCommands.class);
            Form form = frame.bean(Form.class);
            UndoStack stack = frame.bean(UndoStack.class);
            app.run(() -> board.title().set("Set by the application"));

            assertThat(app.call(() -> commands.undo().asAction().isEnabled())).isFalse();

            app.run(() -> form.markClean());
            app.run(() -> board.name.setText("Plan"));

            assertThat(app.call(() -> commands.undo().asAction().isEnabled())).isTrue();
            assertThat(app.call(() -> form.dirty().get())).isTrue();

            app.run(() -> commands.undo().execute());
            app.run(() -> commands.undo().execute());

            assertThat(board.name.getText()).isEqualTo("Set by the application");
            assertThat(app.call(() -> board.title().get())).isEqualTo("Set by the application");
            assertThat(app.call(() -> form.dirty().get())).isFalse();
            assertThat(app.call(() -> stack.canUndo().get())).isFalse();

            app.run(() -> commands.redo().execute());
            app.run(() -> commands.redo().execute());

            assertThat(app.call(() -> board.title().get())).isEqualTo("Plan");
            assertThat(app.call(() -> form.dirty().get())).isTrue();
            assertThat(app.call(() -> commands.redo().asAction().isEnabled())).isFalse();
            assertThatThrownBy(() -> app.run(stack::redo)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void setsTheApplicationsIconOnEveryFrame() {
        try (AppTester<BoardApp> app =
                AppTester.builder(BoardApp.class).arguments("2").start()) {
            assertThat(app.frames())
                    .allSatisfy(frame -> assertThat(frame.iconImage())
                            .hasValueSatisfying(
                                    image -> assertThat(image.getWidth(null)).isEqualTo(16)));
        }
    }

    @Test
    void showsTheProgressOfATaskThatBlocksTheApplicationOnTheTaskbar() throws InterruptedException {
        try (AppTester<BoardApp> app = AppTester.start(BoardApp.class)) {
            Ui ui = app.frame(BoardView.class).bean(Ui.class);
            Stepper first = new Stepper();
            Stepper second = new Stepper();
            assertThat(app.taskbar().progress()).isEmpty();

            app.run(() -> ui.run(first.task(), Block.APPLICATION));
            first.progress(app, 0.5);

            assertThat(app.taskbar().progress()).hasValue(50);

            app.run(() -> ui.run(second.task(), Block.APPLICATION));

            assertThat(app.taskbar().progress()).isEmpty();

            second.progress(app, 0.25);
            first.progress(app, 0.75);

            assertThat(app.taskbar().progress()).hasValue(25);

            second.progress(app, -1);

            assertThat(app.taskbar().progress()).isEmpty();

            second.finish(app);

            assertThat(app.taskbar().progress()).hasValue(75);

            first.finish(app);

            assertThat(app.taskbar().progress()).isEmpty();
        }
    }

    @Test
    void clearsTheTaskbarsProgressWhenTheTaskFails() throws InterruptedException {
        try (AppTester<BoardApp> app = AppTester.start(BoardApp.class)) {
            Ui ui = app.frame(BoardView.class).bean(Ui.class);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch progressed = new CountDownLatch(1);
            Task<String> task = Task.<String>of((TaskContext context) -> {
                        context.progress(0.4);
                        progressed.countDown();
                        release.await();
                        throw new IllegalStateException("Failed");
                    })
                    .onFailure(failure -> {});
            app.run(() -> ui.run(task, Block.APPLICATION));
            progressed.await();
            app.run(() -> {});

            assertThat(app.taskbar().progress()).hasValue(40);

            release.countDown();
            awaitUntil(app, () -> app.taskbar().progress().isEmpty());

            assertThat(app.taskbar().progress()).isEmpty();
        }
    }

    // The tester doesn't wait for the tasks a test runs itself: it settles until their hooks have run.
    private static void awaitUntil(AppTester<?> app, BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < ATTEMPTS && !condition.getAsBoolean(); attempt++) {
            Thread.sleep(PAUSE_MILLIS);
            app.run(() -> {});
        }
    }

    /** A task that reports the progress a test gives it, step by step, until the test lets it finish. */
    private static final class Stepper {

        private static final double FINISH = 2;

        private final BlockingQueue<Double> steps = new LinkedBlockingQueue<>();
        private final BlockingQueue<Double> reported = new LinkedBlockingQueue<>();
        private final CountDownLatch done = new CountDownLatch(1);

        Task<String> task() {
            return Task.<String>of((TaskContext context) -> {
                        while (true) {
                            double step = steps.take();
                            if (step >= FINISH) {
                                return "done";
                            }
                            context.progress(step);
                            reported.add(step);
                        }
                    })
                    .onSuccess(nothing -> done.countDown());
        }

        void progress(AppTester<?> app, double fraction) throws InterruptedException {
            steps.add(fraction);
            reported.take();
            app.run(() -> {});
        }

        void finish(AppTester<?> app) throws InterruptedException {
            steps.add(FINISH);
            awaitUntil(app, () -> done.getCount() == 0);
        }
    }
}
