package be.imgn.swing.desktop.internal;

import be.imgn.inject.IntoSet;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.StartupStep;

/** The desktop's startup steps: the single instance's lock first, the open-file handler last. */
final class DesktopSteps {

    private DesktopSteps() {}

    @Provides
    @Singleton
    @IntoSet(StartupStep.class)
    static StartupStep singleInstance(SingleInstance instance) {
        return step(SingleInstance.ORDER, instance::start);
    }

    @Provides
    @Singleton
    @IntoSet(StartupStep.class)
    static StartupStep openFiles(OpenFiles files) {
        return step(OpenFiles.ORDER, files::start);
    }

    private static StartupStep step(int order, Runnable run) {
        return new StartupStep() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public void run() {
                run.run();
            }
        };
    }
}
