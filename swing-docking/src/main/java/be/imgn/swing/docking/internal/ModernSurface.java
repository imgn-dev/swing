package be.imgn.swing.docking.internal;

import be.imgn.swing.docking.DockRegion;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;
import io.github.andrewauclair.moderndocking.layouts.WindowLayout;
import io.github.andrewauclair.moderndocking.ui.ToolbarLocation;
import java.awt.Window;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;

/**
 * The real dock, drawn by ModernDocking: one {@code Docking} instance per frame that has a dock, with the frame as its
 * main window, and a {@code RootDockingPanel} with no toolbars as the dock's area. Until the frame is shown, its layout
 * is the arrangement the placements gave, since ModernDocking's dividers have no size before; once it is, ModernDocking
 * gives it, as the user may have rearranged it. It sets none of ModernDocking's static settings, and saves nothing to a
 * file of ModernDocking's own.
 */
final class ModernSurface implements DockSurface {

    private final Window frame;
    private final Docking docking;
    private final RootDockingPanel area;
    private final Listener listener;
    private final Arrangement given = new Arrangement();
    private final Map<DockEntry, DockablePanel> panels = new LinkedHashMap<>();

    ModernSurface(Window frame, Listener listener) {
        this.frame = frame;
        this.listener = listener;
        this.docking = new Docking(frame);
        this.area = new RootDockingPanel(docking, frame, EnumSet.noneOf(ToolbarLocation.class));
    }

    @Override
    public JComponent area() {
        return area;
    }

    @Override
    public void dock(DockEntry entry, @Nullable DockEntry target, DockRegion region, double proportion) {
        boolean empty = given.layout().isEmpty() && panels.isEmpty();
        DockablePanel panel = register(entry);
        DockablePanel against = target == null ? null : panels.get(target);
        if (empty) {
            docking.dock(panel, frame);
        } else if (against != null) {
            docking.dock(panel, against, DockingRegion.valueOf(region.name()), proportion);
        } else {
            docking.dock(panel, frame, DockingRegion.valueOf(region.name()), proportion);
        }
        given.dock(entry, target, region, proportion);
        listener.selected(entry);
    }

    @Override
    public void stage(DockEntry entry) {
        register(entry);
    }

    private DockablePanel register(DockEntry entry) {
        DockablePanel panel = new DockablePanel(entry, listener);
        panels.put(entry, panel);
        docking.registerDockable(panel);
        return panel;
    }

    @Override
    public void arrange(DockNode restored) {
        given.arrange(restored);
        docking.getDockingState()
                .restoreWindowLayout_PreserveSizeAndPos(
                        frame, new WindowLayout(frame, ModernLayouts.toModern(docking, restored)));
    }

    @Override
    public void undock(DockEntry entry) {
        DockablePanel panel = panels.remove(entry);
        if (panel == null) {
            return;
        }
        given.undock(entry);
        if (docking.isDocked(panel)) {
            docking.undock(panel);
        }
        // So that a view of the same class may open again with the same persistent id.
        docking.deregisterDockable(panel);
    }

    @Override
    public void select(DockEntry entry) {
        DockablePanel panel = panels.get(entry);
        if (panel != null && docking.isDocked(panel)) {
            given.select(entry);
            docking.bringToFront(panel);
        }
        listener.selected(entry);
    }

    @Override
    public void changed(DockEntry entry) {
        DockablePanel panel = panels.get(entry);
        if (panel != null && docking.isDocked(panel)) {
            docking.updateTabInfo(panel);
            // updateTabInfo leaves the icon of the tab as it was: ModernDocking reads it once. Its header's stays.
            if (SwingUtilities.getAncestorOfClass(JTabbedPane.class, panel) instanceof JTabbedPane tabs) {
                for (int index = 0; index < tabs.getTabCount(); index++) {
                    if (SwingUtilities.isDescendingFrom(panel, tabs.getComponentAt(index))) {
                        tabs.setIconAt(index, entry.icon());
                    }
                }
            }
        }
    }

    @Override
    public Optional<DockNode> layout() {
        if (!frame.isShowing()) {
            return given.layout();
        }
        Map<String, DockEntry> byId = new LinkedHashMap<>();
        panels.keySet().forEach(entry -> byId.put(entry.id(), entry));
        return ModernLayouts.fromModern(
                docking.getDockingState().getWindowLayout(frame).getRootNode(),
                id -> Optional.ofNullable(byId.get(id)));
    }

    @Override
    public void dispose() {
        for (DockablePanel panel : List.copyOf(panels.values())) {
            docking.deregisterDockable(panel);
        }
        panels.clear();
        given.clear();
        docking.uninitialize();
    }
}
