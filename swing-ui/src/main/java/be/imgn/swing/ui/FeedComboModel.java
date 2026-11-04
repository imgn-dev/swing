package be.imgn.swing.ui;

import javax.swing.ComboBoxModel;
import org.jspecify.annotations.Nullable;

/**
 * The model of a combo box that shows an observable list: a {@link FeedListModel} with a selected item, which it keeps
 * with its index in the feed's copy, so that a {@link SelectionSync} tracks it through the list's changes.
 *
 * <p>The combo box sets the selected item when the user selects one; the sync, through {@link ComboSelectionView},
 * selects by index. Either way, the model tells its listener before it fires the event that tells the combo box, so
 * that the combo box's own item and action events see what the listener did.
 *
 * @param <E> the type of the elements
 */
final class FeedComboModel<E> extends FeedListModel<E> implements ComboBoxModel<E> {

    private static final long serialVersionUID = 1L;

    private transient @Nullable E selected;
    private int selectedIndex = -1;
    private transient Runnable listener = () -> {};

    FeedComboModel(ListFeed<E> feed) {
        super(feed);
    }

    /**
     * Sets what is told of each change of the selection, the sync's own included.
     *
     * @param listener the listener
     */
    void follow(Runnable listener) {
        this.listener = listener;
    }

    /**
     * Returns the index of the selected item in the copy.
     *
     * @return the index, -1 when nothing is selected
     */
    int selectedIndex() {
        return selectedIndex;
    }

    /**
     * Selects an element, or clears the selection.
     *
     * @param index its index in the copy, -1 to clear the selection
     */
    void select(int index) {
        selectedIndex = index;
        selected = index < 0 ? null : feed().get(index);
        listener.run();
        // Index -1 tells a combo box that its selected item changed.
        fireContentsChanged(this, -1, -1);
    }

    // The combo box hands the element equal to the item that the user selected, the first one, since it matches items
    // by equality: the selected element stays when it is equal, rather than an equal one before it.
    @Override
    public void setSelectedItem(@Nullable Object item) {
        if (item == null) {
            select(-1);
        } else if (selectedIndex < 0 || selectedIndex >= getSize() || !item.equals(feed().get(selectedIndex))) {
            int index = indexOf(item);
            if (index >= 0) {
                select(index);
            }
        }
    }

    private int indexOf(Object item) {
        for (int index = 0; index < getSize(); index++) {
            if (item.equals(feed().get(index))) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public @Nullable Object getSelectedItem() {
        return selected;
    }

    /**
     * Returns the element of the copy that a value rendered outside the list is: the selected item, or another element
     * that a combo box renders to measure its display area.
     *
     * @param value the value
     * @return the first element equal to the value, {@code null} for none
     */
    @Nullable
    E element(@Nullable Object value) {
        E chosen = selected;
        if (value != null && value.equals(chosen)) {
            return chosen;
        }
        int index = value == null ? -1 : indexOf(value);
        return index < 0 ? null : feed().get(index);
    }
}
