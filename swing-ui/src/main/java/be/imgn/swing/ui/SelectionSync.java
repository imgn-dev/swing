package be.imgn.swing.ui;

import be.imgn.swing.ListChange;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.ui.internal.EdtDelivery;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the single selection of a component that shows a feed's copy and a property in step, on the EDT.
 *
 * <ul>
 *   <li>A user's change of the selection, once it is no longer adjusting, sets the property: to the selected element,
 *       or to {@code Optional.empty()} when the selection is empty. The selection events that the sync's own updates
 *       cause are not the user's, and set nothing.
 *   <li>A change of the property, in its update, first applies the list's queued changes, then selects the first
 *       element that matches the value and scrolls to it; an empty value clears the selection, and a value that no
 *       element matches clears it and sets the property to {@code Optional.empty()}.
 *   <li>Changes of the list keep the selection on the selected element: without a key, by tracking its index through
 *       each change; with one, by finding the element with its key. When neither finds it, the selection moves to the
 *       first element that matches the property's value, or is cleared, with the property set to empty.
 * </ul>
 *
 * <p>An element matches a value when it is equal to it, or, with a key, when their keys are equal
 * ({@link Objects#equals(Object, Object)}).
 *
 * @param <E> the type of the elements
 */
final class SelectionSync<E> implements ListFeed.Observer<E> {

    private static final int NONE = -1;
    private static final int LOST = -2;

    private final ListFeed<E> feed;
    private final SelectionView view;
    private final Property<Optional<E>> property;
    private final @Nullable Function<? super E, ?> key;
    private final Scope scope;
    private boolean updating;
    private int tracked = NONE;
    private @Nullable E selected;
    private boolean inStep;

    private SelectionSync(
            ListFeed<E> feed,
            SelectionView view,
            Property<Optional<E>> property,
            @Nullable Function<? super E, ?> key,
            Scope scope) {
        this.feed = feed;
        this.view = view;
        this.property = property;
        this.key = key;
        this.scope = scope;
    }

    /**
     * Keeps a selection and a property in step: selects the property's value at once, then follows the property's
     * changes, in updates among the framework's, and the feed's changes. The component's listener calls
     * {@link #userChanged()}.
     *
     * @param feed the feed the component shows, which the component's model already observes
     * @param view the component's selection
     * @param property the property
     * @param key the key elements are matched by, {@code null} to match them by equality
     * @param scope the scope the subscription to the property belongs to
     * @param <E> the type of the elements
     * @return the sync
     */
    static <E> SelectionSync<E> bind(
            ListFeed<E> feed,
            SelectionView view,
            Property<Optional<E>> property,
            @Nullable Function<? super E, ?> key,
            Scope scope) {
        SelectionSync<E> sync = new SelectionSync<>(feed, view, property, key, scope);
        feed.observe(sync);
        sync.propertyChanged();
        EdtDelivery.subscribe(scope, property, value -> sync.propertyChanged(), EdtDelivery.Order.FRAMEWORK);
        return sync;
    }

    /**
     * Sets the property from the component's selection, unless the change is the sync's own.
     *
     * @return whether the change was the user's, and so set the property
     */
    boolean userChanged() {
        if (updating || scope.isClosed()) {
            return false;
        }
        int index = view.selectedIndex();
        property.set(index >= 0 && index < feed.size() ? Optional.of(feed.get(index)) : Optional.empty());
        return true;
    }

    private void propertyChanged() {
        feed.apply();
        Optional<E> value = property.get();
        boolean found = true;
        updating = true;
        try {
            if (value.isEmpty()) {
                view.clear();
            } else {
                int current = view.selectedIndex();
                // The element the user selected stays selected, rather than an equal one before it.
                int index = current >= 0 && current < feed.size() && matches(feed.get(current), value.get())
                        ? current
                        : indexOf(value.get());
                if (index < 0) {
                    view.clear();
                    found = false;
                } else {
                    view.select(index);
                    view.scrollTo(index);
                }
            }
        } finally {
            updating = false;
        }
        if (!found) {
            property.set(Optional.empty());
        }
    }

    @Override
    public void beforeChanges() {
        updating = true;
        int index = view.selectedIndex();
        if (index >= 0 && index < feed.size()) {
            tracked = index;
            E element = feed.get(index);
            selected = element;
            Optional<E> value = property.get();
            // Otherwise the property changed since the selection showed it, and its own update, queued, applies it.
            inStep = value.isPresent() && matches(element, value.get());
        } else {
            tracked = NONE;
            selected = null;
            inStep = false;
        }
    }

    @Override
    public void changed(ListChange<E> change) {
        // With a key, the element is found by its key once the changes are applied, wherever it moved.
        if (key != null || tracked < 0) {
            return;
        }
        switch (change) {
            case ListChange.Added<E> added ->
                track(added.index(), 0, added.elements().size());
            case ListChange.Removed<E> removed ->
                track(removed.index(), removed.elements().size(), 0);
            case ListChange.Replaced<E> replaced ->
                track(
                        replaced.index(),
                        replaced.removed().size(),
                        replaced.added().size());
        }
    }

    // A change before the tracked index shifts it; one that removes it loses it, unless it replaces that one element
    // by one element, which keeps it.
    private void track(int index, int removed, int added) {
        if (index + removed <= tracked) {
            tracked += added - removed;
        } else if (index <= tracked && (removed != 1 || added != 1)) {
            tracked = LOST;
        }
    }

    @Override
    public void afterChanges() {
        E before = selected;
        selected = null;
        Optional<E> setting = Optional.empty();
        try {
            if (before == null) {
                return;
            }
            int index = keptIndex(before);
            if (index < 0) {
                Optional<E> value = property.get();
                index = value.isPresent() ? indexOf(value.get()) : NONE;
            }
            if (index >= 0) {
                view.select(index);
                setting = Optional.of(feed.get(index));
            } else {
                view.clear();
            }
        } finally {
            updating = false;
            tracked = NONE;
        }
        // A property set to an equal value calls no listener.
        if (before != null && inStep) {
            property.set(setting);
        }
    }

    // The index of the element that keeps the selection: the tracked one, or, with a key, the one with its key.
    private int keptIndex(E before) {
        Function<? super E, ?> by = key;
        if (by == null) {
            return tracked;
        }
        @Nullable Object wanted = by.apply(before);
        for (int index = 0; index < feed.size(); index++) {
            if (Objects.equals(by.apply(feed.get(index)), wanted)) {
                return index;
            }
        }
        return NONE;
    }

    private int indexOf(E value) {
        for (int index = 0; index < feed.size(); index++) {
            if (matches(feed.get(index), value)) {
                return index;
            }
        }
        return NONE;
    }

    private boolean matches(E element, E value) {
        Function<? super E, ?> by = key;
        return by == null ? element.equals(value) : Objects.equals(by.apply(element), by.apply(value));
    }
}
