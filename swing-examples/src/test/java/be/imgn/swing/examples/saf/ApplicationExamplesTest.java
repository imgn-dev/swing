package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class ApplicationExamplesTest {

    @Test
    void applicationExample1SaysHelloWorld() {
        try (AppTester<ApplicationExample1> app = AppTester.start(ApplicationExample1.class)) {
            assertThat(app.component(JLabel.class).getText()).isEqualTo("Hello World");
        }
    }

    @Test
    void applicationExample2BuildsItsTitleWithReferences() {
        try (AppTester<ApplicationExample2> app = AppTester.start(ApplicationExample2.class)) {
            assertThat(app.title()).isEqualTo("Application Example: Hello World with resources (version 1.0)");
            assertThat(app.info().displayedName()).isEqualTo("Application Example: Hello World with resources");
            assertThat(app.component(JLabel.class).getText()).isEqualTo("Hello World");
        }
    }

    @Test
    void exitExample1AsksBeforeExiting() {
        try (AppTester<ExitExample1> app = AppTester.start(ExitExample1.class)) {
            app.dialogs().answerConfirm(false);

            app.commands(StandardCommands.class).closeWindow().execute();

            assertThat(app.isRunning()).isTrue();
            assertThat(app.dialogs().confirmed()).containsExactly("Really exit?");
        }
    }
}
