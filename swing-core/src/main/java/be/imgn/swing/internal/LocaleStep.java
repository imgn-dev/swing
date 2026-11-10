package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.StartupStep;

/** Reads the display locale the user chose in an earlier run, before anything reads resources. */
@Singleton
@IntoSet(StartupStep.class)
public final class LocaleStep implements StartupStep {

    private static final int ORDER = 50;

    private final DefaultLocales locales;

    @Inject
    LocaleStep(DefaultLocales locales) {
        this.locales = locales;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        locales.load();
    }
}
