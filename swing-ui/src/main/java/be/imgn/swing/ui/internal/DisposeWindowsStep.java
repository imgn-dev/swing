package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;
import be.imgn.swing.ui.Windows;

/**
 * Disposes of the windows at shutdown, and closes their child injectors from innermost to outermost, which runs
 * {@code @PreDestroy} on their scoped beans.
 */
@Singleton
@IntoSet(ShutdownStep.class)
public final class DisposeWindowsStep implements ShutdownStep {

    private static final int ORDER = 200;

    private final WindowRegistry registry;
    private final Windows windows;

    @Inject
    DisposeWindowsStep(WindowRegistry registry, Windows windows) {
        this.registry = registry;
        this.windows = windows;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        for (WindowHost window : registry.windows().reversed()) {
            windows.dispose(window.window());
            try {
                window.injector().close();
            } finally {
                window.scope().close();
            }
        }
    }
}
