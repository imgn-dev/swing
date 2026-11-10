package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.spi.ComponentInjector;
import be.imgn.swing.ui.spi.ComponentState;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;

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
     * Returns the paths of the {@code @Exposed} component fields of a built view, by the component they hold.
     *
     * @param view the view's host
     * @return the paths, such as {@code home.streetLabel}; empty before the view exists
     */
    public Map<Component, String> fields(ViewHost view) {
        Map<Component, String> fields = new HashMap<>();
        view.view()
                .ifPresent(
                        instance -> components(view.type(), instance, (path, held) -> fields.putIfAbsent(held, path)));
        return fields;
    }

    private <V> void components(Class<V> type, Object instance, BiConsumer<String, Component> component) {
        injectorOf(type).ifPresent(generated -> generated.components(type.cast(instance), component));
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
