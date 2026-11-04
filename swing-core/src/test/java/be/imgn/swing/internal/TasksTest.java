package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.UiExecutor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** The hooks, cancellation, chains and partial results of tasks, with a UI thread that the test drives. */
class TasksTest {

    private final QueueExecutor ui = new QueueExecutor();
    private final List<Throwable> unhandled = new CopyOnWriteArrayList<>();
    private final TaskRunner runner = new TaskRunner(ui, unhandled::add);
    private final DisposalScope scope = DisposalScope.root("test");
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @Test
    void aTaskCancelledAfterItsWorkReturnedRunsOnCancelThenOnEnd() throws InterruptedException {
        Task<String> task = Task.of(() -> "done")
                .onSuccess(seen::add)
                .onCancel(() -> seen.add("cancelled"))
                .onEnd(() -> seen.add("end"));
        RunningTask running = runner.start(task, scope, "task");
        assertThat(running.await(Duration.ofSeconds(5))).isTrue();

        running.cancel();
        ui.runUntil(running::isDone);

        assertThat(seen).containsExactly("cancelled", "end");
    }

    @Test
    void aTaskCancelledAfterItsWorkThrewRunsOnCancelAndReportsNothing() throws InterruptedException {
        Task<String> task = Task.<String>of(() -> {
                    throw new IllegalStateException("expected");
                })
                .onFailure(failure -> seen.add("failed"))
                .onCancel(() -> seen.add("cancelled"))
                .onEnd(() -> seen.add("end"));
        RunningTask running = runner.start(task, scope, "task");
        assertThat(running.await(Duration.ofSeconds(5))).isTrue();

        running.cancel();
        ui.runUntil(running::isDone);

        assertThat(seen).containsExactly("cancelled", "end");
        assertThat(unhandled).isEmpty();
    }

    @Test
    void onEndRunsAfterTheOtherHooksHoweverTheTaskEnded() throws InterruptedException {
        IllegalStateException failure = new IllegalStateException("expected");

        run(Task.of(() -> "done").onEnd(() -> seen.add("end")).onSuccess(seen::add));
        run(Task.of(() -> {
                    throw failure;
                })
                .onEnd(() -> seen.add("end")));

        assertThat(seen).containsExactly("done", "end", "end");
        assertThat(unhandled).containsExactly(failure);
    }

    @Test
    void aHookThatThrowsGoesToTheFailureHandlerAndTheTaskProceeds() throws InterruptedException {
        IllegalStateException failure = new IllegalStateException("expected");
        Task<String> task = Task.of(() -> "done")
                .onSuccess(result -> {
                    throw failure;
                })
                .onSuccess(seen::add)
                .onEnd(() -> seen.add("end"));

        RunningTask running = run(task);

        assertThat(seen).containsExactly("done", "end");
        assertThat(unhandled).containsExactly(failure);
        assertThat(running.isDone()).isTrue();
        assertThat(runner.running()).isEmpty();
    }

    @Test
    void aHookThatThrowsAnErrorGoesToTheFailureHandlerAndTheTaskProceeds() throws InterruptedException {
        InternalError failure = new InternalError("A broken invariant");
        Task<String> task = Task.of(() -> "done")
                .onSuccess(result -> {
                    throw failure;
                })
                .onSuccess(seen::add)
                .onEnd(() -> seen.add("end"));

        RunningTask running = run(task);

        assertThat(seen).containsExactly("done", "end");
        assertThat(unhandled).containsExactly(failure);
        assertThat(running.isDone()).isTrue();
    }

    @Test
    void aPreparedTaskCancelledBeforeItBeginsNeverStarts() throws InterruptedException {
        Task<String> task = Task.of(() -> {
                    seen.add("work");
                    return "done";
                })
                .onSuccess(seen::add)
                .onCancel(() -> seen.add("cancelled"))
                .onEnd(() -> seen.add("end"));
        RunningTask running = runner.prepare(task, scope, "task", false);
        assertThat(runner.running()).containsExactly(running);

        running.cancel();
        ui.runUntil(running::isDone);
        running.begin();

        assertThat(seen).containsExactly("cancelled", "end");
        assertThat(runner.running()).isEmpty();
    }

    @Test
    void closingTheScopeCancelsAPreparedTask() throws InterruptedException {
        RunningTask running =
                runner.prepare(Task.of(() -> "done").onCancel(() -> seen.add("cancelled")), scope, "task", false);

        scope.close();
        ui.runUntil(running::isDone);

        assertThat(seen).containsExactly("cancelled");
    }

    @Test
    void aChainRunsTheNextTaskWithTheResultAfterTheFirstTasksHooks() throws InterruptedException {
        Task<Integer> chain = Task.of(() -> 1)
                .onSuccess(value -> seen.add("first " + value))
                .onEnd(() -> seen.add("first end"))
                .then(value -> {
                    seen.add("next " + value);
                    return Task.of(() -> value + 1).onSuccess(second -> seen.add("second " + second));
                })
                .onSuccess(value -> seen.add("chain " + value))
                .onEnd(() -> seen.add("chain end"));

        run(chain);

        assertThat(seen).containsExactly("first 1", "first end", "next 1", "second 2", "chain 2", "chain end");
    }

    @Test
    void chainsChain() throws InterruptedException {
        Task<String> chain = Task.of(() -> 1)
                .then(value -> Task.of(() -> value * 10))
                .then(value -> Task.of(() -> "result " + value))
                .onSuccess(seen::add);

        run(chain);

        assertThat(seen).containsExactly("result 10");
    }

    @Test
    void nextMayReturnAChain() throws InterruptedException {
        Task<String> chain = Task.of(() -> 1)
                .then(value -> Task.of(() -> value + 1)
                        .onSuccess(second -> seen.add("second " + second))
                        .then(second -> Task.of(() -> "result " + second))
                        .onSuccess(inner -> seen.add("inner " + inner)))
                .onSuccess(value -> seen.add("chain " + value));

        run(chain);

        assertThat(seen).containsExactly("second 2", "inner result 2", "chain result 2");
    }

    @Test
    void everyStepRunsOnAThreadOfTheTasksName() throws InterruptedException {
        Task<String> chain = Task.of(() -> Thread.currentThread().getName())
                .then(first ->
                        Task.of(() -> first + " " + Thread.currentThread().getName()))
                .onSuccess(seen::add);

        runner.start(chain, scope, "load");
        ui.runUntil(() -> !seen.isEmpty());

        assertThat(seen).containsExactly("load load");
    }

    @Test
    void aChainWhoseNextReturnsNoTaskSucceedsWithNull() throws InterruptedException {
        Task<String> chain =
                Task.of(() -> 1).then(value -> Task.<String>none()).onSuccess(value -> seen.add("chain " + value));

        run(chain);

        assertThat(seen).containsExactly("chain null");
    }

    @Test
    void aFailingFirstTaskFailsTheChainWithoutCallingNextAndIsReportedOnce() throws InterruptedException {
        IllegalStateException failure = new IllegalStateException("expected");
        Task<Integer> chain = Task.<Integer>of(() -> {
                    throw failure;
                })
                .onEnd(() -> seen.add("first end"))
                .then(value -> {
                    seen.add("next");
                    return Task.of(() -> value);
                })
                .then(value -> Task.of(() -> value))
                .onEnd(() -> seen.add("chain end"));

        run(chain);

        assertThat(seen).containsExactly("first end", "chain end");
        assertThat(unhandled).containsExactly(failure);
    }

    @Test
    void aFailureHandledByTheFailingTaskOrTheChainIsNotReported() throws InterruptedException {
        IllegalStateException failure = new IllegalStateException("expected");

        run(Task.<Integer>of(() -> {
                    throw failure;
                })
                .onFailure(thrown -> seen.add("first failed"))
                .then(value -> Task.of(() -> value))
                .onFailure(thrown -> seen.add("chain failed")));
        run(Task.of(() -> 1)
                .then(value -> Task.<Integer>of(() -> {
                    throw failure;
                }))
                .onFailure(thrown -> seen.add("chain failed " + thrown.getMessage())));

        assertThat(seen).containsExactly("first failed", "chain failed", "chain failed expected");
        assertThat(unhandled).isEmpty();
    }

    @Test
    void aFailingSecondTaskWithoutHandlersIsReportedOnce() throws InterruptedException {
        IllegalStateException failure = new IllegalStateException("expected");

        run(Task.of(() -> 1)
                .then(value -> Task.<Integer>of(() -> {
                            throw failure;
                        })
                        .onEnd(() -> seen.add("second end")))
                .onEnd(() -> seen.add("chain end")));

        assertThat(seen).containsExactly("second end", "chain end");
        assertThat(unhandled).containsExactly(failure);
    }

    @Test
    void aFailureHandledByTheSecondTaskAloneIsNotReported() throws InterruptedException {
        IllegalStateException failure = new IllegalStateException("expected");

        run(Task.of(() -> 1)
                .then(value -> Task.<Integer>of(() -> {
                            throw failure;
                        })
                        .onFailure(thrown -> seen.add("second failed " + thrown.getMessage())))
                .onSuccess(value -> seen.add("chain succeeded")));

        assertThat(seen).containsExactly("second failed expected");
        assertThat(unhandled).isEmpty();
    }

    @Test
    void nextThrowingOrReturningNullFailsTheChain() throws InterruptedException {
        IllegalStateException failure = new IllegalStateException("expected");
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        run(Task.of(() -> 1)
                .<Integer>then(value -> {
                    throw failure;
                })
                .onFailure(failures::add));
        run(Task.of(() -> 1).<Integer>then(value -> null).onFailure(failures::add));

        assertThat(failures).hasSize(2);
        assertThat(failures.getFirst()).isSameAs(failure);
        assertThat(failures.getLast()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theFirstTaskBelongsToItsChain() throws InterruptedException {
        Task<Integer> first = Task.of(() -> 1);
        Task<Integer> chain = first.then(value -> Task.of(() -> value));

        assertThatThrownBy(() -> runner.start(first, scope, "task"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("belongs to a chain");
        assertThatThrownBy(() -> first.then(value -> Task.of(() -> value)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already belongs to a chain");

        run(chain);

        assertThatThrownBy(() -> chain.then(value -> Task.of(() -> value)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has started");
    }

    @Test
    void nextReturningATaskThatStartedOrBelongsToAChainFailsTheChain() throws InterruptedException {
        Task<Integer> started = Task.of(() -> 2);
        run(started);
        Task<Integer> owned = Task.of(() -> 3);
        owned.then(value -> Task.of(() -> value));
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        run(Task.of(() -> 1).then(value -> started).onFailure(failures::add));
        run(Task.of(() -> 1).then(value -> owned).onFailure(failures::add));

        assertThat(failures)
                .extracting(Throwable::getMessage)
                .containsExactly(
                        "The task that next returned has already started",
                        "The task that next returned already belongs to a chain");
        assertThat(failures).allMatch(IllegalStateException.class::isInstance);
    }

    @Test
    void chainingNoTaskRunsNothing() {
        Task<String> none = Task.none();

        Task<Integer> chain = none.then(value -> {
            seen.add("next");
            return Task.of(() -> 1);
        });

        assertThat(chain).isSameAs(Task.none());
        assertThat(none.onEnd(() -> seen.add("end"))).isSameAs(Task.none());
        assertThat(seen).isEmpty();
    }

    @Test
    void onEndAndThenRejectNull() {
        // Through lists, which NullAway doesn't check, to reach the tasks' own checks.
        List<Runnable> handlers = new ArrayList<>();
        handlers.add(null);
        List<Function<Object, Task<Object>>> nexts = new ArrayList<>();
        nexts.add(null);

        for (Task<Object> task : List.of(Task.of(() -> new Object()), Task.none())) {
            assertThatThrownBy(() -> task.onEnd(handlers.getFirst())).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> task.then(nexts.getFirst())).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void cancellingAChainCancelsTheTaskThatRuns() throws InterruptedException {
        CountDownLatch secondStarted = new CountDownLatch(1);
        Task<String> chain = Task.of(() -> "first")
                .onSuccess(seen::add)
                .then(value -> Task.of((TaskContext context) -> {
                            secondStarted.countDown();
                            Thread.sleep(Duration.ofMinutes(1));
                            return "second";
                        })
                        .onSuccess(seen::add)
                        .onCancel(() -> seen.add("second cancelled")))
                .onSuccess(seen::add)
                .onCancel(() -> seen.add("chain cancelled"));
        RunningTask running = runner.start(chain, scope, "task");
        ui.runUntil(() -> secondStarted.getCount() == 0);

        running.cancel();
        ui.runUntil(running::isDone);

        assertThat(seen).containsExactly("first", "second cancelled", "chain cancelled");
    }

    @Test
    void aStepShowsItsOwnProgressAndMessage() throws InterruptedException {
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<String> chain = Task.of((TaskContext context) -> {
                    context.progress(0.5);
                    context.message("first");
                    return "first";
                })
                .then(value -> Task.of((TaskContext context) -> {
                    secondStarted.countDown();
                    release.await();
                    return "second";
                }));
        RunningTask running = runner.start(chain, scope, "task");
        ui.runUntil(() -> secondStarted.getCount() == 0);

        assertThat(running.progressValue().get()).isEqualTo(-1.0);
        assertThat(running.messageValue().get()).isEmpty();

        release.countDown();
        ui.runUntil(running::isDone);
    }

    @Test
    void publishedValuesReachOnPublishInBatchesBeforeOnSuccess() throws InterruptedException {
        List<List<Integer>> batches = new CopyOnWriteArrayList<>();
        CountDownLatch published = new CountDownLatch(1);
        Task<String> together = Task.<String, Integer>of(
                        (context, publish) -> {
                            publish.accept(1);
                            publish.accept(2);
                            publish.accept(3);
                            published.countDown();
                            return "done";
                        },
                        batch -> {
                            batches.add(batch);
                            seen.add("published");
                        })
                .onSuccess(seen::add);

        RunningTask running = runner.start(together, scope, "task");
        // The UI thread takes nothing until the work has published every value, so they come in one batch.
        assertThat(published.await(5, TimeUnit.SECONDS)).isTrue();
        ui.runUntil(running::isDone);

        assertThat(batches).containsExactly(List.of(1, 2, 3));
        assertThat(seen).containsExactly("published", "done");
    }

    @Test
    void valuesPublishedFromAnotherThreadReachOnPublishBeforeOnFailure() throws InterruptedException {
        List<List<Integer>> batches = new CopyOnWriteArrayList<>();
        IllegalStateException failure = new IllegalStateException("expected");
        Task<String> task = Task.<String, Integer>of(
                        (context, publish) -> {
                            Thread helper = Thread.ofPlatform().start(() -> {
                                publish.accept(1);
                                publish.accept(2);
                            });
                            helper.join();
                            throw failure;
                        },
                        batch -> {
                            batches.add(batch);
                            seen.add("published");
                        })
                .onFailure(thrown -> seen.add("failed " + thrown.getMessage()));

        run(task);

        // The UI thread may take the values in one batch or two, but takes them all before the failure.
        assertThat(batches.stream().flatMap(List::stream)).containsExactly(1, 2);
        assertThat(seen).last().isEqualTo("failed expected");
        assertThat(seen.subList(0, seen.size() - 1)).isNotEmpty().containsOnly("published");
        assertThat(unhandled).isEmpty();
    }

    @Test
    void valuesPublishedAfterADeliveryComeInTheNextBatch() throws InterruptedException {
        List<List<Integer>> batches = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        Task<String> task = Task.<String, Integer>of(
                (context, publish) -> {
                    publish.accept(1);
                    delivered.await();
                    publish.accept(2);
                    return "done";
                },
                batch -> {
                    batches.add(batch);
                    delivered.countDown();
                });

        run(task);

        assertThat(batches).containsExactly(List.of(1), List.of(2));
    }

    @Test
    void valuesNotDeliveredWhenTheTaskIsCancelledAreDropped() throws InterruptedException {
        List<List<Integer>> batches = new CopyOnWriteArrayList<>();
        CountDownLatch published = new CountDownLatch(1);
        Task<String> task = Task.<String, Integer>of(
                        (context, publish) -> {
                            publish.accept(1);
                            published.countDown();
                            while (!context.isCancelled()) {
                                Thread.onSpinWait();
                            }
                            publish.accept(2);
                            return "done";
                        },
                        batches::add)
                .onSuccess(seen::add)
                .onCancel(() -> seen.add("cancelled"));
        RunningTask running = runner.start(task, scope, "task");
        assertThat(published.await(5, TimeUnit.SECONDS)).isTrue();

        running.cancel();
        ui.runUntil(running::isDone);

        assertThat(batches).isEmpty();
        assertThat(seen).containsExactly("cancelled");
    }

    @Test
    void publishRejectsNullAndValuesAfterTheWorkReturned() throws InterruptedException {
        AtomicReference<Consumer<? super String>> publisher = new AtomicReference<>();
        Task<String> task = Task.<String, String>of(
                        (context, publish) -> {
                            publisher.set(publish);
                            try {
                                publish.accept(null);
                            } catch (NullPointerException e) {
                                seen.add("null rejected");
                            }
                            return "done";
                        },
                        batch -> {})
                .onSuccess(seen::add);

        run(task);

        assertThat(seen).containsExactly("null rejected", "done");
        Consumer<? super String> publish = Objects.requireNonNull(publisher.get());
        assertThatThrownBy(() -> publish.accept("late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The work has returned: it can't publish any more");
    }

    private RunningTask run(Task<?> task) throws InterruptedException {
        RunningTask running = runner.start(task, scope, "task");
        ui.runUntil(running::isDone);
        return running;
    }

    /** A UI thread that the test drives: posted work runs when the test runs it. */
    private static final class QueueExecutor implements UiExecutor {
        private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable task) {
            queue.add(task);
        }

        @Override
        public boolean isUiThread() {
            return true;
        }

        // Runs the posted work until the condition holds, which a task's thread may also make hold.
        void runUntil(BooleanSupplier condition) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!condition.getAsBoolean()) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("The condition did not hold within 5 seconds");
                }
                Runnable next = queue.poll(10, TimeUnit.MILLISECONDS);
                if (next != null) {
                    next.run();
                }
            }
        }
    }
}
