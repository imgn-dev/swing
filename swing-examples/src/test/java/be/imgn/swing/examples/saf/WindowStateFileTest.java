package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlTable;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The window state file, written through toml's generated code. */
class WindowStateFileTest {

    @Test
    void writesTheKeysOfTheFileLeavingOutWhatIsNotSaved() throws Exception {
        try (AppTester<SessionStorageExample1> app = AppTester.start(SessionStorageExample1.class)) {
            Path file = app.bean(ApplicationDirectories.class).state().resolve("window-state.toml");

            app.commands(StandardCommands.class).exit().execute();

            TomlTable main = Toml.parse(file).root().getTable("windows").getTable("main");
            // The headless window has no bounds.
            assertThat(main.entries()).containsOnlyKeys("maximized", "components");
            TomlTable components = main.getTable("components");
            assertThat(components.getTable("colors").entries()).containsOnlyKeys("selected-index");
            assertThat(components.getTable("red").entries()).containsOnlyKeys("divider-location");
            assertThat(components.getTable("all").getArray("columns").values())
                    .first()
                    .isInstanceOfSatisfying(
                            TomlTable.class,
                            column -> assertThat(column.entries()).containsOnlyKeys("model-index", "width"));
        }
    }
}
