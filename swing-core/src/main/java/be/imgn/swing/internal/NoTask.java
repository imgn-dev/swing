package be.imgn.swing.internal;

import be.imgn.swing.Task;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The task {@link Task#none()} returns: running it does nothing, so its hooks never run. It is shared, so no chain owns
 * it, and chaining it runs nothing.
 */
public final class NoTask implements Task<@Nullable Object> {

    /** The only instance. */
    public static final NoTask INSTANCE = new NoTask();

    private NoTask() {}

    @Override
    public Task<@Nullable Object> onSuccess(Consumer<? super @Nullable Object> handler) {
        return this;
    }

    @Override
    public Task<@Nullable Object> onFailure(Consumer<? super Throwable> handler) {
        return this;
    }

    @Override
    public Task<@Nullable Object> onCancel(Runnable handler) {
        return this;
    }

    @Override
    public Task<@Nullable Object> onEnd(Runnable handler) {
        Objects.requireNonNull(handler, "handler");
        return this;
    }

    @Override
    public <R extends @Nullable Object> Task<R> then(Function<? super @Nullable Object, ? extends Task<R>> next) {
        Objects.requireNonNull(next, "next");
        return Task.none();
    }
}
