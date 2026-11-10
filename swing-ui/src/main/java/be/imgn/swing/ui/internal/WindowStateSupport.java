package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Window;
import be.imgn.swing.ui.WindowScoped;
import be.imgn.swing.ui.spi.ComponentState;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.jspecify.annotations.Nullable;

/** Saves and restores the state of its window: its bounds, and the state of its views' components. */
@WindowScoped
public final class WindowStateSupport {

    private static final int X = 0;
    private static final int Y = 1;
    private static final int WIDTH = 2;
    private static final int HEIGHT = 3;

    private final Window window;
    private final WindowStateStore store;
    private final WindowRegistry registry;
    private final ViewSupports supports;
    private @Nullable Rectangle normalBounds;
    private @Nullable Path directory;
    private boolean directoryLoaded;
    // The state of the added views that closed, by the binary name of their class, until the window's is saved.
    private final Map<String, SavedView> kept = new LinkedHashMap<>();
    private Supplier<Optional<SavedLayout>> layout = Optional::empty;

    @Inject
    WindowStateSupport(Window window, WindowStateStore store, WindowRegistry registry, ViewSupports supports) {
        this.window = window;
        this.store = store;
        this.registry = registry;
        this.supports = supports;
    }

    private WindowHost host() {
        return registry.windowOf(window.rootPane())
                .orElseThrow(() -> new IllegalStateException("The window is not open"));
    }

    /**
     * Restores the saved state of the window, and follows its bounds from then on. Saved bounds that don't lie entirely
     * within the usable bounds of one current screen are ignored.
     *
     * @param restoreBounds whether the bounds are restored
     * @return whether bounds were restored
     */
    public boolean restore(boolean restoreBounds) {
        java.awt.Window awt = window instanceof AwtWindow real ? real.awt() : null;
        if (awt != null) {
            normalBounds = awt.getBounds();
            awt.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    followBounds(awt);
                }

                @Override
                public void componentMoved(ComponentEvent event) {
                    followBounds(awt);
                }
            });
        }
        WindowHost host = host();
        Optional<SavedWindow> saved = store.get(host.stateKey());
        if (saved.isEmpty()) {
            return false;
        }
        Map<String, SavedComponent> components = saved.get().components();
        restoreComponents(ownViews(host), components == null ? Map.of() : components);
        if (awt == null || !restoreBounds) {
            return false;
        }
        Optional<Rectangle> bounds = Optional.ofNullable(saved.get().bounds())
                .map(WindowStateSupport::rectangle)
                .filter(WindowStateSupport::isOnOneScreen);
        bounds.ifPresent(awt::setBounds);
        if (bounds.isPresent() && Boolean.TRUE.equals(saved.get().maximized()) && awt instanceof Frame frame) {
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
        return bounds.isPresent();
    }

    private void followBounds(java.awt.Window awt) {
        if (!(awt instanceof Frame frame) || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            normalBounds = awt.getBounds();
        }
    }

    private static Rectangle rectangle(List<Integer> bounds) {
        return new Rectangle(bounds.get(X), bounds.get(Y), bounds.get(WIDTH), bounds.get(HEIGHT));
    }

    /** Saves the state of the window. */
    public void save() {
        WindowHost host = host();
        @Nullable List<Integer> bounds = null;
        boolean maximized = false;
        if (window instanceof AwtWindow real) {
            java.awt.Window awt = real.awt();
            followBounds(awt);
            Rectangle normal = normalBounds;
            if (normal != null) {
                bounds = List.of(normal.x, normal.y, normal.width, normal.height);
            }
            maximized = awt instanceof Frame frame && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
        }
        Map<String, SavedComponent> components = captureComponents(ownViews(host));
        // The added views open now, read now; what closed ones left in this session, for the classes of no open view;
        // and what was saved before, for the classes neither open nor closed in this session.
        Map<String, SavedView> views = new LinkedHashMap<>();
        store.get(host.stateKey()).map(SavedWindow::views).ifPresent(views::putAll);
        views.putAll(kept);
        for (ViewHost view : savingViews(host)) {
            views.put(view.type().getName(), captureView(view));
        }
        store.save(
                host.stateKey(),
                new SavedWindow(
                        bounds,
                        host.isFrame() ? maximized : null,
                        components.isEmpty() ? null : components,
                        directory().map(Path::toString).orElse(null),
                        views.isEmpty() ? null : views,
                        layout.get().orElse(null)));
    }

    /**
     * Restores the components of an added view, once its content is placed: from the state its class's views left when
     * they closed in this session, else from the window's saved state. A view created through an assisted factory
     * restores none.
     *
     * @param view the added view
     */
    public void restoreAdded(ViewHost view) {
        if (view.isFromFactory()) {
            return;
        }
        String type = view.type().getName();
        Optional<SavedView> state = Optional.ofNullable(kept.get(type))
                .or(() -> store.get(host().stateKey()).map(SavedWindow::views).map(views -> views.get(type)));
        restoreComponents(List.of(view), state.map(SavedView::components).orElse(Map.of()));
    }

    /**
     * Keeps the state of an added view that closes, until the window's state is saved. A view created through an
     * assisted factory keeps none.
     *
     * @param view the added view
     */
    public void keep(ViewHost view) {
        if (!view.isFromFactory()) {
            kept.put(view.type().getName(), captureView(view));
        }
    }

    /**
     * Returns the saved layout of the window's dock.
     *
     * @return the layout, empty when none is saved
     */
    public Optional<SavedLayout> savedLayout() {
        return store.get(host().stateKey()).map(SavedWindow::dock);
    }

    /**
     * Sets what gives the layout of the window's dock when the window's state is saved.
     *
     * @param source the layout, empty when the dock has none to save
     */
    public void layout(Supplier<Optional<SavedLayout>> source) {
        this.layout = source;
    }

    // The window's own view, the one it opened with.
    private static List<ViewHost> ownViews(WindowHost host) {
        return host.views().stream().filter(view -> !view.isAdded()).toList();
    }

    // The added views whose state is saved: those not created through a factory.
    private static List<ViewHost> savingViews(WindowHost host) {
        return host.views().stream()
                .filter(view -> view.isAdded() && !view.isFromFactory())
                .toList();
    }

    private SavedView captureView(ViewHost view) {
        Map<String, SavedComponent> components = captureComponents(List.of(view));
        return new SavedView(components.isEmpty() ? null : components);
    }

    /**
     * Returns the directory last chosen in a file chooser of the window, as saved, unless it no longer exists.
     *
     * @return the directory, empty when none was chosen or it no longer exists
     */
    public Optional<Path> directory() {
        if (!directoryLoaded) {
            directoryLoaded = true;
            directory = store.get(host().stateKey())
                    .map(SavedWindow::directory)
                    .map(saved -> store.file().getFileSystem().getPath(saved))
                    .orElse(null);
        }
        return Optional.ofNullable(directory).filter(Files::isDirectory);
    }

    /**
     * Sets the directory last chosen in a file chooser of the window, saved with its state.
     *
     * @param chosen the directory
     */
    public void directory(Path chosen) {
        directoryLoaded = true;
        directory = chosen.toAbsolutePath();
    }

    private Map<String, Component> components(List<ViewHost> views) {
        Map<String, Component> components = new LinkedHashMap<>();
        for (ViewHost view : views) {
            view.view().ifPresent(instance -> components.putAll(componentsOf(view.type(), instance)));
        }
        return components;
    }

    private <V> Map<String, Component> componentsOf(Class<V> type, Object instance) {
        return supports.stateOf(type)
                .map((ComponentState<V> state) -> state.components(type.cast(instance)))
                .orElse(Map.of());
    }

    private Map<String, SavedComponent> captureComponents(List<ViewHost> views) {
        Map<String, SavedComponent> saved = new LinkedHashMap<>();
        components(views).forEach((field, component) -> capture(component).ifPresent(state -> saved.put(field, state)));
        return saved;
    }

    private static Optional<SavedComponent> capture(Component component) {
        return switch (component) {
            case JSplitPane split -> Optional.of(new SavedComponent(split.getDividerLocation(), null, null));
            case JTabbedPane tabs -> Optional.of(new SavedComponent(null, tabs.getSelectedIndex(), null));
            case JTable table -> {
                List<SavedColumn> columns = new ArrayList<>();
                TableColumnModel model = table.getColumnModel();
                for (int index = 0; index < model.getColumnCount(); index++) {
                    TableColumn column = model.getColumn(index);
                    columns.add(new SavedColumn(column.getModelIndex(), column.getWidth()));
                }
                yield Optional.of(new SavedComponent(null, null, columns));
            }
            default -> Optional.empty();
        };
    }

    private void restoreComponents(List<ViewHost> views, Map<String, SavedComponent> saved) {
        components(views).forEach((field, component) -> {
            SavedComponent state = saved.get(field);
            if (state != null) {
                apply(component, state);
            }
        });
    }

    private static void apply(Component component, SavedComponent state) {
        switch (component) {
            case JSplitPane split -> {
                Integer location = state.dividerLocation();
                if (location != null) {
                    split.setDividerLocation(location);
                }
            }
            case JTabbedPane tabs -> {
                Integer index = state.selectedIndex();
                if (index != null && index >= 0 && index < tabs.getTabCount()) {
                    tabs.setSelectedIndex(index);
                }
            }
            case JTable table -> {
                List<SavedColumn> columns = state.columns();
                if (columns != null) {
                    restoreColumns(table, columns);
                }
            }
            default -> {
                // Other components have no saved state.
            }
        }
    }

    private static void restoreColumns(JTable table, List<SavedColumn> columns) {
        TableColumnModel model = table.getColumnModel();
        if (model.getColumnCount() != columns.size()) {
            return;
        }
        for (int target = 0; target < columns.size(); target++) {
            SavedColumn saved = columns.get(target);
            int current = indexOfModel(model, saved.modelIndex());
            if (current < 0) {
                return;
            }
            model.moveColumn(current, target);
            TableColumn column = model.getColumn(target);
            column.setPreferredWidth(saved.width());
            column.setWidth(saved.width());
        }
    }

    private static int indexOfModel(TableColumnModel model, int modelIndex) {
        for (int index = 0; index < model.getColumnCount(); index++) {
            if (model.getColumn(index).getModelIndex() == modelIndex) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Returns whether bounds lie entirely within the usable bounds of one current screen: its bounds minus its insets.
     *
     * @param bounds the bounds
     * @return {@code true} if one screen holds them
     */
    public static boolean isOnOneScreen(Rectangle bounds) {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        for (GraphicsDevice device :
                GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration configuration = device.getDefaultConfiguration();
            Rectangle usable = configuration.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
            usable = new Rectangle(
                    usable.x + insets.left,
                    usable.y + insets.top,
                    usable.width - insets.left - insets.right,
                    usable.height - insets.top - insets.bottom);
            if (usable.contains(bounds)) {
                return true;
            }
        }
        return false;
    }
}
