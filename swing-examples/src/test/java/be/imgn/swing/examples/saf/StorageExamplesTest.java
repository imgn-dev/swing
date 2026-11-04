package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.testing.AppTester;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.ListModel;
import org.junit.jupiter.api.Test;

class StorageExamplesTest {

    @Test
    void localStorageExample1LoadsItsEntriesBackOnASecondStart() throws Exception {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<LocalStorageExample1> app = AppTester.builder(LocalStorageExample1.class)
                    .fileSystem(fileSystem)
                    .start()) {
                LocalStorageExample1 example = app.application();
                assertThat(entries(app)).isEmpty();
                app.run(() -> {
                    example.put("Hello", "World");
                    example.put("Bonjour", "Monde");
                });
                app.commands(LocalStorageExample1Commands.class)
                        .addRandomEntry()
                        .execute();

                assertThat(entries(app)).hasSize(3).startsWith("Hello = World", "Bonjour = Monde");
            }

            try (AppTester<LocalStorageExample1> app = AppTester.builder(LocalStorageExample1.class)
                    .fileSystem(fileSystem)
                    .start()) {
                assertThat(entries(app)).hasSize(3).startsWith("Hello = World", "Bonjour = Monde");
                assertThat(app.bean(SettingsStore.class)
                                .get(Entries.class)
                                .get()
                                .entries())
                        .containsEntry("Hello", "World")
                        .containsEntry("Bonjour", "Monde");
            }
        }
    }

    @Test
    void localStorageExample1SavesItsEntriesAtEachChange() throws Exception {
        try (AppTester<LocalStorageExample1> app = AppTester.start(LocalStorageExample1.class)) {
            LocalStorageExample1 example = app.application();
            Path file = app.bean(ApplicationDirectories.class).config().resolve("entries.toml");
            assertThat(file).doesNotExist();

            app.run(() -> example.put("Hello", "World"));

            assertThat(Files.readString(file)).contains("Hello = \"World\"");

            app.commands(LocalStorageExample1Commands.class).clearMap().execute();

            assertThat(entries(app)).isEmpty();
            assertThat(Files.readString(file)).doesNotContain("Hello");
            assertThat(app.bean(SettingsStore.class).get(Entries.class).get()).isEqualTo(new Entries());
        }
    }

    @Test
    void localStorageExample1BuildsItsButtonRowWithTheButtonBar() {
        try (AppTester<LocalStorageExample1> app = AppTester.start(LocalStorageExample1.class)) {
            assertThat(app.components(JButton.class))
                    .extracting(JButton::getText)
                    .filteredOn(text -> !text.isEmpty())
                    .containsExactly("Add Random Entry", "Clear");

            ButtonRows.assertButtonBar(
                    app, app.commands(LocalStorageExample1Commands.class).clearMap());
        }
    }

    private static List<String> entries(AppTester<LocalStorageExample1> app) {
        JList<?> list = app.component(JList.class);
        return app.call(() -> {
            List<String> entries = new ArrayList<>();
            ListModel<?> model = list.getModel();
            for (int index = 0; index < model.getSize(); index++) {
                entries.add(String.valueOf(model.getElementAt(index)));
            }
            return entries;
        });
    }
}
