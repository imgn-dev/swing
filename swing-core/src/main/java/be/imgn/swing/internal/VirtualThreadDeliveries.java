package be.imgn.swing.internal;

import be.imgn.gaia.core.event.OnVirtualThread;
import be.imgn.swing.EventExecutor;
import be.imgn.swing.Scope;
import be.imgn.swing.TaskService;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.util.concurrent.Executor;

/**
 * The executor of {@link OnVirtualThread} on the application's bus: it runs each delivery as a task of the application
 * scope, each on a virtual thread of its own, so that the shutdown cancels and waits for the running deliveries as for
 * any task, and an {@code Error} thrown by the method goes to the {@code FailureHandler} as a task's failure does.
 *
 * <p>The bus hands it a delivery without the instance it belongs to, so it can't run in the instance's own scope: a
 * delivery whose subscription was closed before it starts doesn't call the method, as the bus guarantees.
 */
final class VirtualThreadDeliveries implements EventExecutor {

    private static final System.Logger LOGGER = System.getLogger(VirtualThreadDeliveries.class.getName());

    private final TaskService tasks;
    private final Scope application;
    private final DefaultLifecycle lifecycle;

    VirtualThreadDeliveries(TaskService tasks, Scope application, DefaultLifecycle lifecycle) {
        this.tasks = tasks;
        this.application = application;
        this.lifecycle = lifecycle;
    }

    @Override
    public Class<? extends Annotation> annotation() {
        return OnVirtualThread.class;
    }

    @Override
    public Executor executor() {
        return this::deliver;
    }

    private void deliver(Runnable delivery) {
        // Once shutdown has started, it has cancelled and awaited the running tasks, or is doing so: a delivery started
        // then, such as one of an event that a shutdown step publishes, would run with nobody waiting for it.
        if (lifecycle.hasShutdownStarted() || application.isClosed()) {
            dropped();
            return;
        }
        try {
            tasks.run(BackgroundTask.delivery(delivery), application);
        } catch (IllegalStateException e) {
            // The scope closed between the check and the start: the delivery is dropped as above.
            if (!application.isClosed()) {
                throw e;
            }
            dropped();
        }
    }

    private static void dropped() {
        LOGGER.log(Level.DEBUG, "An @OnVirtualThread delivery was dropped: the application is shutting down");
    }
}
