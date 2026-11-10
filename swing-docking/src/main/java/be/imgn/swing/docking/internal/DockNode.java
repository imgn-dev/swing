package be.imgn.swing.docking.internal;

import be.imgn.swing.docking.DockLayout;
import java.util.List;

/** The arrangement of the entries of a surface, as {@link DockLayout} arranges views. */
public sealed interface DockNode {

    /**
     * A split of the space in two.
     *
     * @param orientation where {@code first} is
     * @param proportion {@code first}'s share of the space
     * @param first the first part
     * @param second the second part
     */
    record Split(DockLayout.Orientation orientation, double proportion, DockNode first, DockNode second)
            implements DockNode {}

    /**
     * A tab group.
     *
     * @param entries its entries, in tab order, at least one
     * @param selected its selected entry, one of them
     */
    record Tabs(List<DockEntry> entries, DockEntry selected) implements DockNode {

        /**
         * Copies the entries.
         *
         * @param entries its entries, in tab order, at least one
         * @param selected its selected entry, one of them
         */
        public Tabs {
            entries = List.copyOf(entries);
        }
    }
}
