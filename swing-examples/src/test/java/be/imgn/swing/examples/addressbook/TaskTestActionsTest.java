package be.imgn.swing.examples.addressbook;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.BoundCommand;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JMenuBar;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The serial command and the chained commands of the Address Book's task tests. */
class TaskTestActionsTest {

    @Test
    void runsTwoActivationsOfTheSerialCommandOneAfterTheOther() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            TaskTestActionsCommands tasks = app.commands(TaskTestActionsCommands.class);
            TaskTestActions actions = app.bean(TaskTestActions.class);
            actions.chunk(Duration.ofMillis(1));
            actions.hold(name -> release.await());

            app.activate(tasks.oneTaskSerialExecutor());
            awaitLogged(actions, "Task `oneTaskSerialExecutor` just started");
            app.activate(tasks.oneTaskSerialExecutor());
            assertThat(serial(actions)).containsExactly("Task `oneTaskSerialExecutor` just started");
            assertThat(fromEdt(() -> tasks.oneTaskSerialExecutor().running().get()))
                    .isTrue();
            release.countDown();
            app.run(() -> {});

            assertThat(serial(actions))
                    .containsExactly(
                            "Task `oneTaskSerialExecutor` just started",
                            "Task `oneTaskSerialExecutor` finished",
                            "Task `oneTaskSerialExecutor` just started",
                            "Task `oneTaskSerialExecutor` finished");
            assertThat(tasks.oneTaskSerialExecutor().running().get()).isFalse();
        }
    }

    @Test
    void twoSerialTaskDialogBlockerRunsItsChainAsOneTaskThatBlocksTheApplicationOnce() {
        assertChain("twoSerialTaskDialogBlocker", TaskTestActionsCommands::twoSerialTaskDialogBlocker);
    }

    @Test
    void twoSerialGroupsDialogBlockerRunsItsChainAsOneTaskThatBlocksTheApplicationOnce() {
        assertChain("twoSerialGroupsDialogBlocker", TaskTestActionsCommands::twoSerialGroupsDialogBlocker);
    }

    // Records, in one sequence, each step of the chain as it starts, on its thread, and each blocking glass pane put on
    // the main window or taken off it: one pane, put on before the first step and taken off after the second, shows
    // that the application was blocked once, with no unblocking between the steps.
    private static void assertChain(String command, Function<TaskTestActionsCommands, BoundCommand> chain) {
        List<String> events = new CopyOnWriteArrayList<>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            TaskTestActionsCommands tasks = app.commands(TaskTestActionsCommands.class);
            TaskTestActions actions = app.bean(TaskTestActions.class);
            JRootPane rootPane = app.call(() -> SwingUtilities.getRootPane(app.component(JMenuBar.class)));
            Component unblocked = app.call(rootPane::getGlassPane);
            app.run(() -> rootPane.addContainerListener(new GlassPanes(events)));
            actions.chunk(Duration.ofMillis(1));
            actions.hold(
                    name -> events.add(name + " on " + Thread.currentThread().getName()));
            String first = command + " #1";
            String second = command + " #2";

            assertThat(chain.apply(tasks).execute()).isTrue();

            // execute() waited for both steps.
            assertThat(actions.log()).contains("Task `" + first + "` finished", "Task `" + second + "` finished");
            assertThat(events)
                    .containsExactly("blocked", first + " on " + command, second + " on " + command, "unblocked");
            assertThat(app.call(rootPane::getGlassPane)).isSameAs(unblocked);
            assertThat(chain.apply(tasks).running().get()).isFalse();
            List<String> log = actions.log();
            assertThat(log.indexOf("Task `" + first + "` finished"))
                    .isNotNegative()
                    .isLessThan(log.indexOf("Task `" + second + "` just started"));
        }
    }

    private static List<String> serial(TaskTestActions actions) {
        return actions.log().stream()
                .filter(line -> line.endsWith("just started") || line.endsWith("finished"))
                .toList();
    }

    private static void awaitLogged(TaskTestActions actions, String line) throws InterruptedException {
        for (int attempt = 0; attempt < 500 && !actions.log().contains(line); attempt++) {
            Thread.sleep(10);
        }
        assertThat(actions.log()).contains(line);
    }

    // Reads on the EDT without settling, while a command's task must still run: the tester's calls wait for it.
    private static <T> T fromEdt(Supplier<T> code) throws Exception {
        AtomicReference<@Nullable T> result = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> result.set(code.get()));
        return Objects.requireNonNull(result.get());
    }

    // Records the blocking glass panes that the input blocker puts on a root pane and takes off it.
    private record GlassPanes(List<String> events) implements ContainerListener {
        @Override
        public void componentAdded(ContainerEvent event) {
            if (isBlocking(event)) {
                events.add("blocked");
            }
        }

        @Override
        public void componentRemoved(ContainerEvent event) {
            if (isBlocking(event)) {
                events.add("unblocked");
            }
        }

        private static boolean isBlocking(ContainerEvent event) {
            return event.getChild().getClass().getSimpleName().equals("BlockingGlassPane");
        }
    }
}
