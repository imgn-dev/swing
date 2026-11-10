package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.Resources;
import be.imgn.swing.Scope;
import be.imgn.swing.StartupStep;
import be.imgn.swing.UiExecutor;
import java.time.Duration;

/**
 * Starts watching the TOML files of the class output, after the resources' step, in development mode: a virtual thread
 * polls them every 500 ms until the application's scope closes.
 */
@Singleton
@IntoSet(StartupStep.class)
public final class HotReloadStep implements StartupStep {

    private static final int ORDER = 110;
    private static final Duration PERIOD = Duration.ofMillis(500);
    private static final System.Logger LOGGER = System.getLogger(HotReloadStep.class.getName());

    private final HotReloading reloading;
    private final Resources resources;
    private final ResourceState state;
    private final Refresher refresher;
    private final UiExecutor executor;
    private final Scope application;

    @Inject
    HotReloadStep(
            HotReloading reloading,
            Resources resources,
            ResourceState state,
            Refresher refresher,
            UiExecutor executor,
            Scope application) {
        this.reloading = reloading;
        this.resources = resources;
        this.state = state;
        this.refresher = refresher;
        this.executor = executor;
        this.application = application;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        if (!reloading.enabled()) {
            return;
        }
        ResourceWatcher watcher = new ResourceWatcher(resources.openers(), state, refresher, executor);
        if (watcher.directories().isEmpty()) {
            return;
        }
        Thread thread = Thread.ofVirtual().name("hot reload").start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(PERIOD);
                    // A failure is logged rather than ending the watching for good.
                    Exception thrown = Guarded.runCatchingExceptions(watcher::poll);
                    if (thrown != null) {
                        LOGGER.log(System.Logger.Level.WARNING, "Could not reload the resources", thrown);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        application.onClose(thread::interrupt);
    }
}
