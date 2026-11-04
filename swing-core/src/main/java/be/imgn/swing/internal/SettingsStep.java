package be.imgn.swing.internal;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.StartupStep;

/**
 * Loads every settings record whose descriptor the service loader finds, from the config directory, between resources
 * and window state.
 */
@Singleton
@IntoSet(StartupStep.class)
public final class SettingsStep implements StartupStep {

    private static final int ORDER = 150;

    private final DefaultSettingsStore store;
    private final ApplicationDirectories directories;

    @Inject
    SettingsStep(DefaultSettingsStore store, ApplicationDirectories directories) {
        this.store = store;
        this.directories = directories;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        store.load(directories.config(), DefaultSettingsStore.descriptors());
    }
}
