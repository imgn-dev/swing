package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import org.junit.jupiter.api.Test;

class CommandsStepTest {

    private static Sources view(String body) {
        return Sources.create().source("p.NotesView", """
                package p;

                import be.imgn.swing.Observable;
                import be.imgn.swing.Property;
                import be.imgn.swing.Task;
                import be.imgn.swing.ui.Block;
                import be.imgn.swing.ui.Command;
                import be.imgn.swing.ui.CommandContext;
                import be.imgn.swing.ui.Exposed;
                import be.imgn.swing.ui.View;
                import javax.swing.JComponent;
                import javax.swing.JPanel;

                public final class NotesView implements View {
                    @Exposed final Property<Boolean> dirty = Property.of(false);
                    @Exposed final Property<Boolean> wordWrap = Property.of(false);

                    @Override
                    public JComponent content() {
                        return new JPanel();
                    }

                %s
                }
                """.formatted(body));
    }

    @Test
    void generatesTheCommandsClass() {
        var compilation = view("""
                    @Command
                    void increment() {}

                    @Command(id = "save", enabled = "dirty", block = Block.WINDOW)
                    Task<String> save(CommandContext context) {
                        return Task.of(() -> "saved");
                    }

                    @Command(selected = "wordWrap")
                    void toggleWordWrap() {}
                """).resource("p/NotesView.toml", """
                        [commands.increment]
                        text = "&Increment"

                        [commands.save]
                        text = "&Save"
                        shortcut = "shortcut S"

                        [commands.toggleWordWrap]
                        text = "&Wrap"
                        """).compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.NotesViewCommands")
                .contains("@ViewScoped")
                .contains("public final class NotesViewCommands extends StandardCommands")
                .contains("NotesViewCommands(CommandRegistry registry, Provider<NotesView> owner)")
                .contains("super(registry);")
                .contains("this.increment = registry.command(NotesView.class, \"increment\")")
                .contains(".handler(context -> owner.get().increment())")
                .contains(".taskHandler(context -> owner.get().save(context))")
                .contains(".enabled(() -> owner.get().dirty)")
                .contains(".block(Block.WINDOW)")
                .contains(".selected(() -> owner.get().wordWrap)")
                .contains("public BoundCommand toggleWordWrap()");
    }

    @Test
    void warnsAboutAnOverrideOfAStandardCommand() {
        var compilation = view("""
                    @Command
                    void exit() {}
                """).compile();

        assertThat(compilation)
                .succeeded()
                .hadWarningContaining("Command `exit` of `p.NotesView` overrides the standard command `exit`."
                        + " Overriding standard commands may become an error in a future version.");
        assertThat(compilation).generatedSourceFile("p.NotesViewCommands").contains("public BoundCommand exit()");
    }

    @Test
    void warnsThatAnOverrideOutsideAViewAppliesNowhere() {
        var compilation = Sources.create().source("p.Helper", """
                        package p;

                        @be.imgn.inject.Singleton
                        public class Helper {
                            @be.imgn.swing.ui.Command
                            void exit() {}
                        }
                        """).compile();

        assertThat(compilation)
                .succeeded()
                .hadWarningContaining("Command `exit` of `p.Helper` overrides the standard command `exit`, but"
                        + " `p.Helper` is not a view: the override applies nowhere");
        assertThat(compilation).generatedSourceFile("p.HelperCommands").contains("public BoundCommand exit()");
    }

    @Test
    void addsTheStandardMethodToAnOverrideWithAnotherName() {
        var compilation = view("""
                    @Command(id = "exit")
                    void quit() {}
                """).compile();

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("p.NotesViewCommands")
                .contains("public BoundCommand quit()")
                .contains("@Override\n    public BoundCommand exit() {\n        return quit;");
    }

    @Test
    void rejectsAHandlerNamedLikeAStandardCommandWithAnotherId() {
        var compilation = view("""
                    @Command(id = "leave")
                    void exit() {}
                """)
                .resource("p/NotesView.toml", "[commands.leave]\ntext = \"Leave\"\n")
                .compile();

        assertThat(compilation).failed().hadErrorContaining("is named like the standard command exit");
    }

    @Test
    void rejectsInvalidHandlers() {
        var compilation = view("""
                    @Command
                    private void hidden() {}

                    @Command
                    void withParameter(String text) {}

                    @Command(block = Block.COMMAND)
                    void blocking() {}
                """).resource("p/NotesView.toml", """
                        [commands.hidden]
                        text = "Hidden"
                        [commands.withParameter]
                        text = "With"
                        [commands.blocking]
                        text = "Blocking"
                        """).compile();

        assertThat(compilation).failed().hadErrorContaining("must be neither private nor static");
        assertThat(compilation).hadErrorContaining("takes no parameter, or a single CommandContext");
        assertThat(compilation)
                .hadErrorContaining("block = Block.COMMAND applies only to a handler that returns a Task");
    }

    @Test
    void checksTheEnabledAndSelectedTargets() {
        var compilation = view("""
                    @Command(enabled = "missing")
                    void first() {}

                    @Command(selected = "dirtyView")
                    void second() {}

                    Observable<Boolean> dirtyView() {
                        return dirty;
                    }

                    final Observable<Boolean> plain = Property.of(true);

                    @Command(enabled = "plain")
                    void third() {}
                """).resource("p/NotesView.toml", """
                        [commands.first]
                        text = "First"
                        [commands.second]
                        text = "Second"
                        [commands.third]
                        text = "Third"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("enabled = \"missing\" names no field or no-argument method");
        assertThat(compilation).hadErrorContaining("selected = \"dirtyView\" must be of type Property<Boolean>");
        assertThat(compilation)
                .hadErrorContaining("enabled = \"plain\" names a field that is not @Exposed: generated code reads only"
                        + " @Exposed fields");
    }

    @Test
    void requiresAScope() {
        var compilation = Sources.create()
                .source("p.Actions", """
                        package p;

                        public class Actions {
                            @be.imgn.swing.ui.Command
                            void go() {}
                        }
                        """)
                .resource("p/Actions.toml", "[commands.go]\ntext = \"Go\"\n")
                .compile();

        assertThat(compilation).failed().hadErrorContaining("declares commands but has no scope");
    }

    @Test
    void rejectsAGeneratedNameThatAlreadyExists() {
        var compilation = view("""
                    @Command
                    void increment() {}
                """)
                .source("p.NotesViewCommands", "package p;\n\npublic class NotesViewCommands {}\n")
                .resource("p/NotesView.toml", "[commands.increment]\ntext = \"Increment\"\n")
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("The class p.NotesViewCommands that p.NotesView generates already exists");
    }

    @Test
    void namesNestedClassesAfterTheirChain() {
        var compilation = Sources.create()
                .source("p.Outer", """
                        package p;

                        public class Outer {
                            @be.imgn.inject.Singleton
                            public static class Inner {
                                @be.imgn.swing.ui.Command
                                void go() {}
                            }
                        }
                        """)
                .resource("p/Inner.toml", "[commands.go]\ntext = \"Go\"\n")
                .compile();

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("p.OuterInnerCommands").contains("Provider<Outer.Inner> owner");
    }
}
