package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.inject.Injector;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.ShutdownStep;
import be.imgn.swing.StartupStep;
import be.imgn.swing.internal.Contributions;
import be.imgn.swing.internal.DefaultApplicationInfo;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.Launcher;
import be.imgn.swing.internal.ResourcesStep;
import be.imgn.swing.internal.SaveSettingsStep;
import be.imgn.swing.internal.SettingsStep;
import be.imgn.swing.spi.ApplicationDescriptor;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// The startup and shutdown steps of core and of this module, as the root injector sorts them.
class SettingsStepsTest {

    private final DisposalScope application = DisposalScope.root("application");
    private final Injector root = root(application);

    @AfterEach
    void close() {
        root.close();
        application.close();
    }

    @Test
    void loadsTheSettingsBetweenTheResourcesAndTheWindowState() {
        assertThat(Contributions.sorted(root, StartupStep.class, StartupStep::order))
                .extracting(Object::getClass)
                .containsSubsequence(ResourcesStep.class, SettingsStep.class, WindowStateStep.class);
    }

    @Test
    void rejectsAReadOfTheSettingsFromAStepBeforeTheLoading() {
        SettingsStore settings = root.getInstance(SettingsStore.class);
        for (StartupStep step : Contributions.sorted(root, StartupStep.class, StartupStep::order)) {
            // What the step reads when it runs.
            assertThatThrownBy(() -> settings.get(WindowStates.class))
                    .as("read from %s", step.getClass().getName())
                    .isInstanceOf(IllegalStateException.class);
            step.run();
            if (step instanceof SettingsStep) {
                break;
            }
        }

        // Loaded: no descriptor names the record, rather than not loaded yet.
        assertThatThrownBy(() -> settings.get(WindowStates.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void waitsForTheSettingsAfterTheWindowsAreDisposed() {
        assertThat(Contributions.sorted(root, ShutdownStep.class, ShutdownStep::order))
                .extracting(Object::getClass)
                .containsSubsequence(SaveWindowStateStep.class, DisposeWindowsStep.class, SaveSettingsStep.class);
    }

    private static Injector root(DisposalScope scope) {
        ApplicationDescriptor descriptor = new ApplicationDescriptor() {
            @Override
            public Class<?> type() {
                return SettingsStepsTest.class;
            }

            @Override
            public String name() {
                return "Test";
            }

            @Override
            public Optional<String> vendor() {
                return Optional.empty();
            }

            @Override
            public Optional<String> qualifier() {
                return Optional.empty();
            }

            @Override
            public Optional<String> version() {
                return Optional.empty();
            }
        };
        DefaultApplicationInfo info = new DefaultApplicationInfo(descriptor);
        return Launcher.build(Launcher.rootBuilder(info, List.of(), Locale.ROOT, scope), info);
    }
}
