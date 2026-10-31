package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.spi.ComponentInjector;
import be.imgn.swing.ui.spi.ComponentState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/** The generated injectors and state accessors of the view classes, found through the {@link ServiceLoader}. */
@Singleton
public final class ViewSupports {

    private final Map<Class<?>, ComponentInjector<?>> injectors = new HashMap<>();
    private final Map<Class<?>, ComponentState<?>> states = new HashMap<>();

    @Inject
    @SuppressWarnings("rawtypes")
    ViewSupports() {
        for (ComponentInjector injector : ServiceLoader.load(ComponentInjector.class)) {
            injectors.putIfAbsent(injector.type(), injector);
        }
        for (ComponentState state : ServiceLoader.load(ComponentState.class)) {
            states.putIfAbsent(state.type(), state);
        }
    }

    /**
     * Returns the injector of a view class.
     *
     * @param type the view class
     * @param <V> the view class
     * @return the injector, if generated
     */
    @SuppressWarnings("unchecked")
    public <V> Optional<ComponentInjector<V>> injectorOf(Class<V> type) {
        return Optional.ofNullable((ComponentInjector<V>) injectors.get(type));
    }

    /**
     * Returns the state accessor of a view class.
     *
     * @param type the view class
     * @param <V> the view class
     * @return the accessor, if generated
     */
    @SuppressWarnings("unchecked")
    public <V> Optional<ComponentState<V>> stateOf(Class<V> type) {
        return Optional.ofNullable((ComponentState<V>) states.get(type));
    }
}
