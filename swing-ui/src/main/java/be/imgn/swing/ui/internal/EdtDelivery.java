package be.imgn.swing.ui.internal;

import be.imgn.swing.Observable;
import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import java.awt.EventQueue;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Subscribes to an observable for the user interface. The listener, on the setter's thread, touches no component: it
 * sets a pending flag and, if it was not set, queues one update. The updates of every subscription share one queue,
 * which one {@link EventQueue#invokeLater(Runnable)} drains on the EDT, even when the change happened on the EDT: the
 * framework's updates first, then the views', each group in the order its subscriptions were made. An update queued
 * while the queue drains runs in the same drain. An update clears the flag and reads the latest value; it does nothing
 * once the scope is closed.
 */
public final class EdtDelivery {

    /** Whose update it is, which decides when it runs among those pending. */
    public enum Order {
        /** The framework's own, such as the enabled and selected states of commands, which run first. */
        FRAMEWORK,
        /** A view's, through {@code Ui.bind}, which runs once the framework's are done. */
        VIEW
    }

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Object LOCK = new Object();
    private static final TreeSet<Update> PENDING = new TreeSet<>(
            Comparator.comparing((Update update) -> update.order).thenComparingLong(update -> update.sequence));
    private static boolean drainPosted;

    private EdtDelivery() {}

    /**
     * Subscribes a target to an observable, with updates delivered on the EDT.
     *
     * @param scope the scope the subscription belongs to
     * @param source the observable
     * @param target the target, called on the EDT with the latest value
     * @param order whose update it is
     * @param <T> the type of the value
     * @return the subscription
     */
    public static <T> Subscription subscribe(
            Scope scope, Observable<T> source, Consumer<? super T> target, Order order) {
        Update update = new Update(order, SEQUENCE.getAndIncrement(), () -> {
            if (!scope.isClosed()) {
                target.accept(source.get());
            }
        });
        return source.subscribe(scope, ignored -> update.queue());
    }

    // Runs the pending updates in order, until none is left. Should an update throw, the remaining ones run in a later
    // drain, and the failure goes on to the EDT's handler.
    private static void drain() {
        try {
            for (Update next = next(); next != null; next = next()) {
                next.pending.set(false);
                next.action.run();
            }
        } finally {
            synchronized (LOCK) {
                if (PENDING.isEmpty()) {
                    drainPosted = false;
                } else {
                    EventQueue.invokeLater(EdtDelivery::drain);
                }
            }
        }
    }

    private static Update next() {
        synchronized (LOCK) {
            return PENDING.pollFirst();
        }
    }

    private static final class Update {
        private final Order order;
        private final long sequence;
        private final Runnable action;
        private final AtomicBoolean pending = new AtomicBoolean();

        Update(Order order, long sequence, Runnable action) {
            this.order = order;
            this.sequence = sequence;
            this.action = action;
        }

        void queue() {
            if (pending.compareAndSet(false, true)) {
                synchronized (LOCK) {
                    PENDING.add(this);
                    if (!drainPosted) {
                        drainPosted = true;
                        EventQueue.invokeLater(EdtDelivery::drain);
                    }
                }
            }
        }
    }
}
