package be.imgn.swing.internal;

import be.imgn.swing.Observable;
import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * An observable computed from sources, subscribed to them only while it has subscribers.
 *
 * @param <R> the type of the value
 */
abstract class Derived<R> implements Observable<R> {

    private final Listeners<R> listeners = new Listeners<>(this::connect, this::disconnect);
    private @Nullable DisposalScope connection;
    private @Nullable R last;

    /**
     * Returns the sources.
     *
     * @return the observables this one is computed from
     */
    abstract List<Observable<?>> sources();

    /**
     * Computes the value from the sources' current values.
     *
     * @return the value
     */
    abstract R compute();

    @Override
    public final synchronized R get() {
        R current = last;
        return connection != null && current != null ? current : compute();
    }

    @Override
    public final Subscription subscribe(Scope scope, Consumer<? super R> listener) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(listener, "listener");
        return listeners.subscribe(scope, listener);
    }

    private synchronized void connect() {
        DisposalScope scope = DisposalScope.root("derived observable");
        connection = scope;
        last = compute();
        for (Observable<?> source : sources()) {
            source.subscribe(scope, ignored -> changed());
        }
    }

    private synchronized void disconnect() {
        DisposalScope scope = connection;
        connection = null;
        last = null;
        if (scope != null) {
            scope.close();
        }
    }

    private void changed() {
        R value;
        synchronized (this) {
            if (connection == null) {
                return;
            }
            value = compute();
            if (Objects.equals(value, last)) {
                return;
            }
            last = value;
        }
        listeners.fire(value);
    }
}
