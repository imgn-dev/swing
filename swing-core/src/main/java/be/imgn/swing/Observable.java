package be.imgn.swing;

import be.imgn.swing.internal.Combined;
import be.imgn.swing.internal.Mapped;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A value that tells its listeners when it changes.
 *
 * <p>Listeners are called synchronously, on the thread that changed the value, only when the value changes
 * ({@link java.util.Objects#equals(Object, Object)}). Core has no EDT: the UI layer moves its work to the EDT itself.
 *
 * <p>{@link #map(Function)} and {@link #combine(Observable, Observable, BiFunction)} are lazy: a mapped observable
 * subscribes to its sources only while it has subscribers. While it has some, it keeps its last value and notifies them
 * only when a source change gives a new value; its {@link #get()} returns that value. Without subscribers,
 * {@link #get()} computes from the sources' current values. Mapping functions may therefore run more than once, on any
 * thread: they must be cheap and free of side effects.
 *
 * @param <T> the type of the value
 */
public interface Observable<T> {

    /**
     * Returns the current value.
     *
     * @return the value
     */
    T get();

    /**
     * Subscribes a listener, called with each new value, until the scope closes or the subscription is closed.
     *
     * @param scope the scope the subscription belongs to
     * @param listener the listener
     * @return the subscription
     * @throws IllegalStateException if the scope is closed
     */
    Subscription subscribe(Scope scope, Consumer<? super T> listener);

    /**
     * Returns an observable of this one's value mapped by a function.
     *
     * @param mapper the function, cheap and free of side effects
     * @param <R> the type of the mapped value
     * @return the mapped observable
     */
    default <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        return new Mapped<>(this, mapper);
    }

    /**
     * Returns an observable of two observables' values combined by a function.
     *
     * @param a the first observable
     * @param b the second observable
     * @param combiner the function, cheap and free of side effects
     * @param <A> the type of the first value
     * @param <B> the type of the second value
     * @param <R> the type of the combined value
     * @return the combined observable
     */
    static <A, B, R> Observable<R> combine(
            Observable<A> a, Observable<B> b, BiFunction<? super A, ? super B, ? extends R> combiner) {
        return new Combined<>(a, b, combiner);
    }
}
