package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class ResourcesStepTest {

    private static final String TWO_COMMANDS = """
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
            """;

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
    void listsTheIconsOfAClassDeferredToALaterRound() {
        // The view injects its generated menus class, so it is resolved, and its resources checked, a round later
        // than the application class of its package.
        var compilation = counter("""
                        [commands.increment]
                        text = "&Increment"
                        icon = "icons/plus.png"
                        """)
                .source("p.NotesView", """
                        package p;

                        public final class NotesView implements be.imgn.swing.ui.View {
                            @be.imgn.swing.ui.Exposed javax.swing.JLabel label = new javax.swing.JLabel();

                            @be.imgn.inject.Inject
                            NotesView(NotesViewMenus menus) {}

                            public javax.swing.JComponent content() {
                                return label;
                            }
                        }
                        """)
                .resource("p/NotesView.toml", """
                        [menus.notes]
                        text = "&Notes"

                        [label]
                        icon = "icons/notes.png"
                        """)
                .resource("p/icons/plus.png", new byte[] {1, 2, 3})
                .resource("p/icons/notes.png", new byte[] {1, 2, 3})
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$$Resources")
                .contains("return Set.of(\"icons/notes.png\", \"icons/plus.png\");");
    }

    @Test
    void warnsOnAFileOfAClassTheProcessorGeneratesNothingFor() {
        var compilation = counter("[commands.increment]\ntext = \"&Increment\"\n")
                .source("p.Helper", "package p;\n\npublic final class Helper {}\n")
                .resource("p/Helper.toml", "title = \"Helper\"\n")
                .compile();

        assertThat(compilation)
                .succeeded()
                .hadWarningContaining("Helper.toml belongs to no class the processor generates for")
                .inFile("p/Helper.java");
    }

    @Test
    void resolvesTheReferencesOfAnIconInItsFile() {
        var compilation = counter("""
                        [commands.increment]
                        text = "&Increment"
                        icon = "icons/plus.png"

                        [label]
                        icon = "${commands.increment.icon}"
                        """)
                .resource("p/CounterApp_fr.toml", """
                        [commands.increment]
                        text = "&Augmenter"
                        icon = "${commands.missing.icon}"

                        [label]
                        icon = "icons/${commands.increment.text}.png"
                        """)
                .resource("p/icons/plus.png", new byte[] {1, 2, 3})
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("CounterApp_fr.toml:6:8: The icon icons/&Augmenter.png"
                        + " (icons/${commands.increment.text}.png) names no resource of the package p");
        assertThat(compilation)
                .hadWarningContaining("CounterApp_fr.toml:3:8: The reference ${commands.missing.icon} names no key of"
                        + " this file: the icon is not checked");
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
                        "CounterApp.toml:11:2: [missing] names no @Exposed component field of p.CounterApp");
    }

    @Test
    void checksTheVariantsOfADialog() {
        var compilation = Sources.create()
                .source("p.Panel", """
                        package p;

                        import be.imgn.inject.Inject;
                        import be.imgn.swing.ui.DialogView;
                        import be.imgn.swing.ui.Exposed;
                        import be.imgn.swing.ui.Variants;
                        import java.util.Optional;
                        import javax.swing.JComponent;
                        import javax.swing.JLabel;

                        public final class Panel implements DialogView<Void> {
                            @Exposed
                            final JLabel label = new JLabel();

                            @Inject
                            Panel() {}

                            public JComponent content() {
                                return label;
                            }

                            public Optional<Void> result() {
                                return Optional.empty();
                            }

                            @Variants
                            public enum Name { FIRST, SECOND_ONE }
                        }
                        """)
                .resource("p/Panel.toml", """
                        [label]
                        font = "Monospaced-14"

                        [variants.first]
                        title = "First"

                        [variants.first.label]
                        text = "One"

                        [variants.second-one]
                        title = "Second"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void rejectsVariantsThatMatchNoConstant() {
        var compilation = Sources.create()
                .source("p.Panel", """
                        package p;

                        import be.imgn.inject.Inject;
                        import be.imgn.swing.ui.DialogView;
                        import be.imgn.swing.ui.Exposed;
                        import be.imgn.swing.ui.Variants;
                        import java.util.Optional;
                        import javax.swing.JComponent;
                        import javax.swing.JLabel;

                        public final class Panel implements DialogView<Void> {
                            @Exposed
                            final JLabel label = new JLabel();

                            @Inject
                            Panel() {}

                            public JComponent content() {
                                return label;
                            }

                            public Optional<Void> result() {
                                return Optional.empty();
                            }

                            @Variants
                            public enum Name { FIRST, SECOND_ONE }
                        }
                        """)
                .source("p.Plain", """
                        package p;

                        @be.imgn.swing.ui.Variants
                        public final class Plain {}
                        """)
                .resource("p/Panel.toml", """
                        [variants.first]
                        title = "First"

                        [variants.first.commands.cancel]
                        text = "No"

                        [variants.first.missing]
                        text = "None"

                        [variants.third]
                        title = "Third"
                        """)
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("[variants.third] names no constant of the @Variants enum of p.Panel");
        assertThat(compilation).hadErrorContaining("The variant second-one has no table [variants.second-one]");
        assertThat(compilation).hadErrorContaining("A variant holds no commands");
        assertThat(compilation).hadErrorContaining("[missing] names no @Exposed component field of p.Panel");
        assertThat(compilation).hadErrorContaining("@Variants marks an enum nested in a dialog view");
    }

    @Test
    void acceptsTheAccessiblePropertiesOnAnyComponent() {
        var compilation = Sources.create()
                .source("p.NotesView", """
                        package p;

                        public final class NotesView implements be.imgn.swing.ui.View {
                            @be.imgn.swing.ui.Exposed java.awt.Component plain = new java.awt.Canvas();

                            public javax.swing.JComponent content() {
                                return new javax.swing.JPanel();
                            }
                        }
                        """)
                .resource("p/NotesView.toml", """
                        [plain]
                        accessible-name = "Plain"
                        accessible-description = "A plain component"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$NotesView$$Injector")
                .contains("ComponentResources.setAccessibleName(view.plain, value)")
                .contains("ComponentResources.setAccessibleDescription(view.plain, value)");
    }

    @Test
    void rejectsTwoCommandsWithTheSameShortcut() {
        var compilation = Sources.create()
                .source("p.NotesView", TWO_COMMANDS)
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

    @Test
    void rejectsAShortcutAPlatformFileSetsToAnotherCommands() {
        var compilation = Sources.create()
                .source("p.NotesView", TWO_COMMANDS)
                .resource("p/NotesView.toml", """
                        [commands.first]
                        text = "First"
                        shortcut = "shortcut F"

                        [commands.second]
                        text = "Second"
                        shortcut = "shortcut S"
                        """)
                .resource("p/NotesView.macos.toml", """
                        [commands.second]
                        shortcut = "shortcut F"
                        """)
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("NotesView.macos.toml:2:12: The commands first and second have the same shortcut"
                        + " shortcut F (first in NotesView.toml)");
    }

    @Test
    void keepsTheShortcutModifierApartFromControlAndMeta() {
        var compilation = Sources.create()
                .source("p.NotesView", TWO_COMMANDS)
                .resource("p/NotesView.toml", """
                        [commands.first]
                        text = "First"
                        shortcut = "shortcut F"

                        [commands.second]
                        text = "Second"
                        shortcut = "ctrl F"
                        """)
                .resource("p/NotesView.windows.toml", """
                        [commands.second]
                        shortcut = "meta F"
                        """)
                .compile();

        assertThat(compilation).succeeded();
    }
}
