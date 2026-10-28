package be.imgn.swing.internal;

import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The listeners of an observable, each subscribed in a scope.
 *
 * @param <T> the type of the value
 */
final class Listeners<T> {

    private final List<Consumer<? super T>> listeners = new CopyOnWriteArrayList<>();
    private final Runnable onFirst;
    private final Runnable onLast;

    Listeners(Runnable onFirst, Runnable onLast) {
        this.onFirst = onFirst;
        this.onLast = onLast;
    }

    synchronized Subscription subscribe(Scope scope, Consumer<? super T> listener) {
        if (scope.isClosed()) {
            throw new IllegalStateException("Cannot subscribe in " + scope + ": it is closed.");
        }
        // A distinct object per subscription, so that one listener subscribed twice is two subscriptions.
        Consumer<? super T> entry = value -> listener.accept(value);
        boolean first = listeners.isEmpty();
        listeners.add(entry);
        if (first) {
            onFirst.run();
        }
        Subscription subscription = new Subscription() {
            private boolean closed;

            @Override
            public void close() {
                synchronized (Listeners.this) {
                    if (closed) {
                        return;
                    }
                    closed = true;
                    listeners.remove(entry);
                    if (listeners.isEmpty()) {
                        onLast.run();
                    }
                }
            }
        };
        if (scope instanceof DisposalScope disposal) {
            Runnable withdraw = disposal.register(subscription);
            return () -> {
                withdraw.run();
                subscription.close();
            };
        }
        scope.onClose(subscription);
        return subscription;
    }

    boolean isEmpty() {
        return listeners.isEmpty();
    }

    void fire(T value) {
        for (Consumer<? super T> listener : listeners) {
            listener.accept(value);
        }
    }
}
