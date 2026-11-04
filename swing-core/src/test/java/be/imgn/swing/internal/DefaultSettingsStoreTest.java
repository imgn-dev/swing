package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Property;
import be.imgn.swing.spi.SettingsDescriptor;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlParseException;
import be.imgn.toml.TomlTable;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Run on the class path, where the service loader finds the binder of SampleSettings in the tests' META-INF/services;
// Other has none.
class DefaultSettingsStoreTest {

    @TempDir
    Path config;

    private final List<Throwable> failures = new ArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final DisposalScope application = DisposalScope.root("application");
    private final DefaultSettingsStore store =
            new DefaultSettingsStore(failures::add, application, new Recorder(warnings));

    @Test
    void mergesTheFileOverTheDefaultsComponentByComponent() {
        TomlTable defaults = Toml.parse("""
                size = 14
                wrap = true
                [fonts]
                code = "Mono"
                text = "Serif"
                """).root();
        TomlTable file = Toml.parse("""
                size = 18
                unknown = 1
                [fonts]
                code = "Courier"
                """).root();

        TomlTable merged = DefaultSettingsStore.merge(defaults, Set.of(), file);

        assertThat(merged).isEqualTo(Toml.parse("""
                size = 18
                wrap = true
                unknown = 1
                [fonts]
                code = "Courier"
                """).root());
    }

    @Test
    void leavesTheNullableComponentsOutOfTheDefaults() {
        TomlTable defaults = Toml.parse("""
                size = 14
                font-name = "Mono"
                """).root();

        TomlTable merged = DefaultSettingsStore.merge(
                defaults, Set.of("font-name"), TomlTable.builder().build());

        assertThat(merged).isEqualTo(Toml.parse("size = 14").root());
    }

    @Test
    void givesTheDefaultsForAMissingFileAndWritesNoFile() throws Exception {
        store.load(config, List.of(descriptor("sample", SampleSettings.class, new SampleSettings(14))));

        assertThat(store.get(SampleSettings.class).get()).isEqualTo(new SampleSettings(14));
        assertThat(store.awaitSaves(Duration.ofSeconds(5))).isEmpty();
        assertThat(config).isEmptyDirectory();
    }

    @Test
    void failsTheLoadingOfARecordWithoutBinderWhateverItsFile() throws Exception {
        var other = descriptor("other", Other.class, new Other(0));

        assertThatThrownBy(() -> store.load(config, List.of(other)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Other.class.getName());

        Path file = Files.writeString(config.resolve("other.toml"), "size = [");
        assertThatThrownBy(() -> store.load(config, List.of(other)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Other.class.getName());
        assertThat(file).hasContent("size = [");
        assertThat(config.resolve("other.toml.invalid")).doesNotExist();
        assertThat(warnings).isEmpty();
    }

    @Test
    void returnsTheSamePropertyOnEveryCall() {
        store.load(config, List.of(descriptor("sample", SampleSettings.class, new SampleSettings(14))));

        Property<SampleSettings> first = store.get(SampleSettings.class);

        assertThat(store.get(SampleSettings.class)).isSameAs(first);
    }

    @Test
    void rejectsATypeWithoutDescriptor() {
        store.load(config, List.of());

        assertThatThrownBy(() -> store.get(SampleSettings.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No settings descriptor for " + SampleSettings.class.getName());
    }

    @Test
    void rejectsAReadBeforeTheLoading() {
        assertThatThrownBy(() -> store.get(SampleSettings.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not loaded yet");
    }

    @Test
    void rejectsTwoRecordsWithTheSameId() {
        var first = descriptor("sample", SampleSettings.class, new SampleSettings(14));
        var second = descriptor("sample", Other.class, new Other(0));

        assertThatThrownBy(() -> store.load(config, List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SampleSettings.class.getName())
                .hasMessageContaining(Other.class.getName())
                .hasMessageContaining("same id sample");
    }

    @Test
    void neitherReadsNorSavesAFileItCannotRead() throws Exception {
        Path file = Files.writeString(config.resolve("sample.toml"), "size = 20\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("-w-------"));
        store.load(config, List.of(descriptor("sample", SampleSettings.class, new SampleSettings(14))));

        store.get(SampleSettings.class).set(new SampleSettings(SampleSettings.UNWRITABLE));

        assertThat(store.get(SampleSettings.class).get()).isEqualTo(new SampleSettings(SampleSettings.UNWRITABLE));
        assertThat(store.hasPendingSaves()).isFalse();
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.readString(file)).isEqualTo("size = 20\n");
        assertThat(failures).isEmpty();
        assertThat(warnings)
                .singleElement()
                .asString()
                .contains(file.toString())
                .contains(AccessDeniedException.class.getName());
    }

    @Test
    void warnsOfAFileThatDoesNotParseNamingTheFileAndTheError() throws Exception {
        Path file = Files.writeString(config.resolve("sample.toml"), "size = [");
        String error = parseError(file);

        store.load(config, List.of(descriptor("sample", SampleSettings.class, new SampleSettings(14))));

        assertThat(store.get(SampleSettings.class).get()).isEqualTo(new SampleSettings(14));
        assertThat(config.resolve("sample.toml.invalid")).hasContent("size = [");
        assertThat(warnings)
                .singleElement()
                .asString()
                .contains(file.toString())
                .contains(error);
    }

    @Test
    void warnsOfAnInvalidFileItCannotMoveNamingTheFileAndBothErrors() throws Exception {
        Path file = Files.writeString(config.resolve("sample.toml"), "size = [");
        String error = parseError(file);
        Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("r-x------"));
        try {
            store.load(config, List.of(descriptor("sample", SampleSettings.class, new SampleSettings(14))));
        } finally {
            Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("rwx------"));
        }

        assertThat(store.get(SampleSettings.class).get()).isEqualTo(new SampleSettings(14));
        assertThat(file).hasContent("size = [");
        assertThat(warnings)
                .singleElement()
                .asString()
                .contains(file.toString())
                .contains(error)
                .contains(AccessDeniedException.class.getName());
    }

    private static String parseError(Path file) throws IOException {
        try {
            Toml.parse(file);
        } catch (TomlParseException e) {
            return String.valueOf(e.getMessage());
        }
        throw new AssertionError("The file " + file + " parses");
    }

    @Test
    void handsASaveThatThrowsAnUncheckedExceptionToTheFailureHandlerAndKeepsTheValue() throws Exception {
        store.load(config, List.of(descriptor("sample", SampleSettings.class, new SampleSettings(14))));

        // The binder refuses to write this value, so the writer throws an unchecked exception.
        store.get(SampleSettings.class).set(new SampleSettings(SampleSettings.UNWRITABLE));

        assertThat(store.awaitSaves(Duration.ofSeconds(5))).isEmpty();
        assertThat(failures).singleElement().isInstanceOf(RuntimeException.class);
        assertThat(store.get(SampleSettings.class).get()).isEqualTo(new SampleSettings(SampleSettings.UNWRITABLE));
    }

    @Test
    void namesTheFileOfARecordAfterItsId() {
        assertThat(DefaultSettingsStore.file(config, "editor")).isEqualTo(config.resolve("editor.toml"));
    }

    private static <T> SettingsDescriptor<T> descriptor(String id, Class<T> type, T defaults) {
        return new SettingsDescriptor<>() {
            @Override
            public Class<T> type() {
                return type;
            }

            @Override
            public String id() {
                return id;
            }

            @Override
            public T defaults() {
                return defaults;
            }

            @Override
            public Set<String> nullable() {
                return Set.of();
            }
        };
    }

    record Other(int size) {}

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
