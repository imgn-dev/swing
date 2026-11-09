package be.imgn.swing.desktop;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;

/** Writes the last list of recent files at shutdown, since the shutdown cancels the writes still running. */
@Singleton
@IntoSet(ShutdownStep.class)
final class SaveRecentFilesStep implements ShutdownStep {

    private static final int ORDER = 320;

    private final RecentFiles files;

    @Inject
    SaveRecentFilesStep(RecentFiles files) {
        this.files = files;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        files.flush();
    }
}
