package be.imgn.swing.ui.internal;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The saved state of a component of a view: a split pane's divider location, a tabbed pane's selected index, or a
 * table's columns.
 *
 * @param dividerLocation the divider location of a {@code JSplitPane}
 * @param selectedIndex the selected index of a {@code JTabbedPane}
 * @param columns the columns of a {@code JTable}, in view order
 */
record SavedComponent(
        @Nullable Integer dividerLocation,
        @Nullable Integer selectedIndex,
        @Nullable List<SavedColumn> columns) {}
