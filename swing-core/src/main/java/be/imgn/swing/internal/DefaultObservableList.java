package be.imgn.swing.internal;

import static java.util.Objects.checkIndex;

import be.imgn.swing.ListChange;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The observable list of {@link ObservableList#of(Object[])} and {@link ObservableList#copyOf(Collection)}.
 *
 * <p>Its lock is a {@link ReentrantLock} rather than a monitor, so that a virtual thread that waits for it, or that
 * calls the listeners under it, doesn't pin its carrier.
 *
 * @param <E> the type of the elements
 */
public final class DefaultObservableList<E> implements ObservableList<E> {

    // Whether the current thread is calling the listeners of an observable list, any of them.
    private static final ThreadLocal<Boolean> DELIVERING = ThreadLocal.withInitial(() -> false);
    // The actions that the listeners called on the current thread gave, to run once the list releases its lock.
    private static final ThreadLocal<@Nullable List<Runnable>> AFTER_DELIVERY = new ThreadLocal<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final List<E> elements;
    private final Listeners<ListChange<E>> listeners = new Listeners<>(() -> {}, () -> {});
    private @Nullable List<E> snapshot;

    /**
     * Creates a list.
     *
     * @param elements the initial elements, none of them {@code null}
     */
    public DefaultObservableList(List<E> elements) {
        this.elements = new ArrayList<>(elements);
    }

    /**
     * Returns an unmodifiable copy of a collection of elements.
     *
     * @param elements the elements
     * @param <E> the type of the elements
     * @return the copy
     * @throws NullPointerException if an element is {@code null}
     */
    public static <E> List<E> copy(Collection<? extends E> elements) {
        Objects.requireNonNull(elements, "elements");
        var copy = new ArrayList<E>(elements.size());
        for (E element : elements) {
            copy.add(Objects.requireNonNull(element, "element"));
        }
        return List.copyOf(copy);
    }

    /**
     * Returns whether the current thread is calling the listeners of an observable list, any of them: what runs then
     * holds the list's lock, and can't modify an observable list.
     *
     * @return whether a list's change is being delivered on the current thread
     */
    public static boolean isDelivering() {
        return DELIVERING.get();
    }

    /**
     * Runs an action on the current thread at once, or, if the current thread is calling the listeners of an observable
     * list, once that list's modification has released its lock, before the modification returns: so that a listener
     * can have work that must not hold the lock, such as code that modifies an observable list, done synchronously. The
     * actions given during a modification run in the order they were given, each even if an earlier one threw; the
     * modification then throws what the first one threw, with what the others threw suppressed.
     *
     * @param action the action
     */
    public static void afterDelivery(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (!DELIVERING.get()) {
            action.run();
            return;
        }
        List<Runnable> after = AFTER_DELIVERY.get();
        if (after == null) {
            after = new ArrayList<>();
            AFTER_DELIVERY.set(after);
        }
        after.add(action);
    }

    /**
     * Takes a copy of the elements and subscribes a listener to the changes, under the list's lock, so that no change
     * falls between them or reaches the listener before the copy is taken.
     *
     * @param scope the scope the subscription belongs to
     * @param listener the listener, called with each change after the copy
     * @return the copy and the subscription
     * @throws IllegalStateException if the scope is closed
     */
    public CopyAndSubscription<E> copyAndSubscribeChanges(Scope scope, Consumer<? super ListChange<E>> listener) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(listener, "listener");
        lock.lock();
        try {
            return new CopyAndSubscription<>(get(), listeners.subscribe(scope, listener));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<E> get() {
        lock.lock();
        try {
            var current = snapshot;
            if (current == null) {
                current = List.copyOf(elements);
                snapshot = current;
            }
            return current;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E get(int index) {
        lock.lock();
        try {
            checkIndex(index, elements.size());
            return elements.get(index);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return elements.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void add(E element) {
        Objects.requireNonNull(element, "element");
        checkNotDelivering();
        lock.lock();
        try {
            int index = elements.size();
            elements.add(element);
            changed(new ListChange.Added<>(index, List.of(element)));
        } finally {
            release();
        }
    }

    @Override
    public void add(int index, E element) {
        Objects.requireNonNull(element, "element");
        checkNotDelivering();
        lock.lock();
        try {
            checkIndex(index, elements.size() + 1);
            elements.add(index, element);
            changed(new ListChange.Added<>(index, List.of(element)));
        } finally {
            release();
        }
    }

    @Override
    public void addAll(Collection<? extends E> added) {
        List<E> copy = copy(added);
        checkNotDelivering();
        if (copy.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            int index = elements.size();
            elements.addAll(copy);
            changed(new ListChange.Added<>(index, copy));
        } finally {
            release();
        }
    }

    @Override
    public E set(int index, E element) {
        Objects.requireNonNull(element, "element");
        checkNotDelivering();
        lock.lock();
        try {
            checkIndex(index, elements.size());
            E previous = elements.get(index);
            elements.set(index, element);
            if (previous.equals(element)) {
                // An equal element makes no change, but the list holds the instance it is given.
                snapshot = null;
            } else {
                changed(new ListChange.Replaced<>(index, List.of(previous), List.of(element)));
            }
            return previous;
        } finally {
            release();
        }
    }

    /**
     * Replaces an element, as {@link #set(int, Object)} does, only if the list still holds an expected element at that
     * index: checked and set under the list's lock, so that no other thread changes the list in between.
     *
     * @param index the index, which may no longer be in range
     * @param expected the element the list must hold at that index
     * @param element the new element
     * @return whether the list held the expected element, and so was set
     * @throws NullPointerException if {@code element} is {@code null}
     * @throws IllegalStateException if called by a listener of an observable list
     */
    public boolean setIf(int index, E expected, E element) {
        Objects.requireNonNull(element, "element");
        checkNotDelivering();
        lock.lock();
        try {
            if (index < 0 || index >= elements.size() || !elements.get(index).equals(expected)) {
                return false;
            }
            set(index, element);
            return true;
        } finally {
            release();
        }
    }

    @Override
    public E remove(int index) {
        checkNotDelivering();
        lock.lock();
        try {
            checkIndex(index, elements.size());
            E removed = elements.remove(index);
            changed(new ListChange.Removed<>(index, List.of(removed)));
            return removed;
        } finally {
            release();
        }
    }

    @Override
    public boolean remove(E element) {
        Objects.requireNonNull(element, "element");
        checkNotDelivering();
        lock.lock();
        try {
            int index = elements.indexOf(element);
            if (index < 0) {
                return false;
            }
            E removed = elements.remove(index);
            changed(new ListChange.Removed<>(index, List.of(removed)));
            return true;
        } finally {
            release();
        }
    }

    @Override
    public void setAll(Collection<? extends E> replacing) {
        List<E> copy = copy(replacing);
        checkNotDelivering();
        lock.lock();
        try {
            var previous = get();
            elements.clear();
            elements.addAll(copy);
            if (previous.equals(copy)) {
                // Equal elements make no change, but the list holds the instances it is given.
                snapshot = null;
            } else if (previous.isEmpty()) {
                changed(new ListChange.Added<>(0, copy));
            } else if (copy.isEmpty()) {
                changed(new ListChange.Removed<>(0, previous));
            } else {
                changed(new ListChange.Replaced<>(0, previous, copy));
            }
        } finally {
            release();
        }
    }

    @Override
    public void clear() {
        checkNotDelivering();
        lock.lock();
        try {
            if (elements.isEmpty()) {
                return;
            }
            var previous = get();
            elements.clear();
            changed(new ListChange.Removed<>(0, previous));
        } finally {
            release();
        }
    }

    @Override
    public Subscription subscribe(Scope scope, Consumer<? super List<E>> listener) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(listener, "listener");
        return subscribeUnderLock(scope, change -> listener.accept(get()));
    }

    @Override
    public Subscription subscribeChanges(Scope scope, Consumer<? super ListChange<E>> listener) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(listener, "listener");
        return subscribeUnderLock(scope, listener);
    }

    private Subscription subscribeUnderLock(Scope scope, Consumer<? super ListChange<E>> listener) {
        lock.lock();
        try {
            return listeners.subscribe(scope, listener);
        } finally {
            lock.unlock();
        }
    }

    // Called under the lock, once the elements are changed.
    private void changed(ListChange<E> change) {
        snapshot = null;
        DELIVERING.set(true);
        try {
            listeners.fireAll(change);
        } finally {
            DELIVERING.set(false);
        }
    }

    // Releases the lock that a modification took, then, once the outermost modification of the thread has released
    // it, such as setIf's rather than the set it calls, runs the actions given after delivery.
    private void release() {
        lock.unlock();
        if (!lock.isHeldByCurrentThread()) {
            runAfterDelivery();
        }
    }

    // Takes the actions before running them, so that a modification an action makes runs only those its own
    // listeners give.
    private static void runAfterDelivery() {
        List<Runnable> after = AFTER_DELIVERY.get();
        if (after == null) {
            return;
        }
        AFTER_DELIVERY.remove();
        Throwable first = null;
        for (Runnable action : after) {
            Throwable thrown = Guarded.run(action);
            if (thrown == null) {
                continue;
            }
            if (first == null) {
                first = thrown;
            } else if (!thrown.equals(first)) {
                first.addSuppressed(thrown);
            }
        }
        if (first instanceof RuntimeException exception) {
            throw exception;
        }
        if (first instanceof Error error) {
            throw error;
        }
        if (first != null) {
            throw new UndeclaredThrowableException(first);
        }
    }

    private static void checkNotDelivering() {
        if (DELIVERING.get()) {
            throw new IllegalStateException("A listener of an observable list cannot modify an observable list.");
        }
    }

    @Override
    public String toString() {
        return "ObservableList" + get();
    }

    /**
     * A copy of a list's elements, and a subscription to the changes made after it.
     *
     * @param elements the elements, unmodifiable
     * @param subscription the subscription
     * @param <E> the type of the elements
     */
    public record CopyAndSubscription<E>(List<E> elements, Subscription subscription) {}
}
