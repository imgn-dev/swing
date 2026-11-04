package be.imgn.swing.ui.internal;

import be.imgn.gaia.core.time.Ticker;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.Subscription;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.internal.Guarded;
import be.imgn.swing.internal.Settling;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

/**
 * The periodic actions of the views, which run on the EDT once their period has elapsed, measured with the
 * {@link Ticker}. The running application polls them with a {@link Timer}; the test harness, whose executor settles,
 * polls them while it settles instead. Used on the EDT.
 */
@Singleton
public final class PeriodicActions {

    private static final int POLL_MILLIS = 50;

    private final Ticker ticker;
    private final FailureHandler failures;
    private final boolean polledBySettling;
    private final List<Periodic> actions = new ArrayList<>();
    private final Timer timer = new Timer(POLL_MILLIS, event -> runDue());

    @Inject
    PeriodicActions(Ticker ticker, FailureHandler failures, UiExecutor executor) {
        this.ticker = ticker;
        this.failures = failures;
        this.polledBySettling = executor instanceof Settling;
    }

    /**
     * Adds an action, which first runs one period after the call.
     *
     * @param period the period, positive
     * @param action the action
     * @return removes the action
     */
    public Subscription add(Duration period, Runnable action) {
        Periodic periodic = new Periodic(period.toNanos(), action, ticker.read());
        actions.add(periodic);
        if (!polledBySettling && !timer.isRunning()) {
            timer.start();
        }
        return () -> remove(periodic);
    }

    private void remove(Periodic periodic) {
        actions.remove(periodic);
        if (actions.isEmpty()) {
            timer.stop();
        }
    }

    /**
     * Runs the actions whose period has elapsed, once each: a late run doesn't make up for the runs it missed, and the
     * next one comes one period after it. An exception thrown by an action goes to the {@link FailureHandler}, and the
     * action keeps its schedule; an {@link Error} propagates.
     */
    public void runDue() {
        for (Periodic periodic : List.copyOf(actions)) {
            long now = ticker.read();
            if (actions.contains(periodic) && now - periodic.last >= periodic.period) {
                periodic.last = now;
                Throwable thrown = Guarded.runCatchingExceptions(periodic.action);
                if (thrown != null) {
                    failures.failed(thrown);
                }
            }
        }
    }

    // The timer, which tests check without waiting for its delay.
    Timer timer() {
        return timer;
    }

    private static final class Periodic {
        private final long period;
        private final Runnable action;
        private long last;

        Periodic(long period, Runnable action, long last) {
            this.period = period;
            this.action = action;
            this.last = last;
        }
    }
}
