package be.imgn.swing.ui;

import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import org.jspecify.annotations.Nullable;

/** Binds lists and tables to observable lists, with a single selection bound to a property, for {@link Ui}. */
final class ListBindings {

    // The client property that marks a component bound, so that binding it again fails.
    private static final Object BOUND = new Object();

    private ListBindings() {}

    /**
     * Fails if a component is already bound.
     *
     * @param component the component
     * @throws IllegalStateException if it is
     */
    static void checkNotBound(JComponent component) {
        if (component.getClientProperty(BOUND) != null) {
            throw new IllegalStateException("The " + component.getClass().getName() + " is already bound");
        }
    }

    /**
     * Marks a component bound.
     *
     * @param component the component
     */
    static void markBound(JComponent component) {
        component.putClientProperty(BOUND, true);
    }

    static <E> void bind(
            Scope scope,
            JList<E> list,
            ObservableList<E> items,
            Function<? super E, String> text,
            @Nullable Property<Optional<E>> selection,
            @Nullable Function<? super E, ?> key) {
        checkNotBound(list);
        ListFeed<E> feed = ListFeed.of(items, scope);
        markBound(list);
        FeedListModel<E> model = new FeedListModel<>(feed);
        feed.observe(model);
        list.setModel(model);
        list.setCellRenderer(new TextRenderer<>(text, TextRenderer.of(feed)));
        if (selection != null) {
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            ListSelectionView view = new ListSelectionView(list.getSelectionModel(), list::ensureIndexIsVisible);
            view.follow(SelectionSync.bind(feed, view, selection, key, scope));
        }
    }

    static <E> void bind(
            Scope scope,
            JTable table,
            ObservableList<E> rows,
            List<Column<E, ?>> columns,
            @Nullable Property<Optional<E>> selection,
            @Nullable Function<? super E, ?> key) {
        checkNotBound(table);
        ListFeed<E> feed = ListFeed.of(rows, scope);
        markBound(table);
        FeedTableModel<E> model = new FeedTableModel<>(feed, columns);
        feed.observe(model);
        // Before the model is set, which would otherwise create a sorter.
        table.setAutoCreateRowSorter(false);
        table.setModel(model);
        // A table that doesn't create its columns from its model would otherwise keep those of its previous one.
        table.createDefaultColumnsFromModel();
        table.setRowSorter(null);
        TableColumnModel columnModel = table.getColumnModel();
        EmptyWhenNull renderer = new EmptyWhenNull();
        for (int index = 0; index < columnModel.getColumnCount(); index++) {
            columnModel.getColumn(index).setCellRenderer(renderer);
        }
        if (selection != null) {
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            ListSelectionView view = new ListSelectionView(
                    table.getSelectionModel(), index -> table.scrollRectToVisible(table.getCellRect(index, 0, true)));
            view.follow(SelectionSync.bind(feed, view, selection, key, scope));
        }
    }

    // Renders a value with the table's default renderer of its column's class, and null as an empty cell, which the
    // default renderer of Boolean would show as an unchecked box.
    private static final class EmptyWhenNull implements TableCellRenderer {

        private final DefaultTableCellRenderer empty = new DefaultTableCellRenderer();

        @Override
        public Component getTableCellRendererComponent(
                JTable table, @Nullable Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            TableCellRenderer renderer = value == null ? empty : table.getDefaultRenderer(table.getColumnClass(column));
            return renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }
}
