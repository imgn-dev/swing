package be.imgn.swing.desktop.internal;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Makes the application single-instance: the first instance holds a lock and receives the second launches. */
public interface InstanceLock {

    /**
     * Becomes the first instance, or forwards the arguments to the instance that is.
     *
     * @param directory the directory of the lock, per user and per application
     * @param arguments the arguments of this launch
     * @param receiver receives the arguments of each second launch, on a thread of its own, in the order they arrive
     * @return what closes the lock, when this process is the first instance; empty when the arguments were forwarded
     * @throws IOException if the lock or the request fails
     * @throws IllegalStateException if another instance holds the lock and doesn't answer
     */
    Optional<Closeable> acquire(Path directory, List<String> arguments, Consumer<List<String>> receiver)
            throws IOException;
}
