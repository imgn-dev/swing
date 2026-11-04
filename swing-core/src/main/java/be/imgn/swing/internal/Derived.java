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
 * <p>While connected, it keeps the last value of each source, and a change of one source reads only that source again.
 * It never reads a source while it holds its own monitor, except the one whose change it is handling, on the thread
 * that changed it: an observable list holds its lock while it calls its listeners, so reading another source there
 * would take locks in an order that another thread can take the other way round.
 *
 * @param <R> the type of the value
 */
public abstract sealed class Derived<R> implements Observable<R> permits Mapped, Combined {

    private final Listeners<R> listeners = new Listeners<>(this::connect, this::disconnect);
    private @Nullable DisposalScope connection;
    private @Nullable R last;

    /**
     * Returns the sources.
     *
     * @return the sources this observable is computed from
     */
    abstract List<Source<?>> sources();

    /**
     * Computes the value from the sources' kept values, or from their current values.
     *
     * @param kept whether to compute from the values the sources keep, rather than from their current values
     * @return the value
     */
    abstract R compute(boolean kept);

    @Override
    public final R get() {
        synchronized (this) {
            R current = last;
            if (connection != null && current != null) {
                return current;
            }
        }
        return compute(false);
    }

    @Override
    public final Subscription subscribe(Scope scope, Consumer<? super R> listener) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(listener, "listener");
        return listeners.subscribe(scope, listener);
    }

    // Called by the listeners, which serialize it with disconnect(). It subscribes to each source before reading it, so
    // that no change falls between them; the changes it receives meanwhile only keep their source's value, and it
    // computes once every source is read.
    private void connect() {
        DisposalScope scope = DisposalScope.root("derived observable");
        synchronized (this) {
            connection = scope;
        }
        for (Source<?> source : sources()) {
            source.observable().subscribe(scope, ignored -> changed(source));
            keepCurrent(source);
        }
        synchronized (this) {
            last = compute(true);
        }
    }

    // Reads a source without the monitor, and keeps its value unless a change of the source was kept in the meantime:
    // that change's value is at least as recent as the one read here, or another change follows it.
    private <T> void keepCurrent(Source<T> source) {
        long version;
        synchronized (this) {
            version = source.version;
        }
        T value = source.observable().get();
        synchronized (this) {
            if (source.version == version) {
                source.kept = value;
            }
        }
    }

    private void disconnect() {
        DisposalScope scope;
        synchronized (this) {
            scope = connection;
            connection = null;
            last = null;
        }
        if (scope != null) {
            scope.close();
        }
    }

    private void changed(Source<?> source) {
        @Nullable R value;
        synchronized (this) {
            if (connection == null) {
                return;
            }
            source.read();
            R previous = last;
            // Without a last value, it is still connecting: connect() computes once every source is read.
            value = previous == null ? null : compute(true);
            if (value == null || Objects.equals(value, previous)) {
                return;
            }
            last = value;
        }
        listeners.fire(value);
    }

    /**
     * A source, with the value it had when last read.
     *
     * @param <T> the type of the source's value
     */
    static final class Source<T> {

        private final Observable<T> observable;
        // Guarded by the derived observable's monitor; the version counts the changes kept.
        private @Nullable T kept;
        private long version;

        Source(Observable<T> observable) {
            this.observable = Objects.requireNonNull(observable);
        }

        Observable<T> observable() {
            return observable;
        }

        /**
         * Returns the value kept, or the current value.
         *
         * @param fromKept whether to return the kept value
         * @return the value
         */
        T value(boolean fromKept) {
            return fromKept ? Objects.requireNonNull(kept) : observable.get();
        }

        private void read() {
            kept = observable.get();
            version++;
        }
    }
}
