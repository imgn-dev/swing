package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.StartupStep;

/** Reads the application class's resources off the EDT, so that the user interface finds them parsed. */
@Singleton
@IntoSet(StartupStep.class)
public final class ResourcesStep implements StartupStep {

    private static final int ORDER = 100;

    private final Resources resources;
    private final ApplicationInfo application;

    @Inject
    ResourcesStep(Resources resources, ApplicationInfo application) {
        this.resources = resources;
        this.application = application;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        if (application instanceof DefaultApplicationInfo info) {
            resources.table(info.descriptor().type());
        }
    }
}
