package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Key;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.docking.internal.DockEntry;
import be.imgn.swing.docking.internal.DockNode;
import be.imgn.swing.docking.internal.DockSurface;
import be.imgn.swing.docking.internal.DockSurfaces;
import be.imgn.swing.docking.internal.SavedLayouts;
import be.imgn.swing.ui.Window;
import be.imgn.swing.ui.WindowScoped;
import be.imgn.swing.ui.internal.AddedViews;
import be.imgn.swing.ui.internal.FocusOrder;
import be.imgn.swing.ui.internal.RefreshPhase;
import be.imgn.swing.ui.internal.SavedLayout;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import be.imgn.swing.ui.internal.WindowStateSupport;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;

/**
 * The dock of a frame: views docked in it, split and tabbed, which the user rearranges, opened and closed while the
 * frame is open, their layout saved with the frame's window state. A frame holds one dock, which its frame view, or any
 * bean of the frame's window scope, injects; the frame view places its {@link #area}. Every method is called on the
 * EDT.
 */
@WindowScoped
public final class Dock {

    private final AddedViews added;
    private final WindowRegistry windows;
    private final Window window;
    private final DockSurfaces surfaces;
    private final Scope scope;
    private final Injector injector;
    private final List<Open> views = new ArrayList<>();
    // The views that were active, the most recent last.
    private final List<Open> activated = new ArrayList<>();
    private final Property<Optional<DockView>> active = Property.of(Optional.empty());
    private @Nullable DockSurface surface;
    private boolean opening;

    @Inject
    Dock(
            AddedViews added,
            WindowRegistry windows,
            Window window,
            DockSurfaces surfaces,
            Scope scope,
            Injector injector) {
        this.added = added;
        this.windows = windows;
        this.window = window;
        this.surfaces = surfaces;
        this.scope = scope;
        this.injector = injector;
    }

    /**
     * Returns the dock's area, which the frame view returns from {@code content()} or places in it. It is called once,
     * during the build of the frame view. Once that build has ended and its accessibility checks have run, before the
     * frame is packed, the dock restores its saved layout; when there is none, or none of it can be restored, it calls
     * {@code layOut} with itself, which opens the views of the frame's first layout. An empty dock shows an empty area.
     *
     * @param layOut opens the views of the first layout
     * @return the area
     * @throws IllegalStateException if called again, outside the build of a frame view, or in a dialog's window
     * @throws NullPointerException if {@code layOut} is {@code null}
     */
    public JComponent area(Consumer<? super Dock> layOut) {
        Objects.requireNonNull(layOut, "layOut");
        if (surface != null) {
            throw new IllegalStateException("The area of a dock is created once");
        }
        WindowHost host = host();
        ViewHost own = host.views().getFirst();
        if (!host.isFrame() || !own.isBuilding()) {
            throw new IllegalStateException("The area of a dock is created during the build of its frame view");
        }
        DockSurface created = surfaces.create(window, new DockSurface.Listener() {
            @Override
            public void selected(DockEntry entry) {
                Dock.this.selected(entry);
            }

            @Override
            public void closeRequested(DockEntry entry) {
                openOf(entry).ifPresent(Dock.this::closeOpen);
            }
        });
        surface = created;
        scope.onClose(created::dispose);
        followFocus();
        WindowStateSupport state = host.injector().getInstance(WindowStateSupport.class);
        state.layout(this::savedLayout);
        own.whenBuilt(() -> {
            if (!restore(state)) {
                layOut.accept(this);
            }
        });
        return created.area();
    }

    /**
     * Opens a view of a dock view class, resolved through its child injector, and docks it as {@code placement} says. A
     * dock holds at most one view of a class opened this way: when one is open, this brings it to the front, as
     * {@link #toFront} does, ignores {@code placement}, and returns it.
     *
     * @param type the dock view class
     * @param placement where to dock it
     * @param <V> the dock view class
     * @return the view, once docked
     * @throws IllegalStateException while a view is being built, or before the area is created
     * @throws NullPointerException if an argument is {@code null}
     */
    public <V extends DockView> V open(Class<V> type, Placement placement) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(placement, "placement");
        checkNotBuilding();
        Optional<Open> existing = views.stream()
                .filter(open -> open.factory == null && open.entry.host().type().equals(type))
                .findFirst();
        if (existing.isPresent()) {
            bringToFront(existing.get());
            return type.cast(existing.get().view);
        }
        Open open = openView(
                Optional.of(type),
                injector -> injector.getInstance(type),
                null,
                DockEntry.idOf(type),
                true,
                entry -> dock(entry, placement));
        return type.cast(open.view);
    }

    /**
     * Opens a view created through an assisted factory, as {@code Frames.open(Class<F>, Function)} does, and docks it.
     * The views opened through one factory are told apart by {@code key}: when one with the same factory and key is
     * open, this brings it to the front, ignores {@code create} and {@code placement}, and returns it.
     *
     * @param factory the generated factory class
     * @param create creates the view with the factory
     * @param key what tells the views of the factory apart
     * @param placement where to dock it
     * @param <F> the factory class
     * @return the view, once docked
     * @throws IllegalStateException while a view is being built, or before the area is created
     * @throws IllegalArgumentException for a blank key
     * @throws NullPointerException if an argument is {@code null}
     */
    public <F> DockView open(
            Class<F> factory, Function<? super F, ? extends DockView> create, String key, Placement placement) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placement, "placement");
        if (key.isBlank()) {
            throw new IllegalArgumentException("The key of a view opened through a factory is blank");
        }
        checkNotBuilding();
        String id = DockEntry.idOf(factory, key);
        Optional<Open> existing = views.stream()
                .filter(open -> open.factory != null && open.entry.id().equals(id))
                .findFirst();
        if (existing.isPresent()) {
            bringToFront(existing.get());
            return existing.get().view;
        }
        Open open = openView(
                Optional.empty(),
                injector -> create.apply(injector.getInstance(factory)),
                factory,
                id,
                true,
                entry -> dock(entry, placement));
        return open.view;
    }

    /**
     * Returns the open dock views, in the order they were opened.
     *
     * @return the views
     */
    public List<DockView> views() {
        return views.stream().map(open -> open.view).toList();
    }

    /**
     * Returns the dock view that has the focus, else the one that had it last, else, when none had it, the one opened
     * last. A view becomes the active one when it is opened, when it is brought to the front, and when a component of
     * its content gains the permanent focus. When the active view closes, the active one is the open view that was
     * active before it most recently, else the one opened last. It changes on the EDT.
     *
     * @return the active view, empty when none is open
     */
    public Observable<Optional<DockView>> active() {
        return active;
    }

    /**
     * Returns the title of an open view, as its {@link Docked#title()} gives it.
     *
     * @param view the view
     * @return the title
     * @throws IllegalArgumentException if the view is not open in this dock
     * @throws NullPointerException if {@code view} is {@code null}
     */
    public String title(DockView view) {
        return openOf(view).docked.title();
    }

    /**
     * Selects a view's tab in its tab group, without moving the focus and without changing {@link #active()}.
     *
     * @param view the view
     * @throws IllegalArgumentException if the view is not open in this dock
     * @throws NullPointerException if {@code view} is {@code null}
     */
    public void select(DockView view) {
        surface().select(openOf(view).entry);
    }

    /**
     * Brings a view to the front: selects its tab, moves the focus to the first component of its content that takes it,
     * through the window's {@link Window#focus(Component)}, and makes it the active one.
     *
     * @param view the view
     * @throws IllegalArgumentException if the view is not open in this dock
     * @throws NullPointerException if {@code view} is {@code null}
     */
    public void toFront(DockView view) {
        bringToFront(openOf(view));
    }

    /**
     * Closes a view as an added view is closed: asks the exit vetoes of its scope, and returns {@code false} when one
     * vetoes; otherwise undocks it, closes its scope, and returns {@code true}. The user closing a view through its
     * header's close button does the same, and nothing when it vetoes.
     *
     * @param view the view
     * @return whether it closed
     * @throws IllegalArgumentException if the view is not open in this dock
     * @throws NullPointerException if {@code view} is {@code null}
     */
    public boolean close(DockView view) {
        return closeOpen(openOf(view));
    }

    /**
     * Returns the arrangement of the open views.
     *
     * @return the layout, empty when none is open
     */
    public Optional<DockLayout> layout() {
        return surface == null ? Optional.empty() : surface.layout().map(this::toLayout);
    }

    // No view opens during the build of another, not even an open one that comes to the front instead.
    private void checkNotBuilding() {
        if (added.isBuilding()) {
            throw new IllegalStateException("A view is being built: no view of a dock opens during another's build");
        }
    }

    // Whether this dock is opening the view of a host, which may then resolve its Docked.
    boolean isOpening(ViewHost view) {
        return opening && view.isAdded() && view.window().window().equals(window);
    }

    private Open openView(
            Optional<? extends Class<?>> type,
            Function<Injector, ?> resolve,
            @Nullable Class<?> factory,
            String id,
            boolean docks,
            Consumer<DockEntry> place) {
        DockSurface current = surface();
        List<Open> placed = new ArrayList<>(1);
        boolean opened = false;
        opening = true;
        try {
            added.open(host(), type, resolve, built -> {
                if (!(built.view().orElseThrow() instanceof DockView view)) {
                    throw new IllegalArgumentException(built.type().getName() + " is not a dock view class");
                }
                Docked docked = built.injector().getInstance(Docked.class);
                JComponent content = view.content();
                DockEntry entry = new DockEntry(built, content, docked::title, id);
                // Before it is docked: ModernDocking reads the icon of a tab and of a header when it adds them.
                entry.icon(iconOf(built.type()));
                placed.add(new Open(entry, view, docked, factory));
                place.accept(entry);
                return content;
            });
            opened = true;
        } finally {
            opening = false;
            // A view whose build fails once docked leaves the dock as it was, as it leaves its window.
            if (!opened && docks && !placed.isEmpty()) {
                current.undock(placed.getFirst().entry);
            }
        }
        Open open = placed.getFirst();
        views.add(open);
        ViewHost host = open.entry.host();
        open.docked.onChange(() -> current.changed(open.entry));
        // At the end of the build: the title the resources give, unless the view set its own, and the icon.
        if (!open.docked.isSet()) {
            open.docked.resourceTitle(titleOf(host.type()));
        }
        current.changed(open.entry);
        open.checked = isShowing(open.entry.content());
        host.onRefresh(RefreshPhase.TITLE, host.scope(), () -> {
            open.docked.refreshTitle(titleOf(host.type()));
            open.entry.icon(iconOf(host.type()));
            current.changed(open.entry);
        });
        activate(open);
        return open;
    }

    // Docks an entry as a placement says, against its target.
    private void dock(DockEntry entry, Placement placement) {
        Optional<Open> target = Optional.empty();
        Class<? extends DockView> type = placement.target();
        if (type != null) {
            target = views.reversed().stream()
                    .filter(open -> open.entry.host().type().equals(type))
                    .findFirst();
        }
        if (target.isEmpty() && placement.region() == DockRegion.CENTER) {
            target = activated.isEmpty() ? Optional.empty() : Optional.of(activated.getLast());
        }
        surface().dock(entry, target.map(open -> open.entry).orElse(null), placement.region(), placement.proportion());
    }

    private void bringToFront(Open open) {
        surface().select(open.entry);
        FocusOrder.first(open.entry.content()).ifPresent(window::focus);
        activate(open);
    }

    private boolean closeOpen(Open open) {
        boolean closed = added.close(open.entry.host(), () -> surface().undock(open.entry));
        if (closed) {
            open.docked.onChange(() -> {});
            views.remove(open);
            boolean wasActive = !activated.isEmpty() && activated.getLast() == open;
            activated.remove(open);
            if (wasActive) {
                active.set(
                        activated.isEmpty()
                                ? views.isEmpty() ? Optional.empty() : Optional.of(views.getLast().view)
                                : Optional.of(activated.getLast().view));
            }
        }
        return closed;
    }

    private void activate(Open open) {
        activated.remove(open);
        activated.add(open);
        active.set(Optional.of(open.view));
    }

    // A tab became the selected one: the views its build skipped while hidden are checked the first time they show.
    private void selected(DockEntry entry) {
        Optional<Open> open = openOf(entry);
        if (open.isPresent() && !open.get().checked) {
            open.get().checked = true;
            added.check(entry.host(), entry.content());
        }
    }

    // A view becomes the active one when a component of its content gains the permanent focus.
    private void followFocus() {
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        PropertyChangeListener listener = event -> {
            if (event.getNewValue() instanceof Component owner) {
                views.stream()
                        .filter(open -> SwingUtilities.isDescendingFrom(owner, open.entry.content()))
                        .findFirst()
                        .ifPresent(this::activate);
            }
        };
        focus.addPropertyChangeListener("permanentFocusOwner", listener);
        scope.onClose(() -> focus.removePropertyChangeListener("permanentFocusOwner", listener));
    }

    // Restores the saved layout: opens its views, then arranges them as it says.
    private boolean restore(WindowStateSupport state) {
        Optional<SavedLayouts.Plan> plan =
                state.savedLayout().flatMap(saved -> SavedLayouts.read(saved, this::restorable));
        if (plan.isEmpty()) {
            return false;
        }
        surface().arrange(openAll(plan.get()));
        return true;
    }

    // The view class of a binary name that a dock opens by its class: not one created through an assisted factory.
    private Optional<Class<?>> restorable(String name) {
        return added.viewClass(name)
                .filter(type -> injector.getBeanDefinition(Key.of(type)).isPresent());
    }

    private DockNode openAll(SavedLayouts.Plan plan) {
        return switch (plan) {
            case SavedLayouts.Plan.Split split ->
                new DockNode.Split(
                        split.orientation(), split.proportion(), openAll(split.first()), openAll(split.second()));
            case SavedLayouts.Plan.Tabs tabs -> {
                List<DockEntry> entries = new ArrayList<>();
                for (Class<? extends DockView> type : tabs.types()) {
                    entries.add(openView(
                                    Optional.of(type),
                                    injector -> injector.getInstance(type),
                                    null,
                                    DockEntry.idOf(type),
                                    false,
                                    surface()::stage)
                            .entry);
                }
                yield new DockNode.Tabs(entries, entries.get(tabs.selected()));
            }
        };
    }

    private Optional<SavedLayout> savedLayout() {
        return surface == null
                ? Optional.empty()
                : surface.layout()
                        .flatMap(arrangement -> SavedLayouts.save(
                                arrangement,
                                entry -> openOf(entry)
                                        .map(open -> open.factory == null)
                                        .orElse(false)));
    }

    private DockLayout toLayout(DockNode node) {
        return switch (node) {
            case DockNode.Split split ->
                new DockLayout.Split(
                        split.orientation(), split.proportion(), toLayout(split.first()), toLayout(split.second()));
            case DockNode.Tabs tabs ->
                new DockLayout.Tabs(
                        tabs.entries().stream()
                                .map(entry -> openOf(entry).orElseThrow().view)
                                .toList(),
                        openOf(tabs.selected()).orElseThrow().view);
        };
    }

    private String titleOf(Class<?> type) {
        TomlTable resources = added.resources(type);
        return resources.entries().get("title") instanceof TomlString title ? title.value() : type.getSimpleName();
    }

    private @Nullable Icon iconOf(Class<?> type) {
        TomlTable resources = added.resources(type);
        return resources.entries().get("icon") instanceof TomlString icon ? added.icon(type, icon.value()) : null;
    }

    private Open openOf(DockView view) {
        Objects.requireNonNull(view, "view");
        return views.stream()
                .filter(open -> open.view.equals(view))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(view + " is not open in this dock"));
    }

    private Optional<Open> openOf(DockEntry entry) {
        return views.stream().filter(open -> open.entry == entry).findFirst();
    }

    private DockSurface surface() {
        DockSurface current = surface;
        if (current == null) {
            throw new IllegalStateException("The dock has no area yet: its frame view creates it with area");
        }
        return current;
    }

    private WindowHost host() {
        return windows.windowOf(window.rootPane())
                .orElseThrow(() -> new IllegalStateException("The window of the dock is not open"));
    }

    // Visible up to the window's root pane: a tab that isn't selected is not.
    private boolean isShowing(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
            if (current.equals(window.rootPane())) {
                return true;
            }
        }
        return true;
    }

    /** A view open in the dock. */
    private static final class Open {
        private final DockEntry entry;
        private final DockView view;
        private final Docked docked;
        private final @Nullable Class<?> factory;
        // Whether the accessibility checks covered its content, which its build skips when its tab isn't selected.
        private boolean checked;

        Open(DockEntry entry, DockView view, Docked docked, @Nullable Class<?> factory) {
            this.entry = entry;
            this.view = view;
            this.docked = docked;
            this.factory = factory;
        }
    }
}
