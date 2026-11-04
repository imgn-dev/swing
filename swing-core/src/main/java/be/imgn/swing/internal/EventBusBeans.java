package be.imgn.swing.internal;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Eager;
import be.imgn.inject.Injector;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.EventExecutor;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.Scope;
import be.imgn.swing.TaskService;

/**
 * The application's event bus, the listener that registers the instances the construction hook reports on it, and the
 * executor of {@code @OnVirtualThread} deliveries.
 */
final class EventBusBeans {

    private EventBusBeans() {}

    @Provides
    @Singleton
    @IntoSet(ConstructionListener.class)
    static ConstructionListener subscriberRegistrar(SubscriberRegistrar registrar) {
        return registrar;
    }

    @Provides
    @Singleton
    @IntoSet(EventExecutor.class)
    static EventExecutor onVirtualThread(TaskService tasks, Scope application, DefaultLifecycle lifecycle) {
        return new VirtualThreadDeliveries(tasks, application, lifecycle);
    }

    // Eager, so that the bus is built with the root injector, after the construction listeners, and the instances
    // built for it are reported too. The registrar receives the bus here rather than injecting it: see its comment.
    @Provides
    @Singleton
    @Eager
    static EventBus eventBus(SubscriberRegistrar registrar, Injector injector, FailureHandler failures) {
        EventBus.Builder builder =
                EventBus.newBuilder().exceptionHandler((event, exception) -> failures.failed(exception));
        for (EventExecutor executor : Contributions.of(injector, EventExecutor.class)) {
            builder.executor(executor.annotation(), executor.executor());
        }
        EventBus bus = builder.build();
        registrar.attach(bus);
        return bus;
    }
}
