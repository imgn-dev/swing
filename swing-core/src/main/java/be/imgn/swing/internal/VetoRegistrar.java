package be.imgn.swing.internal;

import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ExitVeto;

/**
 * Registers the exit vetoes among the instances of the window and view scopes, in their scope. The singletons, which
 * the root injector reports, are asked as {@code @IntoSet(ExitVeto.class)} contributions instead.
 */
@Singleton
@IntoSet(ConstructionListener.class)
public final class VetoRegistrar implements ConstructionListener {

    private final Injector root;
    private final Hosts hosts;
    private final ExitVetoes vetoes;

    @Inject
    VetoRegistrar(Injector root, Hosts hosts, ExitVetoes vetoes) {
        this.root = root;
        this.hosts = hosts;
        this.vetoes = vetoes;
    }

    @Override
    public void constructed(Object instance, Injector injector) {
        if (!(instance instanceof ExitVeto veto)
                || injector.equals(root)
                || !ScopedInstances.isScoped(instance, injector)) {
            return;
        }
        // A scoped veto reported with an injector the framework did not open as a host would be lost: fail instead.
        DisposalScope scope = hosts.scopeOf(injector)
                .orElseThrow(() -> new IllegalStateException("The exit veto " + instance + " of class "
                        + instance.getClass().getName() + " was built by an injector that hosts no window or view"));
        vetoes.register(scope, veto);
    }
}
