package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import org.junit.jupiter.api.Test;

/** A command enabled while its view's form is dirty, through an exposed field assigned in the constructor. */
class FormCommandsTest {

    @Test
    void readsTheExposedObservableOfTheFormThatEnabledNames() {
        var compilation = Sources.create()
                .source("p.NotesView", """
                        package p;

                        import be.imgn.inject.Inject;
                        import be.imgn.swing.Observable;
                        import be.imgn.swing.ui.Command;
                        import be.imgn.swing.ui.Exposed;
                        import be.imgn.swing.ui.Form;
                        import be.imgn.swing.ui.View;
                        import javax.swing.JComponent;
                        import javax.swing.JPanel;

                        public final class NotesView implements View {
                            @Exposed final Observable<Boolean> canSave;

                            @Inject
                            NotesView(Form form) {
                                this.canSave = form.dirty();
                            }

                            @Override
                            public JComponent content() {
                                return new JPanel();
                            }

                            @Command(enabled = "canSave")
                            void save() {}
                        }
                        """)
                .resource("p/NotesView.toml", """
                        [commands.save]
                        text = "&Save"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.NotesViewCommands")
                .contains(".enabled(() -> owner.get().canSave)");
    }
}
