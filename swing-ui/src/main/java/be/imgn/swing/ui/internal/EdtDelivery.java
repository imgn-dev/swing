package be.imgn.swing.ui.internal;

import be.imgn.swing.Observable;
import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Subscribes to an observable for the user interface. The listener, on the setter's thread, touches no component: it
 * sets a pending flag and, if it was not set, posts one update with {@link EventQueue#invokeLater(Runnable)}, even on
 * the EDT. The update clears the flag and reads the latest value; it does nothing once the scope is closed.
 */
public final class EdtDelivery {

    private EdtDelivery() {}

    /**
     * Subscribes a target to an observable, with updates delivered on the EDT.
     *
     * @param scope the scope the subscription belongs to
     * @param source the observable
     * @param target the target, called on the EDT with the latest value
     * @param <T> the type of the value
     * @return the subscription
     */
    public static <T> Subscription subscribe(Scope scope, Observable<T> source, Consumer<? super T> target) {
        AtomicBoolean pending = new AtomicBoolean();
        return source.subscribe(scope, ignored -> {
            if (pending.compareAndSet(false, true)) {
                EventQueue.invokeLater(() -> {
                    pending.set(false);
                    if (!scope.isClosed()) {
                        target.accept(source.get());
                    }
                });
            }
        });
    }
}
