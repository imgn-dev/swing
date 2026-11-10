package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;

/** Writes the last display locale the user chose, since the shutdown cancels the tasks still running. */
@Singleton
@IntoSet(ShutdownStep.class)
public final class SaveLocaleStep implements ShutdownStep {

    private static final int ORDER = 330;

    private final LocaleFile file;

    @Inject
    SaveLocaleStep(LocaleFile file) {
        this.file = file;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        file.flush();
    }
}
