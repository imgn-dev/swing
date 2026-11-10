package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The checks of milestone 5 for wizards: the step titles of component fields, and the wizard's commands in the
 * resources of a view whose constructor takes a {@code Wizard}, which the processor knows by name, as a stub stands for
 * it here.
 */
class WizardChecksTest {

    private static final String WIZARD = """
            package be.imgn.swing.wizard;

            public final class Wizard {}
            """;

    private static final String STEPS = """
            package p;

            public final class Steps implements be.imgn.swing.ui.DialogView<String> {
                @be.imgn.swing.ui.Exposed
                final javax.swing.JPanel identity = new javax.swing.JPanel();

                @be.imgn.inject.Inject
                Steps(be.imgn.swing.wizard.Wizard wizard) {}

                public javax.swing.JComponent content() {
                    return identity;
                }

                public java.util.Optional<String> result() {
                    return java.util.Optional.empty();
                }
            }
            """;

    private static final String PLAIN = """
            package p;

            public final class Plain implements be.imgn.swing.ui.DialogView<String> {
                @be.imgn.swing.ui.Exposed
                final javax.swing.JPanel identity = new javax.swing.JPanel();

                @be.imgn.inject.Inject
                Plain() {}

                public javax.swing.JComponent content() {
                    return identity;
                }

                public java.util.Optional<String> result() {
                    return java.util.Optional.empty();
                }
            }
            """;

    @Test
    void acceptsTheWizardCommandsAndTheStepTitlesOfAViewThatTakesAWizard() {
        var compilation = Sources.create()
                .source("be.imgn.swing.wizard.Wizard", WIZARD)
                .source("p.Steps", STEPS)
                .resource("p/Steps.toml", """
                        [identity]
                        step-title = "Identity"

                        [commands.previous]
                        text = "< &Back"

                        [commands.next]
                        text = "&Forward >"
                        """)
                .resource("p/Steps_fr.toml", """
                        [identity]
                        step-title = "Identité"

                        [commands.previous]
                        text = "< &Retour"

                        [commands.next]
                        text = "&En avant >"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void rejectsTheWizardCommandsInTheResourcesOfAViewThatTakesNoWizard() {
        var compilation = Sources.create()
                .source("be.imgn.swing.wizard.Wizard", WIZARD)
                .source("p.Plain", PLAIN)
                .resource("p/Plain.toml", """
                        [commands.next]
                        text = "&Next >"
                        """)
                .compile();

        assertThat(compilation).failed().hadErrorContaining("[commands.next] names no command of p.Plain");
    }

    @Test
    void acceptsAStepTitleOnAnyComponentField() {
        var compilation = Sources.create()
                .source("p.Plain", PLAIN)
                .resource("p/Plain.toml", """
                        [identity]
                        step-title = "Identity"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void rejectsAStepTitleThatIsNotAString() {
        var compilation = Sources.create()
                .source("p.Plain", PLAIN)
                .resource("p/Plain.toml", """
                        [identity]
                        step-title = 1
                        """)
                .compile();

        assertThat(compilation).failed().hadErrorContaining("step-title must be a string");
    }

    @Test
    void requiresTheStepTitleInEveryLanguageFile() {
        var compilation = Sources.create()
                .source("p.Plain", PLAIN)
                .resource("p/Plain.toml", """
                        [identity]
                        step-title = "Identity"
                        """)
                .resource("p/Plain_fr.toml", "")
                .compile();

        assertThat(compilation).failed().hadErrorContaining("Plain_fr.toml lacks the key identity.step-title");
    }
}
