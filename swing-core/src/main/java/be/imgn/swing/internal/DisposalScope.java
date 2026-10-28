package be.imgn.swing.internal;

import be.imgn.swing.Scope;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The disposal list of a scope: its resources, released in reverse order of registration when it closes. */
public final class DisposalScope implements Scope, AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(DisposalScope.class.getName());

    private final @Nullable DisposalScope parent;
    private final String name;
    private final Set<AutoCloseable> resources = new LinkedHashSet<>();
    private boolean closed;

    private DisposalScope(@Nullable DisposalScope parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    /**
     * Returns a new scope with no parent.
     *
     * @param name the name of the scope, for messages
     * @return the scope
     */
    public static DisposalScope root(String name) {
        return new DisposalScope(null, name);
    }

    /**
     * Returns a new scope nested in this one, closed when this one closes, before this one's resources.
     *
     * @param childName the name of the child, for messages
     * @return the child
     * @throws IllegalStateException if this scope is closed
     */
    public DisposalScope child(String childName) {
        DisposalScope child = new DisposalScope(this, childName);
        Runnable unregister = register(child);
        child.onClose(unregister::run);
        return child;
    }

    @Override
    public Optional<Scope> parent() {
        return Optional.ofNullable(parent);
    }

    /**
     * Returns the depth of this scope: 0 with no parent.
     *
     * @return the depth
     */
    public int depth() {
        return parent == null ? 0 : parent.depth() + 1;
    }

    @Override
    public void onClose(AutoCloseable resource) {
        register(resource);
    }

    /**
     * Registers a resource to close when this scope closes, and returns what withdraws it.
     *
     * @param resource the resource
     * @return withdraws the resource, which this scope then no longer closes
     * @throws IllegalStateException if this scope is closed
     */
    public synchronized Runnable register(AutoCloseable resource) {
        if (closed) {
            throw new IllegalStateException("The scope " + name + " is closed.");
        }
        resources.add(resource);
        return () -> withdraw(resource);
    }

    private synchronized void withdraw(AutoCloseable resource) {
        resources.remove(resource);
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    /** Closes this scope: closes its resources in reverse order of registration, and logs their failures. */
    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void close() {
        List<AutoCloseable> toClose;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            toClose = new ArrayList<>(resources);
            resources.clear();
        }
        for (AutoCloseable resource : toClose.reversed()) {
            try {
                resource.close();
            } catch (Exception e) {
                LOGGER.log(Level.ERROR, "Could not release a resource of the scope " + name, e);
            }
        }
    }

    @Override
    public String toString() {
        return "Scope[" + name + "]";
    }
}
