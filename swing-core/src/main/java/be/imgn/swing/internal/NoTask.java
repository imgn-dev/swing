package be.imgn.swing.internal;

import be.imgn.swing.Task;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** The task {@link Task#none()} returns: running it does nothing, so its hooks never run. */
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
}
