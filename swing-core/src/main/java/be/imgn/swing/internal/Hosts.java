package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The child injectors that host a window or a view scope, with the disposal list of their scope. */
@Singleton
public final class Hosts {

    private final Map<Injector, DisposalScope> scopes = new ConcurrentHashMap<>();

    @Inject
    Hosts() {}

    /**
     * Registers a host, until its scope closes.
     *
     * @param host the child injector
     * @param scope the disposal list of its scope
     */
    public void register(Injector host, DisposalScope scope) {
        scopes.put(host, scope);
        scope.onClose(() -> scopes.remove(host));
    }

    /**
     * Returns the scope a host is registered with.
     *
     * @param host an injector
     * @return its scope, or empty if it is not a registered host
     */
    public Optional<DisposalScope> scopeOf(Injector host) {
        return Optional.ofNullable(scopes.get(host));
    }
}
