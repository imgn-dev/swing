package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import org.junit.jupiter.api.Test;

class ViewsStepTest {

    @Test
    void generatesTheInjectorAndTheState() {
        var compilation = Sources.create()
                .source("p.NotesView", """
                        package p;

                        import javax.swing.*;

                        public final class NotesView implements be.imgn.swing.ui.View {
                            final JTextArea editor = new JTextArea();
                            final JPanel panel = new JPanel();
                            final JSplitPane split = new JSplitPane();
                            private final JTable hidden = new JTable();

                            public JComponent content() {
                                return panel;
                            }
                        }
                        """)
                .resource("p/NotesView.toml", """
                        [editor]
                        font = "Monospaced-14"
                        accessible-name = "Note text"
                        rows = 4

                        [panel]
                        title = "Notes"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadWarningContaining("The state of the private field hidden is not saved");
        assertThat(compilation)
                .generatedSourceFile("p.$NotesView$$Injector")
                .contains("public final class $NotesView$$Injector implements ComponentInjector<NotesView>")
                .contains("resources.font(\"editor\", \"font\").ifPresent(value -> view.editor.setFont(value));")
                .contains("resources.integer(\"editor\", \"rows\").ifPresent(value -> view.editor.setRows(value));")
                .contains("ComponentResources.setAccessibleName(view.editor, value)")
                .contains("ComponentResources.setTitle(view.panel, value)")
                .contains("ComponentResources.setTabTitle(view.panel, value)");
        assertThat(compilation)
                .generatedSourceFile("p.$NotesView$$State")
                .contains("components.put(\"split\", view.split);")
                .doesNotContain("hidden");
    }

    @Test
    void warnsOnAnInheritedComponentTheStateCannotReach() {
        var compilation = Sources.create()
                .source("p.Base", """
                        package p;

                        public class Base {
                            javax.swing.JSplitPane split = new javax.swing.JSplitPane();
                            public javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
                        }
                        """)
                .source("q.NotesView", """
                        package q;

                        public final class NotesView extends p.Base implements be.imgn.swing.ui.View {
                            public javax.swing.JComponent content() {
                                return tabs;
                            }
                        }
                        """)
                .compile();

        assertThat(compilation)
                .succeeded()
                .hadWarningContaining("split of p.Base is not public, so the state of q.NotesView can't save it")
                .inFile("q/NotesView.java");
        assertThat(compilation)
                .generatedSourceFile("q.$NotesView$$State")
                .contains("components.put(\"tabs\", view.tabs);")
                .doesNotContain("split");
    }

    @Test
    void registersTheOverridesOfTheViewClass() {
        var compilation = Sources.create().source("p.NotesView", """
                        package p;

                        public final class NotesView implements be.imgn.swing.ui.View {
                            public javax.swing.JComponent content() {
                                return new javax.swing.JPanel();
                            }

                            @be.imgn.swing.ui.Command
                            void exit() {}
                        }
                        """).compile();

        assertThat(compilation)
                .generatedSourceFile("p.$NotesView$$Injector")
                .contains("registry.override(\"exit\", NotesViewCommands.class);");
    }

    @Test
    void rejectsAWiderScopeThatDependsDirectlyOnANarrowerOne() {
        var compilation = Sources.create().source("p.Registry", """
                        package p;

                        @be.imgn.inject.Singleton
                        public final class Registry {
                            @be.imgn.inject.Inject
                            Registry(be.imgn.swing.ui.Ui ui, be.imgn.inject.Provider<be.imgn.swing.ui.Ui> later) {}
                        }
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("@Singleton p.Registry depends directly on @ViewScoped be.imgn.swing.ui.Ui")
                .onLine(6);
    }

    @Test
    void checksTheInjectionPointsInheritedFromAnUnscopedSuperclass() {
        var compilation =
                Sources.create().source("p.Base", """
                        package p;

                        public class Base {
                            @be.imgn.inject.Inject
                            be.imgn.swing.ui.Ui ui;

                            @be.imgn.inject.Inject
                            void setLater(be.imgn.inject.Provider<be.imgn.swing.ui.Ui> later) {}
                        }
                        """).source("p.Registry", """
                        package p;

                        @be.imgn.inject.Singleton
                        public final class Registry extends Base {}
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("@Singleton p.Registry depends directly on @ViewScoped be.imgn.swing.ui.Ui")
                .inFile("p/Base.java")
                .onLine(5);
    }
}
