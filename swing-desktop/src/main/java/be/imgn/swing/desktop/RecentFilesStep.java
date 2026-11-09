package be.imgn.swing.desktop;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.StartupStep;

/** Loads the recent files at startup, after the settings and the window state. */
@Singleton
@IntoSet(StartupStep.class)
final class RecentFilesStep implements StartupStep {

    private static final int ORDER = 250;

    private final RecentFiles files;

    @Inject
    RecentFilesStep(RecentFiles files) {
        this.files = files;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        files.load();
    }
}
