package be.imgn.swing.ui;

import be.imgn.swing.ListChange;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Scope;
import be.imgn.swing.internal.DefaultObservableList;
import be.imgn.swing.internal.Guarded;
import be.imgn.swing.ui.internal.EdtDelivery;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The copy, on the EDT, of an observable list that a component shows, and the delivery of the list's changes to it.
 *
 * <p>The copy is taken and the listener subscribed under the list's lock, in one call, so that no change falls between
 * them or is applied twice. The listener, on the thread that modified the list, touches no component: it adds the
 * change to a queue and, if no update is pending, queues one among the framework's updates, so that a view's
 * {@code Ui.bind} targets see the component's rows already following the change. The update, on the EDT, applies every
 * queued change in order to the copy, and tells the observers of each; it does nothing once the scope is closed.
 *
 * <p>Models read the copy, never the list, so that the indices they report always match the events they fired.
 *
 * @param <E> the type of the elements
 */
final class ListFeed<E> {

    private final DefaultObservableList<E> list;
    private final Scope scope;
    private final List<E> copy;
    private final Queue<ListChange<E>> queued = new ConcurrentLinkedQueue<>();
    private final List<Observer<E>> observers = new ArrayList<>();
    private final Runnable update;

    private ListFeed(DefaultObservableList<E> list, Scope scope, List<E> copy) {
        this.list = list;
        this.scope = scope;
        this.copy = copy;
        this.update = EdtDelivery.updater(scope, this::apply, EdtDelivery.Order.FRAMEWORK);
    }

    /**
     * Takes the copy of a list and subscribes to its changes, on the EDT.
     *
     * @param list the list
     * @param scope the scope the subscription belongs to
     * @param <E> the type of the elements
     * @return the feed
     */
    static <E> ListFeed<E> of(ObservableList<E> list, Scope scope) {
        // The interface is sealed: core's implementation, which takes a copy and subscribes atomically, is the only
        // one.
        var core = (DefaultObservableList<E>) list;
        List<E> copy = new ArrayList<>();
        ListFeed<E> feed = new ListFeed<>(core, scope, copy);
        DefaultObservableList.CopyAndSubscription<E> taken = core.copyAndSubscribeChanges(scope, change -> {
            feed.queued.add(change);
            feed.update.run();
        });
        copy.addAll(taken.elements());
        return feed;
    }

    /**
     * Adds an observer, told of the changes in the order observers were added.
     *
     * @param observer the observer
     */
    void observe(Observer<E> observer) {
        observers.add(observer);
    }

    /**
     * Returns the list that the copy follows.
     *
     * @return the list
     */
    DefaultObservableList<E> list() {
        return list;
    }

    /**
     * Returns the number of elements of the copy.
     *
     * @return the size
     */
    int size() {
        return copy.size();
    }

    /**
     * Returns an element of the copy.
     *
     * @param index the index
     * @return the element
     */
    E get(int index) {
        return copy.get(index);
    }

    /**
     * Applies the queued changes to the copy, in order, on the EDT, unless the scope is closed. A selection's update
     * calls it before it reads the copy, so that an element added then selected is found. Should an observer throw, the
     * other observers are still told of that change, the first exception is thrown once every observer is told the
     * batch is over, and the changes still queued are applied by an update queued again, in the next drain.
     */
    void apply() {
        if (scope.isClosed()) {
            return;
        }
        ListChange<E> change = queued.poll();
        if (change == null) {
            return;
        }
        observers.forEach(Observer::beforeChanges);
        try {
            for (; change != null; change = queued.poll()) {
                applyToCopy(change);
                tellObservers(change);
            }
        } finally {
            try {
                observers.forEach(Observer::afterChanges);
            } finally {
                // Otherwise they would wait for the next change of the list.
                if (!queued.isEmpty()) {
                    update.run();
                }
            }
        }
    }

    // Each observer is told of the change even when one before it threw, so that none misses a change applied to the
    // copy: a selection would otherwise track its index through the wrong changes.
    private void tellObservers(ListChange<E> change) {
        Throwable failure = null;
        for (var observer : observers) {
            var thrown = Guarded.run(() -> observer.changed(change));
            if (failure == null) {
                failure = thrown;
            }
        }
        if (failure instanceof RuntimeException e) {
            throw e;
        }
        if (failure instanceof Error e) {
            throw e;
        }
        if (failure != null) {
            // A checked exception that an observer threw without declaring it.
            throw new IllegalStateException(failure);
        }
    }

    private void applyToCopy(ListChange<E> change) {
        switch (change) {
            case ListChange.Added<E> added -> copy.addAll(added.index(), added.elements());
            case ListChange.Removed<E> removed ->
                copy.subList(
                                removed.index(),
                                removed.index() + removed.elements().size())
                        .clear();
            case ListChange.Replaced<E> replaced -> {
                List<E> range = copy.subList(
                        replaced.index(), replaced.index() + replaced.removed().size());
                range.clear();
                range.addAll(replaced.added());
            }
        }
    }

    /**
     * What follows the changes applied to the copy, on the EDT.
     *
     * @param <E> the type of the elements
     */
    interface Observer<E> {

        /** Called before a batch of changes is applied. */
        default void beforeChanges() {}

        /**
         * Called once a change is applied to the copy.
         *
         * @param change the change
         */
        void changed(ListChange<E> change);

        /** Called once every change of the batch is applied, even when an observer threw. */
        default void afterChanges() {}
    }
}
