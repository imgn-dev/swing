package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Injector;
import be.imgn.inject.TypeLiteral;
import be.imgn.swing.Lifecycle;
import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.examples.saf.SecondaryWindow;
import be.imgn.swing.examples.saf.SingleFrameExample6;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.CommandBuilder;
import be.imgn.swing.ui.CommandRegistry;
import be.imgn.swing.ui.Concurrency;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The running state, stop commands, concurrency, chains, partial results and periodic actions, in the counter's view,
 * and in a secondary window of {@link SingleFrameExample6} where a second view is needed. The commands are built as a
 * generated {@code <Class>Commands} builds them, and attached as the framework attaches those.
 */
class CommandTasksTest {

    private static final Class<?> OWNER = CommandTasksTest.class;
    // How long a blocking glass pane waits before it shows its progress indicator.
    private static final Duration INDICATOR_DELAY = Duration.ofMillis(300);

    private final List<String> seen = new CopyOnWriteArrayList<>();

    @Test
    void aCommandRunsFromTheStartOfItsTaskUntilItHasEnded() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<BoundCommand> command = new AtomicReference<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> Task.of(() -> {
                                                release.await();
                                                return "done";
                                            })
                                            .onEnd(() -> seen.add("end, running "
                                                    + Objects.requireNonNull(command.get())
                                                            .running()
                                                            .get())))
                                    .bind()))
                    .get(0);
            command.set(go);
            assertThat(go.running().get()).isFalse();

            app.activate(go);
            assertThat(go.running().get()).isTrue();
            release.countDown();
            app.isRunning();

            assertThat(go.running().get()).isFalse();
            assertThat(seen).containsExactly("end, running true");
        }
    }

    @Test
    void anActivationWaitsForTheOnVirtualThreadDeliveriesButNotForTheTaskItStarts() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            EventBus bus = app.bean(EventBus.class);
            CountLog log = app.bean(CountLog.class);
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> {
                                        bus.publish(new Counted(5));
                                        return Task.of(() -> {
                                            release.await();
                                            return "done";
                                        });
                                    })
                                    .bind()))
                    .get(0);

            app.activate(go);

            assertThat(log.counts()).containsExactly(5);
            assertThat(go.running().get()).isTrue();
            release.countDown();
            app.isRunning();
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void aCommandThatStartsNothingIsNeverRunning() throws Exception {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            List<BoundCommand> commands = attach(
                    app,
                    registry -> List.of(registry.command(OWNER, "nothing")
                            .taskHandler(context -> Task.none())
                            .bind()));
            BoundCommand nothing = commands.getFirst();
            BoundCommand increment = app.commands(CounterAppCommands.class).increment();
            Scope scope = app.bean(Scope.class);
            nothing.running().subscribe(scope, running -> seen.add("nothing " + running));
            increment.running().subscribe(scope, running -> seen.add("increment " + running));

            assertThat(nothing.execute()).isTrue();
            assertThat(increment.execute()).isTrue();

            assertThat(seen).isEmpty();
            assertThat(nothing.running().get()).isFalse();
            assertThat(increment.running().get()).isFalse();
        }
    }

    @Test
    void cancelCancelsEveryTaskOfTheCommand() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> Task.of((TaskContext task) -> {
                                                release.await();
                                                return "done";
                                            })
                                            .onSuccess(seen::add)
                                            .onCancel(() -> seen.add("cancelled"))
                                            .onEnd(() -> seen.add("end")))
                                    .bind()))
                    .get(0);
            app.activate(go);
            app.activate(go);

            go.cancel();

            assertThat(seen).containsExactly("cancelled", "end", "cancelled", "end");
            assertThat(go.running().get()).isFalse();
            go.cancel();
        }
    }

    @Test
    void aStopCommandIsEnabledWhileItsCommandRunsAndCancelsItsTasks() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            // The stop command comes first, as it may in the generated code.
            List<BoundCommand> commands = attach(
                    app,
                    registry -> List.of(
                            registry.command(OWNER, "stop")
                                    .handler(context -> seen.add("stop"))
                                    .cancels("go")
                                    .bind(),
                            registry.command(OWNER, "go")
                                    .taskHandler(context -> Task.of((TaskContext task) -> {
                                                release.await();
                                                return "done";
                                            })
                                            .onSuccess(seen::add)
                                            .onCancel(() -> seen.add("cancelled")))
                                    .bind()));
            BoundCommand stop = commands.get(0);
            BoundCommand go = commands.get(1);
            assertThat(stop.asAction().isEnabled()).isFalse();
            assertThat(stop.execute()).isFalse();

            app.activate(go);
            assertThat(stop.asAction().isEnabled()).isTrue();

            assertThat(stop.execute()).isTrue();
            assertThat(seen).containsExactly("stop", "cancelled");
            assertThat(stop.asAction().isEnabled()).isFalse();
        }
    }

    @Test
    void aRestartingActivationCancelsTheTasksAndStartsOnceTheirHooksHaveRun() throws Exception {
        List<CountDownLatch> releases = List.of(new CountDownLatch(1), new CountDownLatch(1));
        AtomicBoolean nothing = new AtomicBoolean();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            BoundCommand go = attach(
                            app,
                            registry -> List.of(numbered(registry, releases, nothing)
                                    .concurrency(Concurrency.RESTART)
                                    .bind()))
                    .get(0);

            app.activate(go);
            awaitSeen("started 1");
            app.activate(go);
            awaitSeen("started 2");
            nothing.set(true);
            app.activate(go);
            releases.get(1).countDown();
            app.isRunning();

            assertThat(seen).containsExactly("started 1", "cancelled 1", "end 1", "started 2", "succeeded 2", "end 2");
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void aSerialActivationStartsOnceTheTasksBeforeItHaveEnded() throws Exception {
        List<CountDownLatch> releases = List.of(new CountDownLatch(1), new CountDownLatch(1));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            BoundCommand go = attach(
                            app,
                            registry -> List.of(numbered(registry, releases, new AtomicBoolean())
                                    .concurrency(Concurrency.SERIAL)
                                    .block(Block.WINDOW)
                                    .bind()))
                    .get(0);

            app.activate(go);
            awaitSeen("started 1");
            app.activate(go);
            assertThat(seen).containsExactly("started 1");
            assertThat(go.running().get()).isTrue();
            releases.get(0).countDown();
            releases.get(1).countDown();
            app.isRunning();

            assertThat(seen).containsExactly("started 1", "succeeded 1", "end 1", "started 2", "succeeded 2", "end 2");
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void aSerialTaskThatWaitsBlocksNothingUntilItStarts() throws Exception {
        List<CountDownLatch> releases = List.of(new CountDownLatch(1), new CountDownLatch(1));
        AtomicInteger activations = new AtomicInteger();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JRootPane rootPane = SwingUtilities.getRootPane(app.component(JLabel.class));
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> {
                                        int activation = activations.incrementAndGet();
                                        return Task.of((TaskContext task) -> {
                                            task.message("task " + activation);
                                            seen.add("started " + activation);
                                            releases.get(activation - 1).await();
                                            return activation;
                                        });
                                    })
                                    .concurrency(Concurrency.SERIAL)
                                    .block(Block.WINDOW)
                                    .bind()))
                    .get(0);
            app.activate(go);
            awaitSeen("started 1");

            app.activate(go);

            // The glass pane follows the last task it blocks for: the waiting one would show its empty message.
            awaitIndicator(rootPane, new Indicator(false, Indicator.INDETERMINATE, "task 1"));
            assertThat(seen).containsExactly("started 1");
            releases.get(0).countDown();
            awaitSeen("started 2");
            awaitIndicator(rootPane, new Indicator(false, Indicator.INDETERMINATE, "task 2"));
            releases.get(1).countDown();
            app.isRunning();
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void aTaskCancelledWhileItWaitsNeverStarts() throws Exception {
        List<CountDownLatch> releases = List.of(new CountDownLatch(1), new CountDownLatch(1));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            BoundCommand go = attach(
                            app,
                            registry -> List.of(numbered(registry, releases, new AtomicBoolean())
                                    .concurrency(Concurrency.SERIAL)
                                    .bind()))
                    .get(0);
            app.activate(go);
            awaitSeen("started 1");
            app.activate(go);

            go.cancel();

            assertThat(seen).containsExactlyInAnyOrder("started 1", "cancelled 1", "end 1", "cancelled 2", "end 2");
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void aChainRunsUnderTheCommandAndBlocksTheWindowOnce() throws Exception {
        List<Component> glassPanes = new CopyOnWriteArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JRootPane rootPane = SwingUtilities.getRootPane(app.component(JLabel.class));
            Component unblocked = rootPane.getGlassPane();
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> Task.of(
                                                    () -> Thread.currentThread().getName())
                                            .onSuccess(name -> seen.add("first on " + name))
                                            .then(first -> {
                                                glassPanes.add(rootPane.getGlassPane());
                                                seen.add("covered "
                                                        + rootPane.getGlassPane()
                                                                .isVisible());
                                                return Task.of(() -> Thread.currentThread()
                                                                .getName())
                                                        .onSuccess(name -> glassPanes.add(rootPane.getGlassPane()));
                                            })
                                            .onSuccess(name -> seen.add("second on " + name)))
                                    .block(Block.WINDOW)
                                    .bind()))
                    .get(0);

            assertThat(go.execute()).isTrue();

            assertThat(seen).containsExactly("first on go", "covered true", "second on go");
            assertThat(glassPanes).hasSize(2).doesNotContain(unblocked);
            assertThat(glassPanes.get(0)).isSameAs(glassPanes.get(1));
            assertThat(rootPane.getGlassPane()).isSameAs(unblocked);
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void thePublishedValuesReachTheirHandlerBeforeTheTaskSucceeds() throws Exception {
        List<Integer> values = new CopyOnWriteArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            BoundCommand list = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "list")
                                    .taskHandler(context -> Task.<String, Integer>of(
                                                    (task, publish) -> {
                                                        for (int value = 1; value <= 5; value++) {
                                                            publish.accept(value);
                                                        }
                                                        return "done";
                                                    },
                                                    batch -> {
                                                        values.addAll(batch);
                                                        seen.add("published");
                                                    })
                                            .onSuccess(seen::add))
                                    .bind()))
                    .get(0);

            assertThat(list.execute()).isTrue();

            assertThat(values).containsExactly(1, 2, 3, 4, 5);
            assertThat(seen).startsWith("published").endsWith("done");
        }
    }

    @Test
    void aPeriodicActionRunsOncePerElapsedPeriodOfTheTicker() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Ui ui = app.bean(Ui.class);
            EventQueue.invokeAndWait(
                    () -> subscription.set(ui.every(Duration.ofMillis(500), () -> runs.incrementAndGet())));

            app.ticker().advance(Duration.ofMillis(499));
            app.isRunning();
            assertThat(runs).hasValue(0);

            app.ticker().advance(Duration.ofMillis(1));
            app.isRunning();
            assertThat(runs).hasValue(1);

            app.ticker().advance(Duration.ofSeconds(2));
            app.isRunning();
            app.isRunning();
            assertThat(runs).hasValue(2);

            EventQueue.invokeAndWait(
                    () -> Objects.requireNonNull(subscription.get()).close());
            app.ticker().advance(Duration.ofSeconds(1));
            app.isRunning();
            assertThat(runs).hasValue(2);
            assertThatThrownBy(() -> ui.every(Duration.ZERO, () -> {}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("The period must be positive: PT0S");
        }
    }

    @Test
    void closingTheViewsScopeStopsItsPeriodicActionsAndNoneStartsInAClosedScope() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        AtomicReference<Ui> secondary = new AtomicReference<>();
        try (AppTester<SingleFrameExample6> app = AppTester.start(SingleFrameExample6.class)) {
            inSecondaryWindow(app, app.bean(Ui.class), dialog -> {
                Ui ui = dialog.commands(Ui.class);
                secondary.set(ui);
                ui.every(Duration.ofMillis(500), () -> runs.incrementAndGet());
            });

            app.ticker().advance(Duration.ofSeconds(1));
            app.isRunning();
            assertThat(runs).hasValue(0);

            Ui closed = Objects.requireNonNull(secondary.get());
            assertThatThrownBy(() -> app.run(() -> closed.every(Duration.ofMillis(500), () -> runs.incrementAndGet())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageEndingWith(" is closed.");
            app.ticker().advance(Duration.ofSeconds(1));
            app.isRunning();
            assertThat(runs).hasValue(0);
        }
    }

    @Test
    void uiRunBlocksInputOnceForAChain() throws Exception {
        List<Component> glassPanes = new CopyOnWriteArrayList<>();
        CountDownLatch ended = new CountDownLatch(1);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JRootPane rootPane = SwingUtilities.getRootPane(app.component(JLabel.class));
            Component unblocked = app.call(rootPane::getGlassPane);
            Ui ui = app.bean(Ui.class);

            app.run(() -> ui.run(
                    Task.of(() -> "first")
                            .then(first -> {
                                glassPanes.add(rootPane.getGlassPane());
                                seen.add("covered " + rootPane.getGlassPane().isVisible());
                                return Task.of(() -> "second").onSuccess(second -> {
                                    glassPanes.add(rootPane.getGlassPane());
                                    seen.add(
                                            "covered " + rootPane.getGlassPane().isVisible());
                                });
                            })
                            .onEnd(ended::countDown),
                    Block.WINDOW));
            assertThat(ended.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(seen).containsExactly("covered true", "covered true");
            assertThat(glassPanes).hasSize(2).doesNotContain(unblocked);
            assertThat(glassPanes.get(0)).isSameAs(glassPanes.get(1));
            assertThat(app.call(rootPane::getGlassPane)).isSameAs(unblocked);
        }
    }

    @Test
    void aChainOfAnApplicationBlockingCommandBlocksOnceForBothTasks() throws Exception {
        List<Component> glassPanes = new CopyOnWriteArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JRootPane rootPane = SwingUtilities.getRootPane(app.component(JLabel.class));
            Component unblocked = app.call(rootPane::getGlassPane);
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> Task.of(() -> "first")
                                            .then(first -> {
                                                glassPanes.add(rootPane.getGlassPane());
                                                seen.add("covered "
                                                        + rootPane.getGlassPane()
                                                                .isVisible());
                                                return Task.of(() -> "second")
                                                        .onSuccess(second -> glassPanes.add(rootPane.getGlassPane()));
                                            }))
                                    .block(Block.APPLICATION)
                                    .bind()))
                    .get(0);

            assertThat(go.execute()).isTrue();

            assertThat(seen).containsExactly("covered true");
            assertThat(glassPanes).hasSize(2).doesNotContain(unblocked);
            assertThat(glassPanes.get(0)).isSameAs(glassPanes.get(1));
            assertThat(app.call(rootPane::getGlassPane)).isSameAs(unblocked);
        }
    }

    @Test
    void theIndicatorOfAChainFollowsTheStepThatRunsWithoutRestartingItsDelay() throws Exception {
        CountDownLatch firstReported = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        CountDownLatch secondReported = new CountDownLatch(1);
        CountDownLatch releaseEnd = new CountDownLatch(1);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JRootPane rootPane = SwingUtilities.getRootPane(app.component(JLabel.class));
            Component unblocked = app.call(rootPane::getGlassPane);
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> Task.of((TaskContext task) -> {
                                                task.progress(0.5);
                                                task.message("first");
                                                firstReported.countDown();
                                                releaseFirst.await();
                                                return "first";
                                            })
                                            .then(first -> Task.of((TaskContext task) -> {
                                                secondStarted.countDown();
                                                releaseSecond.await();
                                                task.progress(0.25);
                                                task.message("second");
                                                secondReported.countDown();
                                                releaseEnd.await();
                                                return "second";
                                            })))
                                    .block(Block.WINDOW)
                                    .bind()))
                    .get(0);
            app.activate(go);
            assertThat(firstReported.await(5, TimeUnit.SECONDS)).isTrue();
            Component glassPane = onEdt(rootPane::getGlassPane);
            assertThat(indicator(rootPane).visible()).isFalse();

            app.ticker().advance(INDICATOR_DELAY);
            awaitIndicator(rootPane, new Indicator(true, 50, "first"));

            // The ticker doesn't advance any more: an indicator that waited for its delay again would stay hidden.
            releaseFirst.countDown();
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitIndicator(rootPane, new Indicator(true, Indicator.INDETERMINATE, " "));

            releaseSecond.countDown();
            assertThat(secondReported.await(5, TimeUnit.SECONDS)).isTrue();
            awaitIndicator(rootPane, new Indicator(true, 25, "second"));
            assertThat(onEdt(rootPane::getGlassPane)).isSameAs(glassPane);

            releaseEnd.countDown();
            app.isRunning();
            assertThat(app.call(rootPane::getGlassPane)).isSameAs(unblocked);
        }
    }

    @Test
    void theCancelButtonOfTheGlassPaneCancelsTheChain() throws Exception {
        CountDownLatch never = new CountDownLatch(1);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JRootPane rootPane = SwingUtilities.getRootPane(app.component(JLabel.class));
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> Task.of(() -> "first")
                                            .onSuccess(seen::add)
                                            .then(first -> Task.of(() -> {
                                                        seen.add("second started");
                                                        never.await();
                                                        return "second";
                                                    })
                                                    .onSuccess(seen::add)
                                                    .onCancel(() -> seen.add("second cancelled")))
                                            .onSuccess(seen::add)
                                            .onCancel(() -> seen.add("chain cancelled")))
                                    .block(Block.WINDOW)
                                    .bind()))
                    .get(0);
            app.activate(go);
            awaitSeen("second started");

            EventQueue.invokeAndWait(
                    () -> buttons(rootPane.getGlassPane()).getFirst().doClick());
            app.isRunning();

            assertThat(seen).containsExactly("first", "second started", "second cancelled", "chain cancelled");
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void closingTheViewsScopeCancelsTheTasksThatWait() throws Exception {
        List<CountDownLatch> releases = List.of(new CountDownLatch(1), new CountDownLatch(1));
        AtomicReference<BoundCommand> command = new AtomicReference<>();
        try (AppTester<SingleFrameExample6> app = AppTester.start(SingleFrameExample6.class)) {
            inSecondaryWindow(app, app.bean(Ui.class), dialog -> {
                BoundCommand go = attach(
                                dialog.commands(CommandRegistry.class),
                                dialog.commands(Injector.class),
                                registry -> List.of(numbered(registry, releases, new AtomicBoolean())
                                        .concurrency(Concurrency.SERIAL)
                                        .bind()))
                        .get(0);
                command.set(go);
                go.execute();
                awaitSeen("started 1");
                go.execute();
            });
            app.isRunning();

            assertThat(seen).containsExactlyInAnyOrder("started 1", "cancelled 1", "end 1", "cancelled 2", "end 2");
            assertThat(seen).containsSubsequence("cancelled 1", "end 1").containsSubsequence("cancelled 2", "end 2");
            assertThat(Objects.requireNonNull(command.get()).running().get()).isFalse();
        }
    }

    @Test
    void theShutdownCancelsTheTasksThatWait() throws Exception {
        List<CountDownLatch> releases = List.of(new CountDownLatch(1), new CountDownLatch(1));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Lifecycle lifecycle = app.bean(Lifecycle.class);
            app.dialogs().answerConfirm(true);
            BoundCommand go = attach(
                            app,
                            registry -> List.of(numbered(registry, releases, new AtomicBoolean())
                                    .concurrency(Concurrency.SERIAL)
                                    .bind()))
                    .get(0);
            app.activate(go);
            awaitSeen("started 1");
            app.activate(go);

            lifecycle.requestExit();

            assertThat(app.isRunning()).isFalse();
            assertThat(seen).containsExactlyInAnyOrder("started 1", "cancelled 1", "end 1", "cancelled 2", "end 2");
            assertThat(seen).containsSubsequence("cancelled 1", "end 1").containsSubsequence("cancelled 2", "end 2");
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void aRestartingHandlerThatThrowsCancelsNothing() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean throwing = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("expected");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> {
                                        if (throwing.get()) {
                                            throw failure;
                                        }
                                        return Task.of(() -> {
                                                    release.await();
                                                    return "done";
                                                })
                                                .onSuccess(seen::add)
                                                .onCancel(() -> seen.add("cancelled"));
                                    })
                                    .concurrency(Concurrency.RESTART)
                                    .bind()))
                    .get(0);
            app.activate(go);
            throwing.set(true);

            // Whether the handler's exception reaches the caller or the FailureHandler, the activation cancels nothing.
            assertThatThrownBy(() -> app.activate(go))
                    .satisfiesAnyOf(
                            thrown -> assertThat(thrown).isSameAs(failure),
                            thrown -> assertThat(thrown.getCause()).isSameAs(failure));
            assertThat(go.running().get()).isTrue();
            release.countDown();
            app.isRunning();

            assertThat(seen).containsExactly("done");
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void aHookThatThrowsLetsTheTaskEndAndTheWaitingTasksStart() throws Exception {
        List<CountDownLatch> releases = List.of(new CountDownLatch(1), new CountDownLatch(1));
        IllegalStateException failure = new IllegalStateException("expected");
        AtomicInteger activations = new AtomicInteger();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            BoundCommand go = attach(
                            app,
                            registry -> List.of(registry.command(OWNER, "go")
                                    .taskHandler(context -> {
                                        int activation = activations.incrementAndGet();
                                        return Task.of((TaskContext task) -> {
                                                    seen.add("started " + activation);
                                                    releases.get(activation - 1).await();
                                                    return activation;
                                                })
                                                .onSuccess(value -> {
                                                    if (value == 1) {
                                                        throw failure;
                                                    }
                                                })
                                                .onEnd(() -> seen.add("end " + activation));
                                    })
                                    .concurrency(Concurrency.SERIAL)
                                    .bind()))
                    .get(0);
            app.activate(go);
            awaitSeen("started 1");
            app.activate(go);
            releases.get(0).countDown();
            releases.get(1).countDown();

            assertThatThrownBy(app::isRunning)
                    .isInstanceOf(AssertionError.class)
                    .cause()
                    .isSameAs(failure);
            app.isRunning();

            assertThat(seen).containsExactly("started 1", "end 1", "started 2", "end 2");
            assertThat(go.running().get()).isFalse();
        }
    }

    @Test
    void theRunningStateAndCancellationBelongToTheView() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        List<String> states = new CopyOnWriteArrayList<>();
        // One handler for both views, as a command of a @Singleton or @WindowScoped class has.
        Function<CommandRegistry, List<BoundCommand>> build = registry -> List.of(registry.command(OWNER, "go")
                .taskHandler(context -> Task.of(() -> {
                            release.await();
                            return "done";
                        })
                        .onSuccess(seen::add)
                        .onCancel(() -> seen.add("cancelled"))
                        .onEnd(() -> seen.add("end")))
                .bind());
        try (AppTester<SingleFrameExample6> app = AppTester.start(SingleFrameExample6.class)) {
            Ui ui = app.bean(Ui.class);
            BoundCommand main = attach(app, build).get(0);
            app.activate(main);

            inSecondaryWindow(app, ui, dialog -> {
                BoundCommand secondary = attach(
                                dialog.commands(CommandRegistry.class), dialog.commands(Injector.class), build)
                        .get(0);
                states.add("secondary " + secondary.running().get());
                secondary.execute();
                states.add("secondary " + secondary.running().get() + ", main "
                        + main.running().get());
                secondary.cancel();
            });
            awaitSeen("end");
            assertThat(seen).containsExactly("cancelled", "end");
            assertThat(main.running().get()).isTrue();

            release.countDown();
            app.isRunning();

            assertThat(states).containsExactly("secondary false", "secondary true, main true");
            assertThat(seen).containsExactly("cancelled", "end", "done", "end");
            assertThat(main.running().get()).isFalse();
        }
    }

    // A command whose activations are numbered, and whose handler returns no task while nothing is set: each task waits
    // for its release.
    private CommandBuilder numbered(CommandRegistry registry, List<CountDownLatch> releases, AtomicBoolean nothing) {
        AtomicInteger activations = new AtomicInteger();
        return registry.command(OWNER, "go").taskHandler(context -> {
            if (nothing.get()) {
                return Task.none();
            }
            int activation = activations.incrementAndGet();
            return Task.of((TaskContext task) -> {
                        seen.add("started " + activation);
                        releases.get(activation - 1).await();
                        return activation;
                    })
                    .onSuccess(value -> seen.add("succeeded " + value))
                    .onCancel(() -> seen.add("cancelled " + activation))
                    .onEnd(() -> seen.add("end " + activation));
        });
    }

    private void awaitSeen(String event) throws InterruptedException {
        for (int attempt = 0; attempt < 500 && !seen.contains(event); attempt++) {
            Thread.sleep(10);
        }
        assertThat(seen).contains(event);
    }

    // Builds commands as a generated <Class>Commands does, in its constructor, then reports the instance to the
    // construction listeners, as inject does, which attach its commands.
    private static List<BoundCommand> attach(AppTester<?> app, Function<CommandRegistry, List<BoundCommand>> build)
            throws Exception {
        CommandRegistry registry = app.bean(CommandRegistry.class);
        Injector injector = app.bean(Injector.class);
        AtomicReference<List<BoundCommand>> bound = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> bound.set(attach(registry, injector, build)));
        return Objects.requireNonNull(bound.get());
    }

    // On the EDT, in the view of the registry and the injector.
    private static List<BoundCommand> attach(
            CommandRegistry registry, Injector injector, Function<CommandRegistry, List<BoundCommand>> build) {
        Set<ConstructionListener> listeners = injector.getInstance(new TypeLiteral<Set<ConstructionListener>>() {});
        BuiltCommands commands = new BuiltCommands(registry, build);
        listeners.forEach(listener -> listener.constructed(commands, injector));
        return commands.bound;
    }

    // Shows a secondary window, a dialog view and so a second view, runs an action in it on the EDT, then closes it,
    // which closes its view's scope.
    private static void inSecondaryWindow(
            AppTester<SingleFrameExample6> app, Ui ui, DialogAction<SecondaryWindow> action) throws Exception {
        app.dialogs().answerDialog(SecondaryWindow.class, dialog -> {
            try {
                action.run(dialog);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            dialog.component(JButton.class).doClick();
        });
        EventQueue.invokeAndWait(() -> ui.showDialog(SecondaryWindow.class, SecondaryWindow.Name.ONE));
    }

    private static <R> R onEdt(Supplier<R> read) throws Exception {
        AtomicReference<R> result = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> result.set(read.get()));
        return Objects.requireNonNull(result.get());
    }

    // The progress indicator of the window's glass pane, read on the EDT.
    private static Indicator indicator(JRootPane rootPane) throws Exception {
        return onEdt(() -> {
            JProgressBar progress =
                    descendants(rootPane.getGlassPane(), JProgressBar.class).getFirst();
            JLabel message = descendants(rootPane.getGlassPane(), JLabel.class).getFirst();
            return new Indicator(
                    progress.getParent().isVisible(),
                    progress.isIndeterminate() ? Indicator.INDETERMINATE : progress.getValue(),
                    message.getText());
        });
    }

    // The indicator changes on the EDT, as the tasks report, and its delay elapses on its timer.
    private static void awaitIndicator(JRootPane rootPane, Indicator expected) throws Exception {
        Indicator actual = indicator(rootPane);
        for (int attempt = 0; attempt < 500 && !actual.equals(expected); attempt++) {
            Thread.sleep(10);
            actual = indicator(rootPane);
        }
        assertThat(actual).isEqualTo(expected);
    }

    private static <C> List<C> descendants(Component component, Class<C> type) {
        List<C> found = new ArrayList<>();
        if (type.isInstance(component)) {
            found.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(descendants(child, type));
            }
        }
        return found;
    }

    private static List<JButton> buttons(Component component) {
        List<JButton> found = new ArrayList<>();
        if (component instanceof JButton button) {
            found.add(button);
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(buttons(child));
            }
        }
        return found;
    }

    // What the progress indicator shows: whether it is visible, its percentage, and its message.
    private record Indicator(boolean visible, int percent, String message) {
        static final int INDETERMINATE = -1;
    }

    @FunctionalInterface
    private interface DialogAction<V> {
        void run(OpenDialog<V> dialog) throws InterruptedException;
    }

    private static final class BuiltCommands extends StandardCommands {
        private final List<BoundCommand> bound;

        BuiltCommands(CommandRegistry registry, Function<CommandRegistry, List<BoundCommand>> build) {
            super(registry);
            this.bound = build.apply(registry);
        }
    }
}
