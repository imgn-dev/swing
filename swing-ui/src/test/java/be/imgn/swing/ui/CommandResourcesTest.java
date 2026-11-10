package be.imgn.swing.ui;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.toml.Toml;
import be.imgn.toml.TomlTable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandResourcesTest {

    @Test
    void tellsAShortcutOfTheFrameworkFromOneAnApplicationGives() {
        Map<Class<?>, TomlTable> framework = new LinkedHashMap<>();
        framework.put(
                StandardCommands.class,
                Toml.parse("text = \"&Copy\"\nshortcut = \"shortcut C\"\n").root());
        framework.put(
                CommandResourcesTest.class, Toml.parse("text = \"&Copy It\"\n").root());
        Map<Class<?>, TomlTable> application = new LinkedHashMap<>(framework);
        application.put(
                CommandResourcesTest.class,
                Toml.parse("shortcut = \"shortcut shift C\"\n").root());

        assertThat(CommandResources.of(framework).frameworkShortcut()).isTrue();
        assertThat(CommandResources.of(application).frameworkShortcut()).isFalse();
    }
}
