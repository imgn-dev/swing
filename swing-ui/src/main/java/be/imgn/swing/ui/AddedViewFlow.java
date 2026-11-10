package be.imgn.swing.ui;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.internal.ExitVetoes;
import be.imgn.swing.ui.internal.AddedViews;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowStateSupport;
import be.imgn.toml.TomlTable;
import java.awt.Component;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.Icon;

/** Opens and closes the added views of open windows. Used on the EDT. */
@Singleton
@ExposeAs(AddedViews.class)
final class AddedViewFlow implements AddedViews {

    private final ViewBuilder builder;
    private final UiServices services;
    private final ExitVetoes vetoes;

    @Inject
    AddedViewFlow(ViewBuilder builder, UiServices services, ExitVetoes vetoes) {
        this.builder = builder;
        this.services = services;
        this.vetoes = vetoes;
    }

    @Override
    public ViewHost open(
            WindowHost window,
            Optional<? extends Class<?>> type,
            Function<Injector, ?> resolve,
            Function<ViewHost, Component> place) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(resolve, "resolve");
        Objects.requireNonNull(place, "place");
        if (builder.isBuilding()) {
            throw new IllegalStateException("A view is being built: a view can't be opened during another's build");
        }
        if (window.scope().isClosed()
                || window.isClosing()
                || services.lifecycle().isShuttingDown()) {
            throw new IllegalStateException("The window is closed or closing: no view can be opened in it");
        }
        ViewHost view = builder.buildAddedView(window, type, type.isEmpty(), resolve, place);
        builder.reregister(window);
        return view;
    }

    @Override
    public boolean close(ViewHost view, Runnable leave) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(leave, "leave");
        if (!view.isAdded()) {
            throw new IllegalArgumentException(view.type().getName() + " is not an added view of its window");
        }
        if (view.scope().isClosed()) {
            return true;
        }
        if (!vetoes.of(view.scope()).stream().allMatch(ExitVeto::canExit)) {
            return false;
        }
        WindowHost window = view.window();
        leave.run();
        window.injector().getInstance(WindowStateSupport.class).keep(view);
        try {
            view.close();
        } finally {
            window.remove(view);
        }
        // A shortcut the view took from a command of the window returns to it.
        builder.reregister(window);
        return true;
    }

    @Override
    public boolean isBuilding() {
        return builder.isBuilding();
    }

    @Override
    public TomlTable resources(Class<?> type) {
        return services.resources().table(type);
    }

    @Override
    public Icon icon(Class<?> type, String path) {
        return services.icons().load(type, path);
    }

    @Override
    public Optional<String> fieldOf(ViewHost view, Component component) {
        return builder.fieldOf(view, component);
    }

    @Override
    public Optional<String> fieldString(ViewHost view, Component component, String property) {
        return builder.fieldOf(view, component)
                .flatMap(path -> builder.componentResources(view).string(path, property));
    }

    @Override
    public void check(ViewHost view, Component component) {
        builder.check(view, component);
    }

    @Override
    public Optional<Class<?>> viewClass(String name) {
        return services.supports().viewClass(name);
    }
}
