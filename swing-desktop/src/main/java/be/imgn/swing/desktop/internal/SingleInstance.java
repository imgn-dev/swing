package be.imgn.swing.desktop.internal;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Key;
import be.imgn.inject.Singleton;
import be.imgn.swing.Arguments;
import be.imgn.swing.Exit;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.desktop.SecondInstanceHandler;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Makes an application that has a {@link SecondInstanceHandler} bean single-instance. A startup step of order 10, on
 * the main thread, takes the lock in the state directory: the first instance keeps it until the shutdown, and hands
 * each second launch's arguments to the handler, on the EDT, once the application is ready, in the order they arrived;
 * a second instance forwards its arguments, then exits.
 */
@Singleton
public final class SingleInstance {

    /** The order of the startup step that takes the lock: before the resources. */
    static final int ORDER = 10;

    private final boolean enabled;
    private final Supplier<SecondInstanceHandler> handler;
    private final InstanceLock lock;
    private final Supplier<Path> directory;
    private final List<String> arguments;
    private final Exit exit;
    private final UiExecutor executor;
    // Guarded by this: the launches received before the application is ready.
    private final List<List<String>> pending = new ArrayList<>();
    private boolean ready;
    private @Nullable Closeable held;

    @Inject
    SingleInstance(
            Injector root,
            InstanceLock lock,
            ApplicationDirectories directories,
            Arguments arguments,
            Exit exit,
            UiExecutor executor) {
        this(
                root.getBeanDefinition(Key.of(SecondInstanceHandler.class)).isPresent(),
                () -> root.getInstance(SecondInstanceHandler.class),
                lock,
                directories::state,
                arguments.values(),
                exit,
                executor);
    }

    /**
     * Creates the single instance of an application.
     *
     * @param enabled whether the application has a {@code SecondInstanceHandler}
     * @param handler supplies the handler
     * @param lock the lock
     * @param directory supplies the directory of the lock, the state directory
     * @param arguments the arguments of this launch
     * @param exit ends a second instance
     * @param executor runs the deliveries on the EDT
     */
    SingleInstance(
            boolean enabled,
            Supplier<SecondInstanceHandler> handler,
            InstanceLock lock,
            Supplier<Path> directory,
            List<String> arguments,
            Exit exit,
            UiExecutor executor) {
        this.enabled = enabled;
        this.handler = handler;
        this.lock = lock;
        this.directory = directory;
        this.arguments = arguments;
        this.exit = exit;
        this.executor = executor;
    }

    /** Takes the lock, or forwards the arguments then exits; the startup step of order 10 does, on the main thread. */
    void start() {
        if (!enabled) {
            return;
        }
        Path state = directory.get();
        try {
            var first = lock.acquire(state, arguments, this::received);
            if (first.isEmpty()) {
                exit.exit(0);
                return;
            }
            synchronized (this) {
                held = first.get();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not take the instance lock in " + state, e);
        }
    }

    private void received(List<String> launch) {
        synchronized (this) {
            if (!ready) {
                pending.add(launch);
                return;
            }
        }
        executor.execute(() -> deliver(launch));
    }

    /** Hands the launches received so far to the handler, and the next ones as they arrive; on the EDT. */
    void ready() {
        List<List<String>> launches;
        synchronized (this) {
            ready = true;
            launches = List.copyOf(pending);
            pending.clear();
        }
        launches.forEach(this::deliver);
    }

    private void deliver(List<String> launch) {
        handler.get().launched(new Arguments(launch));
    }

    /**
     * Closes the server and releases the lock; the shutdown step of order 300 does.
     *
     * @throws IOException if closing fails
     */
    void close() throws IOException {
        @Nullable Closeable closing;
        synchronized (this) {
            closing = held;
            held = null;
        }
        if (closing != null) {
            closing.close();
        }
    }
}
