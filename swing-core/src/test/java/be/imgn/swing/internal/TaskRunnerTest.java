package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.UiExecutor;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class TaskRunnerTest {

    private static final UiExecutor NOW = new UiExecutor() {
        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public boolean isUiThread() {
            return true;
        }
    };

    private final List<Throwable> unhandled = new CopyOnWriteArrayList<>();
    private final TaskRunner runner = new TaskRunner(NOW, unhandled::add);
    private final DisposalScope scope = DisposalScope.root("test");

    @Test
    void runsOnANamedVirtualThread() throws InterruptedException {
        List<String> seen = new CopyOnWriteArrayList<>();
        Task<String> task = Task.of(() -> Thread.currentThread().getName() + " "
                        + Thread.currentThread().isVirtual())
                .onSuccess(seen::add);

        runner.start(task, scope, "save").await(Duration.ofSeconds(5));

        assertThat(seen).containsExactly("save true");
    }

    @Test
    void givesAnUnhandledFailureToTheFailureHandler() throws InterruptedException {
        IllegalStateException failure = new IllegalStateException("expected");

        runner.start(
                        Task.of(() -> {
                            throw failure;
                        }),
                        scope,
                        "task")
                .await(Duration.ofSeconds(5));

        assertThat(unhandled).containsExactly(failure);
    }

    @Test
    void runsTheCancelHooksWhenItsScopeCloses() throws InterruptedException {
        List<String> seen = new CopyOnWriteArrayList<>();
        CountDownLatch started = new CountDownLatch(1);
        Task<String> task = Task.of((TaskContext context) -> {
                    started.countDown();
                    Thread.sleep(Duration.ofMinutes(1));
                    return "done";
                })
                .onSuccess(seen::add)
                .onCancel(() -> seen.add("cancelled"));
        RunningTask running = runner.start(task, scope, "task");
        started.await();

        scope.close();
        running.await(Duration.ofSeconds(5));

        assertThat(seen).containsExactly("cancelled");
        assertThat(running.isCancelled()).isTrue();
        assertThat(runner.running()).isEmpty();
    }
}
