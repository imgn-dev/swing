package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.List;

/**
 * Waits for the pending saves of settings at shutdown, after the windows are disposed, so that the values their
 * {@code @PreDestroy} methods set are saved. A value set afterwards, while the root injector closes, is not saved.
 */
@Singleton
@IntoSet(ShutdownStep.class)
public final class SaveSettingsStep implements ShutdownStep {

    /** How long the step waits for the pending saves. */
    public static final Duration TIMEOUT = Duration.ofSeconds(2);

    private static final int ORDER = 300;
    private static final System.Logger LOGGER = System.getLogger(SaveSettingsStep.class.getName());

    private final DefaultSettingsStore store;
    private final System.Logger logger;

    @Inject
    SaveSettingsStep(DefaultSettingsStore store) {
        this(store, LOGGER);
    }

    // Tests read the warning through their own logger.
    SaveSettingsStep(DefaultSettingsStore store, System.Logger logger) {
        this.store = store;
        this.logger = logger;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        try {
            List<String> unsaved = store.awaitSaves(TIMEOUT);
            if (!unsaved.isEmpty()) {
                logger.log(
                        Level.WARNING,
                        "Could not save the settings " + String.join(", ", unsaved) + " within " + TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while waiting for the settings to be saved");
        } finally {
            store.stopSaving();
        }
    }
}
