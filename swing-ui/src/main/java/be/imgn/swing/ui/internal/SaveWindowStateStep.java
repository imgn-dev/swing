package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;

/** Saves the state of every open window at shutdown: the main frame, and a dialog open at exit. */
@Singleton
@IntoSet(ShutdownStep.class)
public final class SaveWindowStateStep implements ShutdownStep {

    private static final int ORDER = 100;

    private final WindowRegistry registry;

    @Inject
    SaveWindowStateStep(WindowRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        for (WindowHost window : registry.windows()) {
            window.injector().getInstance(WindowStateSupport.class).save();
        }
    }
}
