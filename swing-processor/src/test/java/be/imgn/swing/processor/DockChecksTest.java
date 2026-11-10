package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The checks of milestone 5 for dock views: their class properties, the views they can't also be, and their assisted
 * factories. The processor knows {@code DockView} by name, as a stub stands for it here.
 */
class DockChecksTest {

    private static final String DOCK_VIEW = """
            package be.imgn.swing.docking;

            public interface DockView extends be.imgn.swing.ui.View {}
            """;

    private static final String LIST = """
            package p;

            public final class ListView implements be.imgn.swing.docking.DockView {
                @be.imgn.inject.Inject
                ListView() {}

                public javax.swing.JComponent content() {
                    return new javax.swing.JPanel();
                }
            }
            """;

    @Test
    void acceptsTheTitleAndTheIconOfADockView() {
        var compilation = Sources.create()
                .source("be.imgn.swing.docking.DockView", DOCK_VIEW)
                .source("p.ListView", LIST)
                .resource("p/ListView.toml", """
                        title = "Contacts List"
                        icon = "icons/list.png"
                        """)
                .resource("p/icons/list.png", "")
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void rejectsAnIconOfADockViewThatNamesNoResource() {
        var compilation = Sources.create()
                .source("be.imgn.swing.docking.DockView", DOCK_VIEW)
                .source("p.ListView", LIST)
                .resource("p/ListView.toml", "icon = \"icons/missing.png\"\n")
                .compile();

        assertThat(compilation).failed().hadErrorContaining("The icon icons/missing.png names no resource");
    }

    @Test
    void rejectsATitleThatIsNotAString() {
        var compilation = Sources.create()
                .source("be.imgn.swing.docking.DockView", DOCK_VIEW)
                .source("p.ListView", LIST)
                .resource("p/ListView.toml", "title = 1\n")
                .compile();

        assertThat(compilation).failed().hadErrorContaining("title must be a string");
    }

    @Test
    void rejectsAnIconThatIsNotAString() {
        var compilation = Sources.create()
                .source("be.imgn.swing.docking.DockView", DOCK_VIEW)
                .source("p.ListView", LIST)
                .resource("p/ListView.toml", "icon = 1\n")
                .compile();

        assertThat(compilation).failed().hadErrorContaining("icon must be a string");
    }

    @Test
    void rejectsADockViewThatIsAlsoADialogView() {
        var compilation = Sources.create()
                .source("be.imgn.swing.docking.DockView", DOCK_VIEW)
                .source("p.Picker", """
                        package p;

                        public final class Picker
                                implements be.imgn.swing.docking.DockView, be.imgn.swing.ui.DialogView<String> {
                            @be.imgn.inject.Inject
                            Picker() {}

                            public javax.swing.JComponent content() {
                                return new javax.swing.JPanel();
                            }

                            public java.util.Optional<String> result() {
                                return java.util.Optional.empty();
                            }
                        }
                        """)
                .compile();

        assertThat(compilation).failed().hadErrorContaining("A dock view is shown in its frame's dock");
    }

    @Test
    void rejectsADockViewThatIsAlsoAFrameView() {
        var compilation = Sources.create()
                .source("be.imgn.swing.docking.DockView", DOCK_VIEW)
                .source(
                        "p.ListView",
                        LIST.replace(
                                "implements be.imgn.swing.docking.DockView",
                                "implements be.imgn.swing.docking.DockView, be.imgn.swing.ui.FrameView"))
                .compile();

        assertThat(compilation).failed().hadErrorContaining("A dock view is shown in its frame's dock");
    }

    @Test
    void generatesTheAssistedFactoryOfADockView() {
        var compilation = Sources.create()
                .source("be.imgn.swing.docking.DockView", DOCK_VIEW)
                .source("p.PictureView", """
                        package p;

                        public final class PictureView implements be.imgn.swing.docking.DockView {
                            @be.imgn.inject.Inject
                            PictureView(@be.imgn.inject.Assisted String name) {}

                            public javax.swing.JComponent content() {
                                return new javax.swing.JPanel();
                            }
                        }
                        """)
                .source("p.Opener", """
                        package p;

                        final class Opener {
                            PictureView open(PictureViewFactory factory) {
                                return factory.create("Ada");
                            }
                        }
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }
}
