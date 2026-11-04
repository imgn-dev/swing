package be.imgn.swing.ui;

import java.util.function.IntConsumer;
import javax.swing.ListSelectionModel;

/** The single selection of a list or a table, in its selection model, whose indices are those of the copy. */
final class ListSelectionView implements SelectionView {

    private final ListSelectionModel model;
    private final IntConsumer scroll;

    /**
     * Creates the view of a selection model.
     *
     * @param model the selection model
     * @param scroll scrolls the component to an index
     */
    ListSelectionView(ListSelectionModel model, IntConsumer scroll) {
        this.model = model;
        this.scroll = scroll;
    }

    /**
     * Tells a sync of the user's changes of the selection, once they are no longer adjusting.
     *
     * @param sync the sync
     */
    void follow(SelectionSync<?> sync) {
        model.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                sync.userChanged();
            }
        });
    }

    @Override
    public int selectedIndex() {
        return model.getMinSelectionIndex();
    }

    @Override
    public void select(int index) {
        model.setSelectionInterval(index, index);
    }

    @Override
    public void scrollTo(int index) {
        scroll.accept(index);
    }

    @Override
    public void clear() {
        model.clearSelection();
    }
}
