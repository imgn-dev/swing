package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.Scope;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.Hosts;
import be.imgn.swing.internal.ResourceTables;
import be.imgn.swing.ui.internal.DialogControl;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.spi.ComponentInjector;
import be.imgn.swing.ui.spi.ComponentResources;
import be.imgn.toml.TomlTable;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Opens windows and builds views in them, with their child injectors and scopes. */
@Singleton
final class ViewBuilder {

    private final UiServices services;
    private final Hosts hosts;

    @Inject
    ViewBuilder(UiServices services, Hosts hosts) {
        this.services = services;
        this.hosts = hosts;
    }

    /**
     * Opens a window: creates its scope and the child injector hosting the window scope, binding the window and the
     * scope.
     */
    WindowHost openWindow(
            Window window,
            Injector parent,
            DisposalScope parentScope,
            @Nullable ViewHost opener,
            String stateKey,
            String name) {
        DisposalScope scope = parentScope.child(name);
        Injector injector = parent.scopedChildBuilder(WindowScoped.class)
                .bind(Window.class, window)
                .bind(Scope.class, scope)
                .build();
        // Registered before anything is resolved through the host, so that its vetoes are reported in its scope.
        hosts.register(injector, scope);
        WindowHost host = new WindowHost(window, injector, scope, opener, stateKey, services.mode());
        services.registry().addWindow(host);
        return host;
    }

    /**
     * Builds a view in a window: creates its scope and the child injector hosting the view scope, registers the view
     * class's overrides, resolves the view, applies its component resources, places it, applies its tab titles, and
     * ends its build, which attaches its commands.
     */
    ViewHost buildView(
            WindowHost window,
            Optional<? extends Class<?>> type,
            Optional<? extends Enum<?>> variant,
            Function<Injector, ?> resolve,
            @Nullable DialogControl dialog,
            Consumer<ViewHost> place) {
        DisposalScope scope = window.scope().child("view");
        Injector injector = window.injector()
                .scopedChildBuilder(ViewScoped.class)
                .bind(Scope.class, scope)
                .build();
        hosts.register(injector, scope);
        ViewHost view = new ViewHost(type.isPresent() ? type.get() : Object.class, injector, scope, window, dialog);
        services.registry().addView(view);
        type.ifPresent(known -> overrides(known, injector));
        Object instance = resolve.apply(injector);
        view.view(instance);
        if (type.isEmpty()) {
            // The class of a view created by a factory is known only now: register its overrides, among the
            // commands it already bound as well, before its resources and components are built.
            view.type(instance.getClass());
            overrides(view.type(), injector);
            injector.getInstance(CommandRegistry.class).viewTypeKnown();
        }
        ComponentResources resources = resources(view.type(), variant);
        inject(view.type(), instance, resources, false);
        place.accept(view);
        inject(view.type(), instance, resources, true);
        view.built();
        return view;
    }

    private <V> void overrides(Class<V> type, Injector injector) {
        Optional<ComponentInjector<V>> generated = services.supports().injectorOf(type);
        if (generated.isPresent()) {
            generated.get().registerOverrides(injector.getInstance(CommandRegistry.class));
        }
    }

    private ComponentResources resources(Class<?> type, Optional<? extends Enum<?>> variant) {
        return new ComponentResources(
                table(type, variant), path -> services.icons().load(type, path), !services.isMacOS());
    }

    /**
     * Returns the resources of a view class, with a variant's table, {@code [variants.<constant in kebab case>]},
     * merged over its base table.
     *
     * @throws IllegalStateException if the resources have no table for the variant
     */
    TomlTable table(Class<?> type, Optional<? extends Enum<?>> variant) {
        TomlTable base = services.resources().table(type);
        if (variant.isEmpty()) {
            return base;
        }
        String name = variant.get().name().toLowerCase(Locale.ROOT).replace('_', '-');
        if (base.entries().get("variants") instanceof TomlTable variants
                && variants.entries().get(name) instanceof TomlTable table) {
            return ResourceTables.merge(base, table);
        }
        throw new IllegalStateException(
                "The resources of " + type.getName() + " have no table [variants." + name + "] for " + variant.get());
    }

    private <V> void inject(Class<V> type, Object instance, ComponentResources resources, boolean tabTitles) {
        Optional<ComponentInjector<V>> generated = services.supports().injectorOf(type);
        if (generated.isEmpty()) {
            return;
        }
        V view = type.cast(instance);
        if (tabTitles) {
            generated.get().injectTabTitles(view, resources);
        } else {
            generated.get().inject(view, resources);
        }
    }

    /** Closes a window: each view's injector then scope, the last view first, then the window's own. */
    void close(WindowHost window) {
        window.close();
    }

    UiServices services() {
        return services;
    }
}
