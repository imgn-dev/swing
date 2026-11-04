package be.imgn.swing.internal;

import be.imgn.swing.Task;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A chain: a first task, which it owns, then the task its {@code next} function returns for the first one's result.
 *
 * @param <T> the type of the first task's result
 * @param <R> the type of the chain's result
 */
public final class ChainTask<T extends @Nullable Object, R extends @Nullable Object> extends AbstractTask<R> {

    private final AbstractTask<T> first;
    private final Function<? super T, ? extends Task<R>> next;

    ChainTask(AbstractTask<T> first, Function<? super T, ? extends Task<R>> next) {
        this.first = first;
        this.next = next;
    }

    AbstractTask<T> first() {
        return first;
    }

    Function<? super T, ? extends Task<R>> next() {
        return next;
    }
}
