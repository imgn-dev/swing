package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.Scope;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.Hosts;
import be.imgn.swing.internal.ResourceTables;
import be.imgn.swing.internal.SubscriberRegistrar;
import be.imgn.swing.ui.internal.AccessibilityCheck;
import be.imgn.swing.ui.internal.ComponentInspector;
import be.imgn.swing.ui.internal.DialogControl;
import be.imgn.swing.ui.internal.InspectorOverlay;
import be.imgn.swing.ui.internal.RefreshPhase;
import be.imgn.swing.ui.internal.UiMode;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowStateSupport;
import be.imgn.swing.ui.spi.ComponentInjector;
import be.imgn.swing.ui.spi.ComponentResources;
import be.imgn.toml.TomlTable;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.util.List;
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
    private final SubscriberRegistrar subscribers;
    private int building;

    @Inject
    ViewBuilder(UiServices services, Hosts hosts, SubscriberRegistrar subscribers) {
        this.services = services;
        this.hosts = hosts;
        this.subscribers = subscribers;
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
            WindowHost.Kind kind,
            String stateKey,
            String name) {
        DisposalScope scope = parentScope.child(name);
        Injector injector = parent.scopedChildBuilder(WindowScoped.class)
                .bind(Window.class, window)
                .bind(Scope.class, scope)
                .build();
        // Registered before anything is resolved through the host, so that its vetoes are reported in its scope.
        hosts.register(injector, scope);
        WindowHost host = new WindowHost(window, injector, scope, opener, kind, stateKey, services.mode());
        services.registry().addWindow(host);
        if (Boolean.getBoolean(UiMode.DEV_PROPERTY)) {
            ComponentInspector inspector = injector.getInstance(ComponentInspector.class);
            InspectorOverlay.install(
                    window.rootPane(),
                    host.shortcuts(),
                    scope,
                    component -> inspector
                            .inspect(component)
                            .map(ComponentInspector.Inspected::lines)
                            .orElse(List.of(component.getClass().getName())),
                    services.isMacOS());
        }
        return host;
    }

    /**
     * Builds a view in a window: creates its scope and the child injector hosting the view scope, registers the view
     * class's overrides, resolves the view, applies its component resources, places it, applies its tab titles, and
     * ends its build, which attaches its commands and registers the instances built meanwhile on the bus.
     */
    ViewHost buildView(
            WindowHost window,
            Optional<? extends Class<?>> type,
            Optional<? extends Enum<?>> variant,
            Function<Injector, ?> resolve,
            @Nullable DialogControl dialog,
            Consumer<ViewHost> place) {
        return build(window, type, variant, resolve, dialog, null, built -> {
            place.accept(built);
            return window.window().rootPane();
        });
    }

    /**
     * Builds an added view in an open window, as a window's own view is built, but for its content, which {@code place}
     * places and returns: its window's component orientation is applied to it, and the accessibility checks cover it
     * only. Its component state is restored last.
     */
    ViewHost buildAddedView(
            WindowHost window,
            Optional<? extends Class<?>> type,
            boolean factory,
            Function<Injector, ?> resolve,
            Function<ViewHost, Component> place) {
        return build(window, type, Optional.empty(), resolve, null, factory, built -> {
            Component content = place.apply(built);
            built.content(content);
            return content;
        });
    }

    /**
     * Returns whether a view of any window is being built.
     *
     * @return {@code true} during a build
     */
    boolean isBuilding() {
        return building > 0;
    }

    private ViewHost build(
            WindowHost window,
            Optional<? extends Class<?>> type,
            Optional<? extends Enum<?>> variant,
            Function<Injector, ?> resolve,
            @Nullable DialogControl dialog,
            @Nullable Boolean addedFromFactory,
            Function<ViewHost, Component> place) {
        DisposalScope scope = window.scope().child("view");
        Injector injector = window.injector()
                .scopedChildBuilder(ViewScoped.class)
                .bind(Scope.class, scope)
                .build();
        hosts.register(injector, scope);
        ViewHost view = new ViewHost(type.isPresent() ? type.get() : Object.class, injector, scope, window, dialog);
        variant.ifPresent(view::variant);
        if (addedFromFactory != null) {
            view.added(addedFromFactory);
        }
        services.registry().addView(view);
        // The instances built meanwhile join the bus when the build ends, as their commands are attached.
        Runnable subscribe = subscribers.hold();
        building++;
        boolean done = false;
        try {
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
            Component placed = place.apply(view);
            placed.applyComponentOrientation(
                    ComponentOrientation.getOrientation(services.resources().locale()));
            inject(view.type(), instance, resources, true);
            view.built();
            // In development mode and tests only: a user can't act on a missing accessible name.
            check(view, placed);
            if (view.isAdded()) {
                window.injector().getInstance(WindowStateSupport.class).restoreAdded(view);
            }
            done = true;
        } finally {
            building--;
            subscribe.run();
            // An added view whose build fails leaves its window as it was; a window whose own view fails is discarded
            // whole by its flow.
            if (!done && view.isAdded()) {
                try {
                    view.close();
                } finally {
                    window.remove(view);
                }
            }
        }
        view.completed();
        return view;
    }

    /**
     * Registers the shortcuts of a window's views again, in the order they were built, so that a shortcut that several
     * views share acts on the one built last, and one that a closed view took returns to the command it took it from.
     */
    void reregister(WindowHost window) {
        List<ViewHost> views = window.views();
        for (RefreshPhase phase : List.of(RefreshPhase.UNREGISTER, RefreshPhase.REGISTER)) {
            views.forEach(view -> view.refresh(phase));
        }
    }

    /**
     * Runs the accessibility checks over a component of a view, in development mode and tests only: a user can't act on
     * a missing accessible name. The window's root pane is its own view's content.
     */
    void check(ViewHost view, Component component) {
        if (services.mode().strict()) {
            AccessibilityCheck.check(component, services.supports().fields(view), view.type(), services.mode());
        }
    }

    // Applies the view's component resources again, at a refresh, but enabled and visible, which its code owns now.
    void reinject(ViewHost view) {
        Optional<Object> instance = view.view();
        if (instance.isEmpty()) {
            return;
        }
        Class<?> type = view.type();
        ComponentResources resources = ComponentResources.refreshing(
                table(type, view.variant()), path -> services.icons().load(type, path), !services.isMacOS(), type);
        inject(type, instance.get(), resources, false);
        inject(type, instance.get(), resources, true);
    }

    // The path of the @Exposed component field of a view that holds a component, nested ones included.
    Optional<String> fieldOf(ViewHost view, Component component) {
        return Optional.ofNullable(services.supports().fields(view).get(component));
    }

    // The component resources of a view, with its variant's table.
    ComponentResources componentResources(ViewHost view) {
        return resources(view.type(), view.variant());
    }

    // The tab-title of a view's component field.
    Optional<String> tabTitle(ViewHost view, String path) {
        return resources(view.type(), view.variant()).string(path, "tab-title");
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
