package be.imgn.swing.ui;

import be.imgn.swing.ListChange;
import javax.swing.AbstractListModel;

/**
 * The model of a list that shows an observable list: it reads the feed's copy, and fires the event of each change
 * applied to it, once it observes the feed. A combo box's model extends it with the selected item.
 *
 * @param <E> the type of the elements
 */
class FeedListModel<E> extends AbstractListModel<E> implements ListFeed.Observer<E> {

    private static final long serialVersionUID = 1L;

    private final transient ListFeed<E> feed;

    FeedListModel(ListFeed<E> feed) {
        this.feed = feed;
    }

    ListFeed<E> feed() {
        return feed;
    }

    @Override
    public int getSize() {
        return feed.size();
    }

    @Override
    public E getElementAt(int index) {
        return feed.get(index);
    }

    @Override
    public void changed(ListChange<E> change) {
        switch (change) {
            case ListChange.Added<E> added ->
                fireIntervalAdded(
                        this, added.index(), added.index() + added.elements().size() - 1);
            case ListChange.Removed<E> removed ->
                fireIntervalRemoved(
                        this,
                        removed.index(),
                        removed.index() + removed.elements().size() - 1);
            case ListChange.Replaced<E> replaced -> {
                if (replaced.removed().size() == replaced.added().size()) {
                    fireContentsChanged(
                            this,
                            replaced.index(),
                            replaced.index() + replaced.added().size() - 1);
                } else {
                    fireContentsChanged(this, 0, feed.size() - 1);
                }
            }
        }
    }
}
