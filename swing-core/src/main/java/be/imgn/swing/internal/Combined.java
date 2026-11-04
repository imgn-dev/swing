package be.imgn.swing.internal;

import be.imgn.swing.Observable;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * An observable combined from two others.
 *
 * @param <A> the type of the first value
 * @param <B> the type of the second value
 * @param <R> the type of the value
 */
public final class Combined<A, B, R> extends Derived<R> {

    private final Source<A> a;
    private final Source<B> b;
    private final BiFunction<? super A, ? super B, ? extends R> combiner;

    /**
     * Creates the combined observable.
     *
     * @param a the first source
     * @param b the second source
     * @param combiner the function
     */
    public Combined(Observable<A> a, Observable<B> b, BiFunction<? super A, ? super B, ? extends R> combiner) {
        this.a = new Source<>(Objects.requireNonNull(a, "a"));
        this.b = new Source<>(Objects.requireNonNull(b, "b"));
        this.combiner = Objects.requireNonNull(combiner, "combiner");
    }

    @Override
    List<Source<?>> sources() {
        return List.of(a, b);
    }

    @Override
    R compute(boolean kept) {
        return combiner.apply(a.value(kept), b.value(kept));
    }
}
