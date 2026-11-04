package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Subscription;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.internal.Settling;
import java.awt.EventQueue;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

/**
 * The schedule of periodic actions, measured with a ticker the test moves, and polled by the test, or by the timer of
 * the running application.
 */
class PeriodicActionsTest {

    private long now;
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final PeriodicActions actions = new PeriodicActions(() -> now, failures::add, new PolledExecutor());
    private final AtomicInteger runs = new AtomicInteger();

    @Test
    void runsAnActionOnePeriodAfterItWasAdded() {
        actions.add(Duration.ofSeconds(1), runs::incrementAndGet);

        advance(Duration.ofMillis(999));
        actions.runDue();
        assertThat(runs).hasValue(0);

        advance(Duration.ofMillis(1));
        actions.runDue();
        actions.runDue();
        assertThat(runs).hasValue(1);
    }

    @Test
    void aLateRunDoesNotMakeUpForTheRunsItMissed() {
        actions.add(Duration.ofSeconds(1), runs::incrementAndGet);

        advance(Duration.ofMillis(3_500));
        actions.runDue();
        assertThat(runs).hasValue(1);

        advance(Duration.ofMillis(900));
        actions.runDue();
        assertThat(runs).hasValue(1);

        advance(Duration.ofMillis(100));
        actions.runDue();
        assertThat(runs).hasValue(2);
    }

    @Test
    void anActionThatThrowsGoesToTheFailureHandlerAndKeepsItsSchedule() {
        IllegalStateException failure = new IllegalStateException("expected");
        actions.add(Duration.ofSeconds(1), () -> {
            runs.incrementAndGet();
            throw failure;
        });

        advance(Duration.ofSeconds(1));
        actions.runDue();
        advance(Duration.ofSeconds(1));
        actions.runDue();

        assertThat(runs).hasValue(2);
        assertThat(failures).containsExactly(failure, failure);
    }

    @Test
    void anErrorThrownByAnActionPropagates() {
        InternalError failure = new InternalError("A broken invariant");
        actions.add(Duration.ofSeconds(1), () -> {
            throw failure;
        });

        advance(Duration.ofSeconds(1));

        assertThatThrownBy(actions::runDue).isSameAs(failure);
        assertThat(failures).isEmpty();
    }

    @Test
    void aClosedSubscriptionStopsTheAction() {
        Subscription subscription = actions.add(Duration.ofSeconds(1), runs::incrementAndGet);

        subscription.close();
        advance(Duration.ofSeconds(2));
        actions.runDue();

        assertThat(runs).hasValue(0);
    }

    @Test
    void theTestHarnessStartsNoTimer() {
        actions.add(Duration.ofSeconds(1), runs::incrementAndGet);

        assertThat(actions.timer().isRunning()).isFalse();
    }

    @Test
    void theRunningApplicationPollsTheActionsWithATimerEvery50Milliseconds() throws Exception {
        AtomicLong ticks = new AtomicLong();
        PeriodicActions polled = new PeriodicActions(ticks::get, failures::add, new EdtUiExecutor());
        CountDownLatch ran = new CountDownLatch(1);
        AtomicBoolean onEdt = new AtomicBoolean();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        Timer timer = polled.timer();
        assertThat(timer.getDelay()).isEqualTo(50);
        assertThat(timer.isRepeats()).isTrue();

        EventQueue.invokeAndWait(() -> subscription.set(polled.add(Duration.ofSeconds(1), () -> {
            onEdt.set(EventQueue.isDispatchThread());
            ran.countDown();
        })));
        assertThat(timer.isRunning()).isTrue();
        ticks.addAndGet(Duration.ofSeconds(1).toNanos());

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(onEdt).isTrue();
        EventQueue.invokeAndWait(
                () -> Objects.requireNonNull(subscription.get()).close());
        assertThat(timer.isRunning()).isFalse();
        assertThat(failures).isEmpty();
    }

    private void advance(Duration duration) {
        now += duration.toNanos();
    }

    // An executor that settles, as the test harness's does: the actions start no timer, and the test polls them.
    private static final class PolledExecutor implements UiExecutor, Settling {
        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public boolean isUiThread() {
            return true;
        }

        @Override
        public void settle() {}
    }
}
