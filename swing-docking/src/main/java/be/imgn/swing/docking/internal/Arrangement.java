package be.imgn.swing.docking.internal;

import be.imgn.swing.docking.DockLayout;
import be.imgn.swing.docking.DockRegion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An arrangement of entries, as the placements build it: the headless surface draws it, and the real surface keeps it
 * until its frame is shown, since ModernDocking's dividers have no size before.
 */
final class Arrangement {

    private @Nullable Node root;

    /**
     * Docks an entry: fills an empty arrangement; adds it as the last tab of the target's group, selected, for
     * {@code CENTER}, the first group when there is no target; else splits the target's group, or the whole arrangement
     * when there is no target, in two.
     */
    void dock(DockEntry entry, @Nullable DockEntry target, DockRegion region, double proportion) {
        Group group = new Group(entry);
        Node current = root;
        if (current == null) {
            root = group;
        } else if (region == DockRegion.CENTER) {
            Group tabs = target == null ? firstGroup(current) : groupOf(current, target);
            tabs.entries.add(entry);
            tabs.selected = entry;
        } else {
            Node old = target == null ? current : groupOf(current, target);
            replace(old, split(region, proportion, group, old));
        }
    }

    // The split that a placement on a side of a target gives: the view's group takes the proportion on that side.
    private static Node split(DockRegion region, double proportion, Node view, Node old) {
        return switch (region) {
            case WEST -> new Split(DockLayout.Orientation.HORIZONTAL, proportion, view, old);
            case EAST -> new Split(DockLayout.Orientation.HORIZONTAL, 1 - proportion, old, view);
            case NORTH -> new Split(DockLayout.Orientation.VERTICAL, proportion, view, old);
            case SOUTH -> new Split(DockLayout.Orientation.VERTICAL, 1 - proportion, old, view);
            case CENTER -> throw new IllegalArgumentException("CENTER splits nothing");
        };
    }

    /** Replaces the arrangement with a restored one. */
    void arrange(DockNode arrangement) {
        root = toNode(arrangement);
    }

    private static Node toNode(DockNode node) {
        return switch (node) {
            case DockNode.Split split ->
                new Split(split.orientation(), split.proportion(), toNode(split.first()), toNode(split.second()));
            case DockNode.Tabs tabs -> {
                Group group = new Group(tabs.selected());
                group.entries.clear();
                group.entries.addAll(tabs.entries());
                yield group;
            }
        };
    }

    /**
     * Takes an entry out: a group left with none leaves, and the split that held it is replaced with its other part.
     */
    void undock(DockEntry entry) {
        Node current = root;
        if (current == null) {
            return;
        }
        Group group = findGroup(current, entry);
        if (group == null) {
            return;
        }
        int index = group.entries.indexOf(entry);
        group.entries.remove(entry);
        if (group.entries.isEmpty()) {
            remove(group);
        } else if (group.selected == entry) {
            group.selected = group.entries.get(Math.min(index, group.entries.size() - 1));
        }
    }

    // Removes a group: the split that holds it is replaced with its other part.
    private void remove(Group group) {
        if (root == group) {
            root = null;
            return;
        }
        Split parent = parentOf(root, group);
        if (parent != null) {
            replace(parent, parent.first == group ? parent.second : parent.first);
        }
    }

    /**
     * Selects an entry in its group.
     *
     * @return whether the selection changed
     */
    boolean select(DockEntry entry) {
        Node current = root;
        if (current == null) {
            return false;
        }
        Group group = groupOf(current, entry);
        if (group.selected == entry) {
            return false;
        }
        group.selected = entry;
        return true;
    }

    /** Returns the arrangement, empty when no entry is docked. */
    Optional<DockNode> layout() {
        Node current = root;
        return current == null ? Optional.empty() : Optional.of(toDockNode(current));
    }

    /** Empties the arrangement. */
    void clear() {
        root = null;
    }

    private static DockNode toDockNode(Node node) {
        return switch (node) {
            case Split split ->
                new DockNode.Split(
                        split.orientation, split.proportion, toDockNode(split.first), toDockNode(split.second));
            case Group group -> new DockNode.Tabs(group.entries, group.selected);
        };
    }

    private void replace(Node old, Node replacement) {
        if (root == old) {
            root = replacement;
            return;
        }
        Split parent = parentOf(root, old);
        if (parent == null) {
            return;
        }
        if (parent.first == old) {
            parent.first = replacement;
        } else {
            parent.second = replacement;
        }
    }

    private static @Nullable Split parentOf(@Nullable Node node, Node child) {
        if (node instanceof Split split) {
            if (split.first == child || split.second == child) {
                return split;
            }
            Split found = parentOf(split.first, child);
            return found != null ? found : parentOf(split.second, child);
        }
        return null;
    }

    private static Group groupOf(Node node, DockEntry entry) {
        Group found = findGroup(node, entry);
        if (found == null) {
            throw new IllegalArgumentException(entry.id() + " is not docked");
        }
        return found;
    }

    private static @Nullable Group findGroup(Node node, DockEntry entry) {
        return switch (node) {
            case Group group -> group.entries.contains(entry) ? group : null;
            case Split split -> {
                Group found = findGroup(split.first, entry);
                yield found != null ? found : findGroup(split.second, entry);
            }
        };
    }

    private static Group firstGroup(Node node) {
        return switch (node) {
            case Group group -> group;
            case Split split -> firstGroup(split.first);
        };
    }

    /** A node of the arrangement, which docking and undocking change in place. */
    private sealed interface Node permits Split, Group {}

    private static final class Split implements Node {
        private final DockLayout.Orientation orientation;
        private final double proportion;
        private Node first;
        private Node second;

        Split(DockLayout.Orientation orientation, double proportion, Node first, Node second) {
            this.orientation = orientation;
            this.proportion = proportion;
            this.first = first;
            this.second = second;
        }
    }

    private static final class Group implements Node {
        private final List<DockEntry> entries = new ArrayList<>();
        private DockEntry selected;

        Group(DockEntry entry) {
            entries.add(entry);
            this.selected = entry;
        }
    }
}
