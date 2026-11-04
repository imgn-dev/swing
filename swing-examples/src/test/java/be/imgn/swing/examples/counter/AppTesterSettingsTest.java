package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.Property;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.examples.notes.EditorSettings;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.Ui;
import be.imgn.toml.Toml;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The tester's support of settings and files, on the counter, with the notes' editor settings. */
class AppTesterSettingsTest {

    private static Property<EditorSettings> editor(AppTester<?> app) {
        return app.bean(SettingsStore.class).get(EditorSettings.class);
    }

    private static Path editorFile(AppTester<?> app) {
        return app.bean(ApplicationDirectories.class).config().resolve("editor.toml");
    }

    @Test
    void startsWithTheDefaultsAndNoFile() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            assertThat(editor(app).get()).isEqualTo(new EditorSettings());
            assertThat(editorFile(app)).doesNotExist();
            assertThat(editorFile(app).getFileSystem()).isNotEqualTo(FileSystems.getDefault());
        }
    }

    @Test
    void startsOnTheSettingsGivenToTheBuilderTheLaterReplacingTheEarlier() throws Exception {
        try (AppTester<CounterApp> app = AppTester.builder(CounterApp.class)
                .settings(new EditorSettings(10, true))
                .settings(new EditorSettings(12, false))
                .start()) {
            assertThat(editor(app).get()).isEqualTo(new EditorSettings(12, false));
            assertThat(Files.readString(editorFile(app)))
                    .contains("font-size = 12")
                    .contains("word-wrap = false");
        }
    }

    @Test
    void rejectsARecordThatIsNotASettingsRecord() {
        record NotSettings(int size) {}

        assertThatThrownBy(() -> AppTester.builder(CounterApp.class).settings(new NotSettings(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No settings descriptor for");
    }

    @Test
    void waitsForTheSaveOfAChangedValue() throws Exception {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            editor(app).set(new EditorSettings(18, true));

            assertThat(Files.readString(editorFile(app)))
                    .contains("font-size = 18")
                    .contains("word-wrap = true");
        }
    }

    @Test
    void loadsAFileWrittenWithTheBuilderIgnoringUnknownKeys() {
        try (AppTester<CounterApp> app = AppTester.builder(CounterApp.class)
                .file(directories -> directories.config().resolve("editor.toml"), """
                        font-size = 20
                        line-numbers = true
                        """)
                .start()) {
            assertThat(editor(app).get()).isEqualTo(new EditorSettings(20, true));
        }
    }

    @Test
    void movesAFileThatDoesNotParseAside() throws Exception {
        try (AppTester<CounterApp> app = AppTester.builder(CounterApp.class)
                .file(directories -> directories.config().resolve("editor.toml"), "font-size = ")
                .start()) {
            assertThat(editor(app).get()).isEqualTo(new EditorSettings());
            assertThat(editorFile(app)).doesNotExist();
            assertThat(Files.readString(editorFile(app).resolveSibling("editor.toml.invalid")))
                    .isEqualTo("font-size = ");
        }
    }

    @Test
    void writesAFileCreatingItsDirectories() throws Exception {
        try (AppTester<CounterApp> app = AppTester.builder(CounterApp.class)
                .file(directories -> directories.data().resolve("notes/today.txt"), "Hello")
                .start()) {
            Path file = app.bean(ApplicationDirectories.class).data().resolve("notes/today.txt");

            assertThat(Files.readString(file)).isEqualTo("Hello");
        }
    }

    @Test
    void startsASecondTimeOnWhatTheFirstRunSavedOnTheSameFileSystem() throws Exception {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<CounterApp> app =
                    AppTester.builder(CounterApp.class).fileSystem(fileSystem).start()) {
                // Closing right after setting waits for the save before the run ends.
                app.bean(SettingsStore.class).get(EditorSettings.class).set(new EditorSettings(30, false));
            }

            try (AppTester<CounterApp> app =
                    AppTester.builder(CounterApp.class).fileSystem(fileSystem).start()) {
                assertThat(editor(app).get()).isEqualTo(new EditorSettings(30, false));
            }

            assertThat(fileSystem.isOpen()).isTrue();
        }
    }

    @Test
    void savesAtExitTheValueAViewsPreDestroyMethodSets() throws Exception {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            app.bean(ClosingEditorSettings.class);
            Path file = editorFile(app);

            app.commands(CounterAppCommands.class).exit().execute();

            assertThat(app.isRunning()).isFalse();
            assertThat(Toml.parse(file, EditorSettings.class)).isEqualTo(ClosingEditorSettings.CLOSED);
        }
    }

    @Test
    void savesBeforeClosingTheValueAViewsPreDestroyMethodSets() throws Exception {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path file;
            try (AppTester<CounterApp> app =
                    AppTester.builder(CounterApp.class).fileSystem(fileSystem).start()) {
                app.bean(ClosingEditorSettings.class);
                file = editorFile(app);
            }

            assertThat(Toml.parse(file, EditorSettings.class)).isEqualTo(ClosingEditorSettings.CLOSED);
        }
    }

    @Test
    void resolvesTheBeansOfTheMainViewsScopes() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            assertThat(app.bean(Ui.class)).isSameAs(app.bean(Ui.class));
            assertThat(app.bean(SettingsStore.class)).isSameAs(app.bean(SettingsStore.class));
        }
    }
}
