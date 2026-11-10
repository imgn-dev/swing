package be.imgn.swing.docking;

import java.util.List;
import java.util.Objects;

/**
 * The arrangement of the open views of a {@link Dock}: splits and tab groups. A placement gives these layouts, where
 * {@code old} is the target's layout and {@code new} the view's tab group: {@code WEST} gives {@code Split(HORIZONTAL,
 * proportion, new, old)}; {@code EAST}, {@code Split(HORIZONTAL, 1 - proportion, old, new)}; {@code NORTH},
 * {@code Split(VERTICAL, proportion, new, old)}; {@code SOUTH}, {@code Split(VERTICAL, 1 - proportion, old, new)}.
 */
public sealed interface DockLayout {

    /** The direction of a split. */
    enum Orientation {
        /** {@code first} left of {@code second}. */
        HORIZONTAL,
        /** {@code first} above {@code second}. */
        VERTICAL
    }

    /**
     * A split of the space in two.
     *
     * @param orientation where {@code first} is: left of {@code second}, or above it
     * @param proportion {@code first}'s share of the space, strictly between 0 and 1
     * @param first the first part
     * @param second the second part
     */
    record Split(Orientation orientation, double proportion, DockLayout first, DockLayout second)
            implements DockLayout {

        /**
         * Checks the split.
         *
         * @param orientation where {@code first} is: left of {@code second}, or above it
         * @param proportion {@code first}'s share of the space, strictly between 0 and 1
         * @param first the first part
         * @param second the second part
         * @throws NullPointerException if an argument is {@code null}
         * @throws IllegalArgumentException if {@code proportion} is not strictly between 0 and 1
         */
        public Split {
            Objects.requireNonNull(orientation, "orientation");
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (!(proportion > 0 && proportion < 1)) {
                throw new IllegalArgumentException("The proportion " + proportion + " is not strictly between 0 and 1");
            }
        }
    }

    /**
     * A tab group.
     *
     * @param views the views of its tabs, in tab order, at least one; the list is copied, unmodifiable
     * @param selected its selected view, one of them
     */
    record Tabs(List<DockView> views, DockView selected) implements DockLayout {

        /**
         * Checks and copies the tab group.
         *
         * @param views the views of its tabs, in tab order, at least one
         * @param selected its selected view, one of them
         * @throws NullPointerException if an argument or a view is {@code null}
         * @throws IllegalArgumentException if there is no view, or the selected view isn't one of them
         */
        public Tabs {
            views = List.copyOf(views);
            Objects.requireNonNull(selected, "selected");
            if (views.isEmpty()) {
                throw new IllegalArgumentException("A tab group has at least one view");
            }
            if (!views.contains(selected)) {
                throw new IllegalArgumentException("The selected view " + selected + " is not a view of the group");
            }
        }
    }
}
