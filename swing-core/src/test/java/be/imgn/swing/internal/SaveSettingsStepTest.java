package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.spi.SettingsDescriptor;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveSettingsStepTest {

    @TempDir
    Path config;

    private final CountDownLatch release = new CountDownLatch(1);
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final DisposalScope application = DisposalScope.root("application");
    // The binder refuses to write UNWRITABLE, so its save fails: the failure handler holds the save until the
    // test releases it.
    private final DefaultSettingsStore store = new DefaultSettingsStore(
            failure -> {
                failures.add(failure);
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            },
            application);
    private final SaveSettingsStep step = new SaveSettingsStep(store, new Recorder(warnings));

    @AfterEach
    void close() {
        release.countDown();
        application.close();
    }

    @Test
    void waitsForThePendingSavesThenNamesTheRecordsItCouldNotSave() {
        store.load(config, List.of(new SampleDescriptor()));
        store.get(SampleSettings.class).set(new SampleSettings(SampleSettings.UNWRITABLE));

        step.run();

        assertThat(store.hasPendingSaves()).isTrue();
        assertThat(warnings).singleElement().asString().contains(SampleSettings.class.getName());
    }

    @Test
    void savesNothingSetAfterIt() {
        store.load(config, List.of(new SampleDescriptor()));

        step.run();
        store.get(SampleSettings.class).set(new SampleSettings(SampleSettings.UNWRITABLE));

        assertThat(store.hasPendingSaves()).isFalse();
        assertThat(failures).isEmpty();
        assertThat(warnings).isEmpty();
    }

    private static final class SampleDescriptor implements SettingsDescriptor<SampleSettings> {

        @Override
        public Class<SampleSettings> type() {
            return SampleSettings.class;
        }

        @Override
        public String id() {
            return "sample";
        }

        @Override
        public SampleSettings defaults() {
            return new SampleSettings(14);
        }

        @Override
        public Set<String> nullable() {
            return Set.of();
        }
    }

    // Records the warnings it logs.
    private record Recorder(List<String> warnings) implements System.Logger {

        @Override
        public String getName() {
            return SaveSettingsStep.class.getName();
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(
                Level level, @Nullable ResourceBundle bundle, @Nullable String message, @Nullable Throwable thrown) {
            if (level == Level.WARNING) {
                warnings.add(String.valueOf(message));
            }
        }

        @Override
        public void log(
                Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... params) {
            if (level == Level.WARNING) {
                warnings.add(String.valueOf(format));
            }
        }
    }
}
