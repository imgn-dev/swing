package be.imgn.swing.docking.internal;

import be.imgn.swing.docking.DockLayout;
import be.imgn.swing.docking.DockRegion;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import org.jspecify.annotations.Nullable;

/**
 * The dock of the test harness, which never realizes a window: it follows the placements and draws its arrangement into
 * the dock's area, each split as a {@link JSplitPane}, each tab group as a {@link JTabbedPane}, so that the components
 * of dock views are in the frame's root pane. It has no header and no close button.
 */
final class HeadlessSurface implements DockSurface {

    private final JPanel area = new JPanel(new BorderLayout());
    private final Arrangement arrangement = new Arrangement();
    private final Listener listener;
    // While the arrangement is drawn anew: the tabbed panes it drops fire selection changes as their tabs move.
    private boolean drawing;

    HeadlessSurface(Listener listener) {
        this.listener = listener;
    }

    @Override
    public JComponent area() {
        return area;
    }

    @Override
    public void dock(DockEntry entry, @Nullable DockEntry target, DockRegion region, double proportion) {
        arrangement.dock(entry, target, region, proportion);
        render();
        listener.selected(entry);
    }

    @Override
    public void stage(DockEntry entry) {
        // Placed by the arrangement that follows.
    }

    @Override
    public void arrange(DockNode restored) {
        arrangement.arrange(restored);
        render();
    }

    @Override
    public void undock(DockEntry entry) {
        arrangement.undock(entry);
        render();
    }

    @Override
    public void select(DockEntry entry) {
        if (arrangement.select(entry)) {
            render();
        }
        listener.selected(entry);
    }

    @Override
    public void changed(DockEntry entry) {
        render();
    }

    @Override
    public Optional<DockNode> layout() {
        return arrangement.layout();
    }

    @Override
    public void dispose() {
        arrangement.clear();
        area.removeAll();
    }

    // Draws the arrangement anew into the area.
    private void render() {
        drawing = true;
        try {
            area.removeAll();
            arrangement.layout().ifPresent(root -> area.add(draw(root), BorderLayout.CENTER));
        } finally {
            drawing = false;
        }
        area.revalidate();
        area.repaint();
    }

    private Component draw(DockNode node) {
        return switch (node) {
            case DockNode.Split split -> {
                JSplitPane pane = new JSplitPane(
                        split.orientation() == DockLayout.Orientation.HORIZONTAL
                                ? JSplitPane.HORIZONTAL_SPLIT
                                : JSplitPane.VERTICAL_SPLIT,
                        draw(split.first()),
                        draw(split.second()));
                pane.setResizeWeight(split.proportion());
                pane.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent event) {
                        pane.removeComponentListener(this);
                        pane.setDividerLocation(split.proportion());
                    }
                });
                yield pane;
            }
            case DockNode.Tabs group -> {
                JTabbedPane tabs = new JTabbedPane();
                for (DockEntry entry : group.entries()) {
                    tabs.addTab(entry.title(), entry.icon(), entry.content());
                }
                tabs.setSelectedIndex(group.entries().indexOf(group.selected()));
                tabs.addChangeListener(event -> {
                    int index = tabs.getSelectedIndex();
                    if (!drawing && index >= 0) {
                        DockEntry selected = group.entries().get(index);
                        if (arrangement.select(selected)) {
                            listener.selected(selected);
                        }
                    }
                });
                yield tabs;
            }
        };
    }
}
