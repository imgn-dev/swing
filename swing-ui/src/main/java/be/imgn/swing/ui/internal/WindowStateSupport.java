package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Window;
import be.imgn.swing.ui.WindowScoped;
import be.imgn.swing.ui.support.ComponentState;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.jspecify.annotations.Nullable;

/** Saves and restores the state of its window: its bounds, and the state of its views' components. */
@WindowScoped
public final class WindowStateSupport {

    private final Window window;
    private final WindowStateStore store;
    private final WindowRegistry registry;
    private final ViewSupports supports;
    private @Nullable Rectangle normalBounds;

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
        restoreComponents(host, saved.get().components());
        if (awt == null || !restoreBounds) {
            return false;
        }
        Optional<Rectangle> bounds = saved.get().bounds().filter(WindowStateSupport::isOnOneScreen);
        bounds.ifPresent(awt::setBounds);
        if (bounds.isPresent() && saved.get().maximized() && awt instanceof Frame frame) {
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
        return bounds.isPresent();
    }

    private void followBounds(java.awt.Window awt) {
        if (!(awt instanceof Frame frame) || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            normalBounds = awt.getBounds();
        }
    }

    /** Saves the state of the window. */
    public void save() {
        WindowHost host = host();
        Optional<Rectangle> bounds = Optional.empty();
        boolean maximized = false;
        if (window instanceof AwtWindow real) {
            java.awt.Window awt = real.awt();
            followBounds(awt);
            bounds = Optional.ofNullable(normalBounds);
            maximized = host.isMain()
                    && awt instanceof Frame frame
                    && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
        }
        store.save(host.stateKey(), new SavedWindow(bounds, maximized, captureComponents(host)));
    }

    private Map<String, Component> components(WindowHost host) {
        Map<String, Component> components = new LinkedHashMap<>();
        for (ViewHost view : host.views()) {
            view.view().ifPresent(instance -> components.putAll(componentsOf(view.type(), instance)));
        }
        return components;
    }

    private <V> Map<String, Component> componentsOf(Class<V> type, Object instance) {
        return supports.stateOf(type)
                .map((ComponentState<V> state) -> state.components(type.cast(instance)))
                .orElse(Map.of());
    }

    private Map<String, SavedComponent> captureComponents(WindowHost host) {
        Map<String, SavedComponent> saved = new LinkedHashMap<>();
        components(host).forEach((field, component) -> capture(component).ifPresent(state -> saved.put(field, state)));
        return saved;
    }

    private static Optional<SavedComponent> capture(Component component) {
        return switch (component) {
            case JSplitPane split ->
                Optional.of(new SavedComponent(
                        OptionalInt.of(split.getDividerLocation()), OptionalInt.empty(), Optional.empty()));
            case JTabbedPane tabs ->
                Optional.of(new SavedComponent(
                        OptionalInt.empty(), OptionalInt.of(tabs.getSelectedIndex()), Optional.empty()));
            case JTable table -> {
                List<SavedComponent.Column> columns = new ArrayList<>();
                TableColumnModel model = table.getColumnModel();
                for (int index = 0; index < model.getColumnCount(); index++) {
                    TableColumn column = model.getColumn(index);
                    columns.add(new SavedComponent.Column(column.getModelIndex(), column.getWidth()));
                }
                yield Optional.of(new SavedComponent(OptionalInt.empty(), OptionalInt.empty(), Optional.of(columns)));
            }
            default -> Optional.empty();
        };
    }

    private void restoreComponents(WindowHost host, Map<String, SavedComponent> saved) {
        components(host).forEach((field, component) -> {
            SavedComponent state = saved.get(field);
            if (state != null) {
                apply(component, state);
            }
        });
    }

    private static void apply(Component component, SavedComponent state) {
        switch (component) {
            case JSplitPane split -> state.dividerLocation().ifPresent(split::setDividerLocation);
            case JTabbedPane tabs ->
                state.selectedIndex().ifPresent(index -> {
                    if (index >= 0 && index < tabs.getTabCount()) {
                        tabs.setSelectedIndex(index);
                    }
                });
            case JTable table -> state.columns().ifPresent(columns -> restoreColumns(table, columns));
            default -> {
                // Other components have no saved state.
            }
        }
    }

    private static void restoreColumns(JTable table, List<SavedComponent.Column> columns) {
        TableColumnModel model = table.getColumnModel();
        if (model.getColumnCount() != columns.size()) {
            return;
        }
        for (int target = 0; target < columns.size(); target++) {
            SavedComponent.Column saved = columns.get(target);
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
