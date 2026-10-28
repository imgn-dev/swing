package be.imgn.swing.ui.internal;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The saved state of a component of a view: a split pane's divider location, a tabbed pane's selected index, or a
 * table's columns.
 *
 * @param dividerLocation the divider location of a {@code JSplitPane}
 * @param selectedIndex the selected index of a {@code JTabbedPane}
 * @param columns the columns of a {@code JTable}, in view order
 */
public record SavedComponent(OptionalInt dividerLocation, OptionalInt selectedIndex, Optional<List<Column>> columns) {

    /**
     * Creates the state of a component.
     *
     * @param dividerLocation the divider location of a {@code JSplitPane}
     * @param selectedIndex the selected index of a {@code JTabbedPane}
     * @param columns the columns of a {@code JTable}, in view order; copied
     */
    public SavedComponent {
        columns = columns.map(List::copyOf);
    }

    /**
     * A column of a table.
     *
     * @param modelIndex the index of the column in the model
     * @param width the width of the column
     */
    public record Column(int modelIndex, int width) {}
}
