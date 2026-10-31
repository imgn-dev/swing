package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;
import be.imgn.swing.ui.Windows;

/**
 * Disposes of the windows at shutdown, and closes them from innermost to outermost: each view's child injector, which
 * runs {@code @PreDestroy} on its scoped beans, then its scope, then the window's child injector, then its scope.
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
            window.close();
        }
    }
}
