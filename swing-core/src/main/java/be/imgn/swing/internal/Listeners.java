package be.imgn.swing.internal;

import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

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
        Notifications.notifying(() -> {
            for (Consumer<? super T> listener : listeners) {
                listener.accept(value);
            }
        });
    }

    // Calls every listener, even after one threw, so that a listener subscribed after a failing one, such as the UI's
    // copy of an observable list, still sees the change; then throws the first failure, with the later ones suppressed.
    void fireAll(T value) {
        Notifications.notifying(() -> {
            @Nullable Throwable first = null;
            for (Consumer<? super T> listener : listeners) {
                @Nullable Throwable thrown = Guarded.run(() -> listener.accept(value));
                if (thrown == null) {
                    continue;
                }
                if (first == null) {
                    first = thrown;
                } else if (!thrown.equals(first)) {
                    first.addSuppressed(thrown);
                }
            }
            if (first instanceof Error error) {
                throw error;
            }
            if (first instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (first != null) {
                throw new UndeclaredThrowableException(first);
            }
        });
    }
}
