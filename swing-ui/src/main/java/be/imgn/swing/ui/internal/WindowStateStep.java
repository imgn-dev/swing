package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.StartupStep;

/** Loads the window state at startup. */
@Singleton
@IntoSet(StartupStep.class)
public final class WindowStateStep implements StartupStep {

    private static final int ORDER = 200;

    private final WindowStateStore store;

    @Inject
    WindowStateStep(WindowStateStore store) {
        this.store = store;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        store.load();
    }
}
