package be.imgn.swing.desktop.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;
import java.io.IOException;
import java.lang.System.Logger.Level;

/** Closes the single instance's server and releases its lock at shutdown, after the windows. */
@Singleton
@IntoSet(ShutdownStep.class)
public final class CloseInstanceStep implements ShutdownStep {

    private static final int ORDER = 300;
    private static final System.Logger LOGGER = System.getLogger(CloseInstanceStep.class.getName());

    private final SingleInstance instance;

    @Inject
    CloseInstanceStep(SingleInstance instance) {
        this.instance = instance;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        try {
            instance.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not release the instance lock", e);
        }
    }
}
