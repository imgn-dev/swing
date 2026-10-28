package be.imgn.swing;

import be.imgn.swing.internal.AtomicProperty;

/**
 * An observable value that can be set, from any thread. {@link #get()} on any thread sees the latest value.
 *
 * <p>Listeners are called synchronously, on the thread that calls {@link #set(Object)}, only when the value changes.
 * When two threads set the same property at once, listeners may see the values out of order; code for which the order
 * matters sets the property from one thread.
 *
 * @param <T> the type of the value
 */
public interface Property<T> extends Observable<T> {

    /**
     * Sets the value, and tells the listeners if it changed.
     *
     * @param value the new value
     */
    void set(T value);

    /**
     * Returns a new property.
     *
     * @param initial the initial value
     * @param <T> the type of the value
     * @return the property
     */
    static <T> Property<T> of(T initial) {
        return new AtomicProperty<>(initial);
    }
}
