package be.imgn.swing.docking.internal;

import be.imgn.swing.docking.DockLayout;
import io.github.andrewauclair.moderndocking.api.DockingAPI;
import io.github.andrewauclair.moderndocking.layouts.DockingLayoutNode;
import io.github.andrewauclair.moderndocking.layouts.DockingSimplePanelNode;
import io.github.andrewauclair.moderndocking.layouts.DockingSplitPanelNode;
import io.github.andrewauclair.moderndocking.layouts.DockingTabPanelNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.JSplitPane;

/**
 * Converts an arrangement to ModernDocking's layout nodes and back. ModernDocking shows a group of one view as a simple
 * panel, without tabs, and a larger one as a tabbed panel: both are a tab group of the arrangement. It needs no window,
 * so that it is tested headless.
 */
final class ModernLayouts {

    private static final String NO_ANCHOR = "";
    // No class name: ModernDocking would load a class a node names and has no dockable of; every view is registered
    // before its layout is restored.
    private static final String NO_CLASS = "";
    private static final double PRECISION = 1000;

    private ModernLayouts() {}

    /**
     * Returns ModernDocking's node of an arrangement.
     *
     * @param docking the docking instance the nodes belong to
     * @param arrangement the arrangement
     * @return the node
     */
    static DockingLayoutNode toModern(DockingAPI docking, DockNode arrangement) {
        return switch (arrangement) {
            case DockNode.Split split ->
                new DockingSplitPanelNode(
                        docking,
                        toModern(docking, split.first()),
                        toModern(docking, split.second()),
                        split.orientation() == DockLayout.Orientation.HORIZONTAL
                                ? JSplitPane.HORIZONTAL_SPLIT
                                : JSplitPane.VERTICAL_SPLIT,
                        split.proportion(),
                        NO_ANCHOR);
            case DockNode.Tabs tabs
            when tabs.entries().size() == 1 -> {
                DockEntry entry = tabs.entries().getFirst();
                yield new DockingSimplePanelNode(
                        docking, entry.id(), NO_CLASS, NO_ANCHOR, entry.title(), entry.title());
            }
            case DockNode.Tabs tabs -> {
                DockEntry selected = tabs.selected();
                DockingTabPanelNode node = new DockingTabPanelNode(
                        docking, selected.id(), NO_CLASS, NO_ANCHOR, selected.title(), selected.title());
                // Adding the selected tab again moves it to its place among the others.
                for (DockEntry entry : tabs.entries()) {
                    node.addTab(entry.id(), NO_CLASS, NO_ANCHOR, entry.title(), entry.title());
                }
                yield node;
            }
        };
    }

    /**
     * Returns the arrangement of ModernDocking's node, its proportions rounded to 3 decimal places.
     *
     * @param node the node
     * @param entries the entry of each persistent id
     * @return the arrangement, empty for an empty node or one whose ids name no entry
     */
    static Optional<DockNode> fromModern(DockingLayoutNode node, Function<String, Optional<DockEntry>> entries) {
        return switch (node) {
            case DockingSplitPanelNode split -> {
                Optional<DockNode> first = fromModern(split.getLeft(), entries);
                Optional<DockNode> second = fromModern(split.getRight(), entries);
                if (first.isEmpty() || second.isEmpty()) {
                    yield first.or(() -> second);
                }
                yield Optional.of(new DockNode.Split(
                        split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                                ? DockLayout.Orientation.HORIZONTAL
                                : DockLayout.Orientation.VERTICAL,
                        rounded(split.getDividerProportion()),
                        first.get(),
                        second.get()));
            }
            case DockingTabPanelNode tabs -> {
                List<DockEntry> found = new ArrayList<>();
                for (DockingSimplePanelNode tab : tabs.getPersistentIDs()) {
                    entries.apply(tab.getPersistentID()).ifPresent(found::add);
                }
                Optional<DockEntry> selected =
                        entries.apply(tabs.getSelectedTabID()).filter(found::contains);
                yield found.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new DockNode.Tabs(found, selected.orElse(found.getFirst())));
            }
            case DockingSimplePanelNode simple ->
                entries.apply(simple.getPersistentID()).map(entry -> new DockNode.Tabs(List.of(entry), entry));
            default -> Optional.empty();
        };
    }

    // Rounded to 3 decimal places, and kept strictly between 0 and 1.
    private static double rounded(double proportion) {
        double rounded = Math.round(proportion * PRECISION) / PRECISION;
        return Math.min(Math.max(rounded, 1 / PRECISION), 1 - 1 / PRECISION);
    }
}
