package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.platform.Directories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.toml.Toml;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowStateStoreTest {

    @TempDir
    Path home;

    private WindowStateStore store;

    @BeforeEach
    void createStore() throws Exception {
        store = new WindowStateStore(new TestPlatform(home).applicationDirectories("test"));
        Files.createDirectories(store.file().getParent());
    }

    @Test
    void restoresAStateWithKeysItDoesNotKnow() throws Exception {
        Files.writeString(store.file(), """
                version = 3

                [windows.main]
                bounds = [1, 2, 3, 4]
                maximized = true
                opacity = 0.5

                [windows.main.components.split]
                divider-location = 240
                scroll = 10

                [[windows.main.components.table.columns]]
                model-index = 1
                width = 80
                sorted = true
                """);

        store.load();

        assertThat(store.get("main"))
                .contains(new SavedWindow(
                        List.of(1, 2, 3, 4),
                        true,
                        Map.of(
                                "split",
                                new SavedComponent(240, null, null),
                                "table",
                                new SavedComponent(null, null, List.of(new SavedColumn(1, 80))))));
    }

    @Test
    void discardsAStateWithAValueOfTheWrongType() throws Exception {
        Files.writeString(store.file(), """
                [windows.main]
                bounds = [1, 2, 3, 4]

                [windows.dialog.components.tabs]
                selected-index = "two"
                """);

        store.load();

        assertThat(store.get("main")).isEmpty();
        assertThat(store.get("dialog")).isEmpty();
    }

    @Test
    void discardsAStateWhoseBoundsAreNotFourIntegers() throws Exception {
        Files.writeString(store.file(), """
                [windows.main]
                maximized = false

                [windows.dialog]
                bounds = [1, 2, 3]
                """);

        store.load();

        assertThat(store.get("main")).isEmpty();
    }

    @Test
    void writesTheKeysOfMilestoneOnesFileLeavingOutTheNullComponents() throws Exception {
        store.save(
                "main",
                new SavedWindow(
                        List.of(100, 100, 640, 480), false, Map.of("split", new SavedComponent(240, null, null))));
        store.save("p.RenameDialog", new SavedWindow(List.of(300, 200, 320, 160), null, null));

        assertThat(Toml.parse(store.file()).root()).isEqualTo(Toml.parse("""
                [windows.main]
                bounds = [100, 100, 640, 480]
                maximized = false

                [windows.main.components.split]
                divider-location = 240

                [windows."p.RenameDialog"]
                bounds = [300, 200, 320, 160]
                """).root());

        store.load();

        assertThat(store.get("p.RenameDialog")).contains(new SavedWindow(List.of(300, 200, 320, 160), null, null));
    }

    @Test
    void savesAndRestoresTheStateOfAddedViewsAndTheLayoutOfADock() {
        SavedLayout layout = new SavedLayout(
                "horizontal",
                0.33,
                new SavedLayout(null, null, null, null, List.of("p.ListView"), 0),
                new SavedLayout(null, null, null, null, List.of("p.PictureView", "p.DetailView"), 1),
                null,
                null);
        SavedWindow window = new SavedWindow(
                List.of(1, 2, 3, 4),
                false,
                null,
                null,
                Map.of("p.ListView", new SavedView(Map.of("table", new SavedComponent(null, null, null)))),
                layout);

        store.save("main", window);
        store.load();

        assertThat(store.get("main")).contains(window);
    }

    @Test
    void discardsAStateWhoseDockIsOfTheWrongType() throws Exception {
        Files.writeString(store.file(), """
                [windows.main]
                bounds = [1, 2, 3, 4]
                dock = "list"
                """);

        store.load();

        assertThat(store.get("main")).isEmpty();
    }

    @Test
    void readsANestedDockLayout() throws Exception {
        Files.writeString(store.file(), """
                [windows.main.dock]
                split = "horizontal"
                proportion = 0.2

                [windows.main.dock.first]
                tabs = ["p.A"]
                selected = 0

                [windows.main.dock.second]
                split = "vertical"
                proportion = 0.6

                [windows.main.dock.second.first]
                tabs = ["p.B", "p.C"]
                selected = 1

                [windows.main.dock.second.second]
                tabs = ["p.D"]
                selected = 0
                """);

        store.load();

        assertThat(store.get("main").map(SavedWindow::dock))
                .contains(new SavedLayout(
                        "horizontal",
                        0.2,
                        new SavedLayout(null, null, null, null, List.of("p.A"), 0),
                        new SavedLayout(
                                "vertical",
                                0.6,
                                new SavedLayout(null, null, null, null, List.of("p.B", "p.C"), 1),
                                new SavedLayout(null, null, null, null, List.of("p.D"), 0),
                                null,
                                null),
                        null,
                        null));
    }

    // A Linux platform whose directories are all under a temporary home.
    private record TestPlatform(Path home) implements Platform {

        @Override
        public boolean isLinux() {
            return true;
        }

        @Override
        public boolean isMacOS() {
            return false;
        }

        @Override
        public boolean isWindows() {
            return false;
        }

        @Override
        public FileSystem fileSystem() {
            return FileSystems.getDefault();
        }

        @Override
        public Directories directories() {
            return new Directories() {
                @Override
                public Path home() {
                    return home;
                }

                @Override
                public Path dataHome() {
                    return home.resolve("data");
                }

                @Override
                public Path configHome() {
                    return home.resolve("config");
                }

                @Override
                public Path stateHome() {
                    return home.resolve("state");
                }

                @Override
                public Path cacheHome() {
                    return home.resolve("cache");
                }

                @Override
                public Optional<Path> runtimeDir() {
                    return Optional.empty();
                }

                @Override
                public List<Path> dataDirs() {
                    return List.of();
                }

                @Override
                public List<Path> configDirs() {
                    return List.of();
                }
            };
        }
    }
}
