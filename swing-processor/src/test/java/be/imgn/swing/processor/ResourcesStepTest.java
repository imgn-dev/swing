package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class ResourcesStepTest {

    private static Sources counter(String resources) {
        return Sources.create().source("p.CounterApp", Sources.COUNTER).resource("p/CounterApp.toml", resources);
    }

    @Test
    void generatesTheOpenerWithItsIcons() {
        var compilation =
                counter("""
                        name = "Counter!"
                        theme = "dark"
                        title = "The counter"

                        [commands.increment]
                        text = "&Increment"
                        icon = "icons/plus.png"
                        shortcut = "shortcut I"

                        [label]
                        font = "SansSerif-BOLD-12"
                        foreground = "#336699"
                        """).resource("p/icons/plus.png", new byte[] {1, 2, 3}).compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$$Resources")
                .contains("public final class $$Resources implements ResourceOpener")
                .contains("return Optional.ofNullable($$Resources.class.getResourceAsStream(fileName));")
                .contains("return Set.of(\"icons/plus.png\");");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/be.imgn.swing.ResourceOpener")
                .contains("p.$$Resources");
    }

    @Test
    void reportsASyntaxErrorAtItsPosition() {
        var compilation = counter("[commands.increment]\ntext = \n").compile();

        assertThat(compilation).failed().hadErrorContaining("CounterApp.toml:2:");
    }

    @Test
    void rejectsACommandTableThatNamesNoCommand() {
        var compilation = counter("""
                        [commands.increment]
                        text = "&Increment"

                        [commands.decrement]
                        text = "&Decrement"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("CounterApp.toml:4:11: [commands.decrement] names no command of p.CounterApp");
    }

    @Test
    void checksTheCommandProperties() {
        var compilation = counter("""
                        [commands.increment]
                        text = "&In&crement"
                        shortcut = "shortcut NOPE NOPE"
                        icon = "icons/missing.png"
                        color = "red"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("CounterApp.toml:2:8: The text \"&In&crement\" has more" + " than one & mnemonic");
        assertThat(compilation)
                .hadErrorContaining("CounterApp.toml:3:12: The shortcut \"shortcut NOPE NOPE\" does not"
                        + " parse as a key stroke");
        assertThat(compilation).hadErrorContaining("The icon icons/missing.png names no resource of the package p");
        assertThat(compilation).hadErrorContaining("CounterApp.toml:5:1: color is not a property of a command");
    }

    @Test
    void requiresTheTextOfACommand() {
        var compilation =
                counter("[commands.increment]\nshortcut = \"shortcut I\"\n").compile();

        assertThat(compilation).failed().hadErrorContaining("The command increment has no text in CounterApp.toml");
    }

    @Test
    void checksTheTranslations() {
        var compilation = counter("""
                        title = "Counter"

                        [commands.increment]
                        text = "&Increment"
                        """).resource("p/CounterApp_fr.toml", """
                        [commands.increment]
                        text = "&Incrémenter"
                        tool-tip-text = "Ajoute un"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("CounterApp.toml:1:1: CounterApp_fr.toml lacks the key title");
        assertThat(compilation)
                .hadErrorContaining("CounterApp_fr.toml:3:1: The key commands.increment.tool-tip-text is not in the"
                        + " base file CounterApp.toml");
    }

    @Test
    void checksTheComponentFieldsAndTheClassProperties() {
        var compilation = counter("""
                        subtitle = "Nope"
                        theme = "purple"

                        [commands.increment]
                        text = "&Increment"

                        [label]
                        columns = 3
                        background = "blue"

                        [missing]
                        text = "Nope"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("CounterApp.toml:1:1: subtitle is not a property of" + " p.CounterApp");
        assertThat(compilation)
                .hadErrorContaining("CounterApp.toml:2:9: The theme \"purple\" is none of system, light" + " or dark");
        assertThat(compilation)
                .hadErrorContaining(
                        "CounterApp.toml:8:1: columns is not a supported property of" + " javax.swing.JLabel");
        assertThat(compilation).hadErrorContaining("CounterApp.toml:9:14: The color \"blue\" is not");
        assertThat(compilation)
                .hadErrorContaining(
                        "CounterApp.toml:11:2: [missing] names no non-private component" + " field of p.CounterApp");
    }

    @Test
    void rejectsTwoCommandsWithTheSameShortcut() {
        var compilation = Sources.create()
                .source("p.NotesView", """
                        package p;

                        public final class NotesView implements be.imgn.swing.ui.View {
                            public javax.swing.JComponent content() {
                                return new javax.swing.JPanel();
                            }

                            @be.imgn.swing.ui.Command
                            void first() {}

                            @be.imgn.swing.ui.Command
                            void second() {}
                        }
                        """)
                .resource("p/NotesView.toml", """
                        [commands.first]
                        text = "First"
                        shortcut = "shortcut F"

                        [commands.second]
                        text = "Second"
                        shortcut = "shortcut F"
                        """)
                .compile();

        assertThat(compilation).failed().hadErrorContaining("The commands first and second have the same shortcut");
    }
}
