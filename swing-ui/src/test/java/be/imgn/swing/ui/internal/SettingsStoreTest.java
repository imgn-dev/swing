package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.inject.Injector;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.internal.DefaultApplicationInfo;
import be.imgn.swing.internal.DefaultLifecycle;
import be.imgn.swing.internal.DefaultSettingsStore;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.Launcher;
import be.imgn.swing.internal.TaskRunner;
import be.imgn.swing.spi.ApplicationDescriptor;
import be.imgn.swing.spi.SettingsDescriptor;
import be.imgn.toml.Toml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Core's settings store, on this module's window state record: toml finds the binders of a module's own records only,
// through its provides directives, and core has none.
class SettingsStoreTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final WindowStates DEFAULTS =
            new WindowStates(Map.of("main", new SavedWindow(List.of(1, 2, 3, 4), false, null)));
    private static final SettingsDescriptor<WindowStates> DESCRIPTOR = new SettingsDescriptor<>() {
        @Override
        public Class<WindowStates> type() {
            return WindowStates.class;
        }

        @Override
        public String id() {
            return "states";
        }

        @Override
        public WindowStates defaults() {
            return DEFAULTS;
        }

        @Override
        public Set<String> nullable() {
            return Set.of();
        }
    };

    @TempDir
    Path directory;

    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final List<String> failingThreads = new CopyOnWriteArrayList<>();
    private final DisposalScope application = DisposalScope.root("application");
    private final DefaultSettingsStore store = new DefaultSettingsStore(
            failure -> {
                failingThreads.add(Thread.currentThread().getName() + " "
                        + Thread.currentThread().isVirtual());
                failures.add(failure);
            },
            application);

    @AfterEach
    void close() {
        application.close();
    }

    private Path config() {
        return directory.resolve("config");
    }

    private Path file() {
        return config().resolve("states.toml");
    }

    private Property<WindowStates> load() {
        store.load(config(), List.of(DESCRIPTOR));
        return store.get(WindowStates.class);
    }

    @Test
    void mergesTheFileOverTheDefaultsAndIgnoresUnknownKeys() throws Exception {
        Files.createDirectories(config());
        Files.writeString(file(), """
                unknown = "from a newer version"

                [windows.dialog]
                maximized = true
                other = 3
                """);

        WindowStates loaded = load().get();

        // The file's windows replace the defaults' whole: an entry removed from a map stays removed.
        assertThat(loaded).isEqualTo(new WindowStates(Map.of("dialog", new SavedWindow(null, true, null))));
    }

    @Test
    void givesTheDefaultsOfTheKeysTheFileLacks() throws Exception {
        Files.createDirectories(config());
        Files.writeString(file(), "unknown = 1\n");

        assertThat(load().get()).isEqualTo(DEFAULTS);
    }

    @Test
    void movesAFileThatDoesNotParseAsideReplacingAPreviousOne() throws Exception {
        Files.createDirectories(config());
        Files.writeString(file(), "windows = [");
        Path invalid = config().resolve("states.toml.invalid");
        Files.writeString(invalid, "previous");

        assertThat(load().get()).isEqualTo(DEFAULTS);
        assertThat(file()).doesNotExist();
        assertThat(invalid).hasContent("windows = [");
    }

    @Test
    void movesAFileThatDoesNotBindAside() throws Exception {
        Files.createDirectories(config());
        Files.writeString(file(), "windows = 3\n");

        assertThat(load().get()).isEqualTo(DEFAULTS);
        assertThat(config().resolve("states.toml.invalid")).hasContent("windows = 3\n");
    }

    @Test
    void warnsOfAFileThatDoesNotBindNamingTheFileAndWhereItIsMoved() throws Exception {
        Files.createDirectories(config());
        Files.writeString(file(), "windows = 3\n");
        List<String> warnings = new CopyOnWriteArrayList<>();
        DefaultSettingsStore warned = new DefaultSettingsStore(failures::add, application, new Recorder(warnings));

        warned.load(config(), List.of(DESCRIPTOR));

        assertThat(warned.get(WindowStates.class).get()).isEqualTo(DEFAULTS);
        assertThat(warnings)
                .singleElement()
                .asString()
                .contains(file().toString(), "is invalid", "defaults", config().resolve("states.toml.invalid") + ":");
    }

    @Test
    void neitherMovesNorSavesAnInvalidFileItCannotMove() throws Exception {
        Files.createDirectories(config());
        Files.writeString(file(), "windows = 3\n");
        Files.setPosixFilePermissions(config(), PosixFilePermissions.fromString("r-x------"));
        try {
            Property<WindowStates> property = load();

            property.set(new WindowStates(Map.of()));

            assertThat(property.get()).isEqualTo(new WindowStates(Map.of()));
            assertThat(store.hasPendingSaves()).isFalse();
        } finally {
            Files.setPosixFilePermissions(config(), PosixFilePermissions.fromString("rwx------"));
        }
        assertThat(file()).hasContent("windows = 3\n");
        assertThat(config().resolve("states.toml.invalid")).doesNotExist();
    }

    @Test
    void savesEveryComponentWhenTheValueChangesCreatingTheDirectory() throws Exception {
        Property<WindowStates> property = load();
        assertThat(config()).doesNotExist();
        WindowStates changed = new WindowStates(Map.of(
                "main",
                new SavedWindow(List.of(5, 6, 7, 8), true, Map.of("split", new SavedComponent(9, null, null)))));

        property.set(changed);

        assertThat(store.awaitSaves(TIMEOUT)).isEmpty();
        assertThat(Toml.parse(file()).root()).isEqualTo(Toml.toTable(changed));
        assertThat(failures).isEmpty();
    }

    @Test
    void savesTheLatestValue() throws Exception {
        Property<WindowStates> property = load();
        WindowStates last = DEFAULTS;
        for (int index = 0; index < 50; index++) {
            last = new WindowStates(Map.of("main", new SavedWindow(List.of(index, 0, 1, 1), false, null)));
            property.set(last);
        }

        assertThat(store.awaitSaves(TIMEOUT)).isEmpty();
        assertThat(Toml.parse(file(), WindowStates.class)).isEqualTo(last);
    }

    @Test
    void savesOneAtATimeThenOnceMoreWithTheLastValueSetWhileItRan() throws Exception {
        Property<WindowStates> property = load();
        List<String> writes = new CopyOnWriteArrayList<>();
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Runnable inFile = () -> writes.add("with " + savedWindows() + " in the file");

        property.set(watched("first", writes, () -> {
            writing.countDown();
            await(release, writes);
        }));
        assertThat(writing.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        property.set(watched("second", writes, inFile));
        property.set(watched("third", writes, inFile));
        property.set(watched("fourth", writes, inFile));
        assertThat(writes).containsExactly("first on settings, virtual");
        release.countDown();

        assertThat(store.awaitSaves(TIMEOUT)).isEmpty();
        // The second save starts once the first has written its file.
        assertThat(writes)
                .containsExactly(
                        "first on settings, virtual",
                        "released, not interrupted",
                        "fourth on settings, virtual",
                        "with [first] in the file");
        assertThat(savedWindows()).containsExactly("fourth");
        assertThat(failures).isEmpty();
    }

    @Test
    void theShutdownsCancellationOfTheTasksDoesNotInterruptASave() throws Exception {
        DisposalScope scope = DisposalScope.root("application");
        Injector root = root(scope);
        try {
            DefaultSettingsStore settings = root.getInstance(DefaultSettingsStore.class);
            settings.load(config(), List.of(DESCRIPTOR));
            TaskRunner tasks = root.getInstance(TaskRunner.class);
            List<String> writes = new CopyOnWriteArrayList<>();
            CountDownLatch writing = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch interrupted = new CountDownLatch(1);
            tasks.start(
                    Task.of(() -> {
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException e) {
                            interrupted.countDown();
                            throw e;
                        }
                        return "never";
                    }),
                    scope,
                    "task");
            settings.get(WindowStates.class).set(watched("held", writes, () -> {
                writing.countDown();
                await(release, writes);
            }));
            assertThat(writing.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

            tasks.cancelAndAwait(DefaultLifecycle.TASK_TIMEOUT);
            assertThat(interrupted.getCount()).isZero();
            release.countDown();

            assertThat(settings.awaitSaves(TIMEOUT)).isEmpty();
            assertThat(writes).containsExactly("held on settings, virtual", "released, not interrupted");
            assertThat(savedWindows()).containsExactly("held");
            assertThat(failures).isEmpty();
        } finally {
            root.close();
            scope.close();
        }
    }

    @Test
    void writesNothingForAValueSetToTheSameValue() throws Exception {
        Property<WindowStates> property = load();

        property.set(new WindowStates(Map.of("main", new SavedWindow(List.of(1, 2, 3, 4), false, null))));

        assertThat(store.awaitSaves(TIMEOUT)).isEmpty();
        assertThat(file()).doesNotExist();
    }

    @Test
    void handsAFailedSaveToTheFailureHandlerAndKeepsTheValue() throws Exception {
        Property<WindowStates> property = load();
        Files.writeString(config(), "a file where the directory should be");
        WindowStates changed = new WindowStates(Map.of());

        property.set(changed);

        assertThat(store.awaitSaves(TIMEOUT)).isEmpty();
        assertThat(failures).singleElement().isInstanceOf(FileAlreadyExistsException.class);
        assertThat(failingThreads).containsExactly("settings true");
        assertThat(property.get()).isEqualTo(changed);
    }

    @Test
    void savesNothingOnceStopped() throws Exception {
        Property<WindowStates> property = load();

        store.stopSaving();
        property.set(new WindowStates(Map.of()));

        assertThat(store.hasPendingSaves()).isFalse();
        assertThat(file()).doesNotExist();
    }

    @Test
    void writesWithTheStoresWriter() throws Exception {
        WindowStates value = new WindowStates(Map.of("main", new SavedWindow(null, null, null)));

        DefaultSettingsStore.write(config(), "states", value);

        assertThat(Toml.parse(file(), WindowStates.class)).isEqualTo(value);
        assertThat(load().get()).isEqualTo(value);
    }

    // The names of the windows in the file.
    private Set<String> savedWindows() {
        try {
            return Toml.parse(file(), WindowStates.class).windows().keySet();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void await(CountDownLatch release, List<String> writes) {
        try {
            release.await();
            writes.add("released, " + (Thread.currentThread().isInterrupted() ? "interrupted" : "not interrupted"));
        } catch (InterruptedException e) {
            writes.add("interrupted while held");
            Thread.currentThread().interrupt();
        }
    }

    // A value with one window, named after it, whose writing runs an action on the thread that writes.
    private static WindowStates watched(String name, List<String> writes, Runnable writing) {
        return new WindowStates(new WatchedMap(Map.of(name, new SavedWindow(List.of(1, 2, 3, 4), false, null)), () -> {
            Thread thread = Thread.currentThread();
            writes.add(name + " on " + thread.getName() + (thread.isVirtual() ? ", virtual" : ", platform"));
            writing.run();
        }));
    }

    // The root injector, as the launch builds it, whose failures this test records.
    private Injector root(DisposalScope scope) {
        ApplicationDescriptor descriptor = new ApplicationDescriptor() {
            @Override
            public Class<?> type() {
                return SettingsStoreTest.class;
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
        return Launcher.build(
                Launcher.rootBuilder(info, List.of(), Locale.ROOT, scope).bind(FailureHandler.class, failures::add),
                info);
    }

    // A map that runs an action each time toml's writer reads it, which it does once per save, through forEach. The
    // property's equality checks read its entries, which run nothing.
    private static final class WatchedMap extends AbstractMap<String, SavedWindow> {

        private final Map<String, SavedWindow> windows;
        private final Runnable writing;

        WatchedMap(Map<String, SavedWindow> windows, Runnable writing) {
            this.windows = windows;
            this.writing = writing;
        }

        @Override
        public Set<Entry<String, SavedWindow>> entrySet() {
            return windows.entrySet();
        }

        @Override
        public void forEach(BiConsumer<? super String, ? super SavedWindow> action) {
            writing.run();
            windows.forEach(action);
        }
    }

    // Records the warnings it logs.
    private record Recorder(List<String> warnings) implements System.Logger {

        @Override
        public String getName() {
            return DefaultSettingsStore.class.getName();
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
