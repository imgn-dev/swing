package be.imgn.swing;

import java.util.Optional;

/** The disposal list of an application, window or view scope, for what is not a bean. */
public interface Scope {

    /**
     * Returns the scope this one is nested in, if any.
     *
     * @return the scope this one is nested in, if any
     */
    Optional<Scope> parent();

    /**
     * Registers a resource to close when this scope closes. Resources are closed in reverse order of registration.
     *
     * @param resource the resource to close
     */
    void onClose(AutoCloseable resource);

    /**
     * Returns whether this scope is closed.
     *
     * @return whether this scope is closed
     */
    boolean isClosed();
}
