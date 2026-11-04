package be.imgn.swing.internal;

import be.imgn.swing.Observable;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * An observable mapped from another.
 *
 * @param <T> the type of the source's value
 * @param <R> the type of the value
 */
public final class Mapped<T, R> extends Derived<R> {

    private final Source<T> source;
    private final Function<? super T, ? extends R> mapper;

    /**
     * Creates the mapped observable.
     *
     * @param source the source
     * @param mapper the function
     */
    public Mapped(Observable<T> source, Function<? super T, ? extends R> mapper) {
        this.source = new Source<>(Objects.requireNonNull(source, "source"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    List<Source<?>> sources() {
        return List.of(source);
    }

    @Override
    R compute(boolean kept) {
        return mapper.apply(source.value(kept));
    }
}
