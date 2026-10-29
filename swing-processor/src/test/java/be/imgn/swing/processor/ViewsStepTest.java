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
                            @be.imgn.swing.ui.Exposed final JTextArea editor = new JTextArea();
                            @be.imgn.swing.ui.Exposed final JPanel panel = new JPanel();
                            @be.imgn.swing.ui.Exposed final JSplitPane split = new JSplitPane();
                            final JTable plain = new JTable();
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

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$NotesView$$Injector")
                .contains("public final class $NotesView$$Injector implements ComponentInjector<NotesView>")
                .contains("resources.font(\"editor\", \"font\").ifPresent(value -> view.editor.setFont(value));")
                .contains("resources.integer(\"editor\", \"rows\").ifPresent(value -> view.editor.setRows(value));")
                .contains("ComponentResources.setAccessibleName(view.editor, value)")
                .contains("ComponentResources.setTitle(view.panel, value)")
                .contains("ComponentResources.setTabTitle(view.panel, value)")
                .doesNotContain("plain")
                .doesNotContain("hidden");
        assertThat(compilation)
                .generatedSourceFile("p.$NotesView$$State")
                .contains("components.put(\"split\", view.split);")
                .doesNotContain("plain")
                .doesNotContain("hidden");
    }

    @Test
    void warnsOnAnInheritedComponentTheStateCannotReach() {
        var compilation = Sources.create()
                .source("p.Base", """
                        package p;

                        public class Base {
                            @be.imgn.swing.ui.Exposed
                            javax.swing.JSplitPane split = new javax.swing.JSplitPane();

                            @be.imgn.swing.ui.Exposed
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
    void rejectsExposedFieldsThatGeneratedCodeCannotRead() {
        var compilation = Sources.create().source("p.Holder", """
                        package p;

                        import be.imgn.swing.Observable;
                        import be.imgn.swing.Property;
                        import be.imgn.swing.ui.Exposed;

                        public final class Holder {
                            @Exposed private final javax.swing.JLabel hidden = new javax.swing.JLabel();
                            @Exposed static javax.swing.JLabel shared = new javax.swing.JLabel();
                            @Exposed final String text = "";
                            @Exposed final Observable<Integer> count = Property.of(0);
                            @Exposed final Property<Boolean> enabled = Property.of(true);
                            @Exposed final javax.swing.JButton button = new javax.swing.JButton();
                        }
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("An @Exposed field must be neither private nor static")
                .inFile("p/Holder.java")
                .onLine(8);
        assertThat(compilation)
                .hadErrorContaining("An @Exposed field must be neither private nor static")
                .inFile("p/Holder.java")
                .onLine(9);
        assertThat(compilation)
                .hadErrorContaining("An @Exposed field must be a java.awt.Component or an Observable<Boolean>, not"
                        + " java.lang.String");
        assertThat(compilation)
                .hadErrorContaining("An @Exposed field must be a java.awt.Component or an Observable<Boolean>, not"
                        + " be.imgn.swing.Observable<java.lang.Integer>");
        assertThat(compilation).hadErrorCount(4);
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
