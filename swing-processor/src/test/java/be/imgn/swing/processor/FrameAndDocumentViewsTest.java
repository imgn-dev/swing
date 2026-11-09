package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import org.junit.jupiter.api.Test;

/** The checks of milestone 3: titles and variants of frame views, document views and their commands. */
class FrameAndDocumentViewsTest {

    private static final String FRAME_VIEW = """
            package p;

            public final class Board implements be.imgn.swing.ui.FrameView {
                @be.imgn.inject.Inject
                Board() {}

                public javax.swing.JComponent content() {
                    return new javax.swing.JPanel();
                }

                @be.imgn.swing.ui.Variants
                public enum Kind { SMALL, LARGE }
            }
            """;

    private static final String DOCUMENT_VIEW = """
            package p;

            public final class Pad implements be.imgn.swing.ui.FrameView, be.imgn.swing.desktop.DocumentView<String> {
                @be.imgn.inject.Inject
                Pad() {}

                public javax.swing.JComponent content() {
                    return new javax.swing.JPanel();
                }

                public be.imgn.swing.desktop.DocumentFormat<String> format() {
                    return be.imgn.swing.desktop.DocumentFormat.text(
                            new be.imgn.swing.ui.FileType("Text", java.util.List.of("txt")));
                }

                public String toSave() {
                    return "";
                }

                public void loaded(String contents) {}
            }
            """;

    @Test
    void acceptsTheTitleAndTheVariantsOfAFrameView() {
        var compilation = Sources.create()
                .source("p.Board", FRAME_VIEW)
                .resource("p/Board.toml", """
                        title = "Board"

                        [variants.small]
                        title = "Small board"

                        [variants.large]
                        title = "Large board"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void acceptsTheDocumentCommandsInTheResourcesOfADocumentView() {
        var compilation = Sources.create()
                .source("p.Pad", DOCUMENT_VIEW)
                .resource("p/Pad.toml", """
                        [commands.save]
                        text = "&Save the pad"

                        [commands.newDocument]
                        text = "&New pad"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void rejectsTheTitleOfADocumentView() {
        var compilation = Sources.create()
                .source("p.Pad", DOCUMENT_VIEW)
                .resource("p/Pad.toml", "title = \"Pad\"\n")
                .compile();

        assertThat(compilation).failed().hadErrorContaining("title is not a property of p.Pad");
        assertThat(compilation).hadErrorContaining("not to document views");
    }

    @Test
    void rejectsTheDocumentCommandsInTheResourcesOfAnotherView() {
        var compilation = Sources.create()
                .source("p.Board", FRAME_VIEW)
                .resource("p/Board.toml", """
                        [commands.save]
                        text = "&Save"

                        [variants.small]
                        [variants.large]
                        """)
                .compile();

        assertThat(compilation).failed().hadErrorContaining("[commands.save] names no command of p.Board");
    }

    @Test
    void rejectsADocumentViewThatIsNotAFrameView() {
        var compilation = Sources.create()
                .source("p.Pad", DOCUMENT_VIEW.replace("be.imgn.swing.ui.FrameView, ", ""))
                .compile();

        assertThat(compilation).failed().hadErrorContaining("A document view is shown in a frame of its own");
    }
}
