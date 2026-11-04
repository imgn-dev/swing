package be.imgn.swing;

import be.imgn.swing.internal.DefaultObservableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * A list that tells its listeners each change, so that a component showing it updates one row rather than all of them.
 *
 * <p>{@link #get()} returns an unmodifiable snapshot of the elements, which never changes afterwards; {@link #get(int)}
 * and {@link #size()} read the current elements. Elements are never {@code null}.
 *
 * <p>Each method that modifies the list makes one {@link ListChange}: {@code add} and {@code addAll} give
 * {@link ListChange.Added}; {@code remove}, and {@code clear} on a list that isn't empty, give
 * {@link ListChange.Removed}; {@code set} gives {@link ListChange.Replaced} of one element; {@code setAll} gives
 * {@code Replaced(0, previous, new)}, or {@code Added} when the list was empty, or {@code Removed} when the new
 * elements are none. A modification that leaves the list equal ({@link List#equals(Object)}) makes no change and calls
 * no listener; {@code set} and {@code setAll} still store the elements they are given, so that the list holds those
 * instances.
 *
 * <p>A list is thread-safe. Modifications are serialized by a lock that each one holds while it changes the elements
 * and calls the listeners, so listeners see the changes in the order they were made, each with indices that are valid
 * for the list just before it. Reads take the lock too. Listeners, of {@link #subscribeChanges(Scope, Consumer)} and of
 * {@link #subscribe(Scope, Consumer)} alike, are called synchronously on the thread that modifies the list, while it
 * holds the lock, in the order they subscribed: they must be quick. A listener that modifies an observable list, the
 * one it listens to or any other, throws {@link IllegalStateException}: its change would reach the other listeners
 * before the one being delivered, and two lists whose listeners modify each other from two threads would deadlock.
 *
 * <p>{@link #subscribe(Scope, Consumer)} listeners receive the new snapshot, and
 * {@link #map(java.util.function.Function)} and {@link Observable#combine(Observable, Observable,
 * java.util.function.BiFunction)} work on lists as on any observable.
 *
 * <p>Only core implements observable lists: the interface is sealed, and {@link #of(Object...)} and
 * {@link #copyOf(Collection)} make them.
 *
 * @param <E> the type of the elements
 */
public sealed interface ObservableList<E> extends Observable<List<E>> permits DefaultObservableList {

    /**
     * Returns a new list of elements.
     *
     * @param elements the elements
     * @param <E> the type of the elements
     * @return the list
     * @throws NullPointerException if an element is {@code null}
     */
    @SafeVarargs
    static <E> ObservableList<E> of(E... elements) {
        // Read element by element: passing the array on is an unsafe use of a generic varargs parameter for javac.
        var list = new ArrayList<E>(elements.length);
        for (E element : elements) {
            list.add(element);
        }
        return copyOf(list);
    }

    /**
     * Returns a new list of the elements of a collection, in its iteration order.
     *
     * @param elements the elements
     * @param <E> the type of the elements
     * @return the list
     * @throws NullPointerException if an element is {@code null}
     */
    static <E> ObservableList<E> copyOf(Collection<? extends E> elements) {
        return new DefaultObservableList<>(DefaultObservableList.copy(elements));
    }

    /**
     * Returns the number of elements.
     *
     * @return the number of elements
     */
    int size();

    /**
     * Returns the element at an index.
     *
     * @param index the index
     * @return the element
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    E get(int index);

    /**
     * Adds an element at the end.
     *
     * @param element the element
     * @throws NullPointerException if the element is {@code null}
     * @throws IllegalStateException if called by a listener of an observable list
     */
    void add(E element);

    /**
     * Inserts an element at an index.
     *
     * @param index the index, from 0 to {@link #size()}
     * @param element the element
     * @throws NullPointerException if the element is {@code null}
     * @throws IndexOutOfBoundsException if the index is out of range
     * @throws IllegalStateException if called by a listener of an observable list
     */
    void add(int index, E element);

    /**
     * Adds elements at the end, in the collection's iteration order.
     *
     * @param elements the elements
     * @throws NullPointerException if an element is {@code null}
     * @throws IllegalStateException if called by a listener of an observable list
     */
    void addAll(Collection<? extends E> elements);

    /**
     * Replaces the element at an index.
     *
     * @param index the index
     * @param element the new element
     * @return the previous element
     * @throws NullPointerException if the element is {@code null}
     * @throws IndexOutOfBoundsException if the index is out of range
     * @throws IllegalStateException if called by a listener of an observable list
     */
    E set(int index, E element);

    /**
     * Removes the element at an index.
     *
     * @param index the index
     * @return the removed element
     * @throws IndexOutOfBoundsException if the index is out of range
     * @throws IllegalStateException if called by a listener of an observable list
     */
    E remove(int index);

    /**
     * Removes the first element equal to the one given, if any.
     *
     * @param element the element
     * @return whether an element was removed
     * @throws NullPointerException if the element is {@code null}
     * @throws IllegalStateException if called by a listener of an observable list
     */
    boolean remove(E element);

    /**
     * Replaces all the elements, by those of a collection in its iteration order.
     *
     * @param elements the new elements
     * @throws NullPointerException if an element is {@code null}
     * @throws IllegalStateException if called by a listener of an observable list
     */
    void setAll(Collection<? extends E> elements);

    /**
     * Removes all the elements.
     *
     * @throws IllegalStateException if called by a listener of an observable list
     */
    void clear();

    /**
     * Subscribes a listener, called with each change, until the scope closes or the subscription is closed.
     *
     * @param scope the scope the subscription belongs to
     * @param listener the listener
     * @return the subscription
     * @throws IllegalStateException if the scope is closed
     */
    Subscription subscribeChanges(Scope scope, Consumer<? super ListChange<E>> listener);
}
