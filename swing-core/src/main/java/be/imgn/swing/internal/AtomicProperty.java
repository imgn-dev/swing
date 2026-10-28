package be.imgn.swing.internal;

import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A property holding its value in an {@link AtomicReference}.
 *
 * @param <T> the type of the value
 */
public final class AtomicProperty<T> implements Property<T> {

    private final AtomicReference<T> value;
    private final Listeners<T> listeners = new Listeners<>(() -> {}, () -> {});

    /**
     * Creates a property.
     *
     * @param initial the initial value
     */
    public AtomicProperty(T initial) {
        this.value = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    @Override
    public T get() {
        return Objects.requireNonNull(value.get());
    }

    @Override
    public void set(T newValue) {
        Objects.requireNonNull(newValue, "value");
        T old = value.getAndSet(newValue);
        if (!Objects.equals(old, newValue)) {
            listeners.fire(newValue);
        }
    }

    @Override
    public Subscription subscribe(Scope scope, Consumer<? super T> listener) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(listener, "listener");
        return listeners.subscribe(scope, listener);
    }

    @Override
    public String toString() {
        return "Property[" + value.get() + "]";
    }
}
