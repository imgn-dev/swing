package be.imgn.swing.internal;

import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ExitVeto;

/** Registers the exit vetoes among the instances of the window and view scopes, in their scope. */
@Singleton
@IntoSet(ConstructionListener.class)
public final class VetoRegistrar implements ConstructionListener {

    private final Hosts hosts;
    private final ExitVetoes vetoes;

    @Inject
    VetoRegistrar(Hosts hosts, ExitVetoes vetoes) {
        this.hosts = hosts;
        this.vetoes = vetoes;
    }

    @Override
    public void constructed(Object instance, Injector injector) {
        if (instance instanceof ExitVeto veto && ScopedInstances.isScoped(instance, injector)) {
            hosts.scopeOf(injector).ifPresent(scope -> vetoes.register(scope, veto));
        }
    }
}
