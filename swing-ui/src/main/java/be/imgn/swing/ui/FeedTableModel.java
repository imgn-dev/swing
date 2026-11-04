package be.imgn.swing.ui;

import be.imgn.swing.ListChange;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.jspecify.annotations.Nullable;

/**
 * The model of a table that shows an observable list, a row per element and a column per {@link Column}, whose model
 * index is its position: it reads the feed's copy, and fires the event of each change applied to it, once it observes
 * the feed. A committed edit replaces the row in the list, unless the list no longer holds the edited row at its index.
 *
 * @param <E> the type of the rows
 */
final class FeedTableModel<E> extends AbstractTableModel implements ListFeed.Observer<E> {

    private static final long serialVersionUID = 1L;

    private final transient ListFeed<E> feed;
    private final transient List<Column<E, ?>> columns;

    FeedTableModel(ListFeed<E> feed, List<Column<E, ?>> columns) {
        this.feed = feed;
        this.columns = columns;
    }

    @Override
    public int getRowCount() {
        return feed.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).header();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return columns.get(column).type();
    }

    @Override
    public @Nullable Object getValueAt(int row, int column) {
        return columns.get(column).value(feed.get(row));
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return columns.get(column).isEditable();
    }

    @Override
    public void setValueAt(@Nullable Object value, int row, int column) {
        // A default editor commits null for a number cell left empty, which the column's function doesn't take.
        if (value == null || !isCellEditable(row, column)) {
            return;
        }
        E edited = feed.get(row);
        E replacing = with(columns.get(column), edited, value);
        // The copy may lag behind the list, while its changes are queued: the edit is then of a row that has moved.
        // The list checks and sets under its lock, so that no other thread moves the row in between.
        feed.list().setIf(row, edited, replacing);
    }

    private static <E, V> E with(Column<E, V> column, E row, Object value) {
        return column.with(row, column.type().cast(value));
    }

    @Override
    public void changed(ListChange<E> change) {
        switch (change) {
            case ListChange.Added<E> added ->
                fireTableRowsInserted(
                        added.index(), added.index() + added.elements().size() - 1);
            case ListChange.Removed<E> removed ->
                fireTableRowsDeleted(
                        removed.index(), removed.index() + removed.elements().size() - 1);
            case ListChange.Replaced<E> replaced -> {
                if (replaced.removed().size() == replaced.added().size()) {
                    fireTableRowsUpdated(
                            replaced.index(),
                            replaced.index() + replaced.added().size() - 1);
                } else {
                    fireTableDataChanged();
                }
            }
        }
    }
}
