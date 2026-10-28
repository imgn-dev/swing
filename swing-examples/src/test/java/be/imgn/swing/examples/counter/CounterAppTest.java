package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import java.util.Locale;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class CounterAppTest {

    @Test
    void incrementShowsTheCount() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class, Locale.FRENCH)) {
            app.commands(CounterAppCommands.class).increment().execute();

            assertThat(app.component(JLabel.class).getText()).isEqualTo("Le compte est : 1.");
        }
    }

    @Test
    void exitAsksForConfirmation() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            app.dialogs().answerConfirm(false);

            app.commands(CounterAppCommands.class).exit().execute();

            assertThat(app.isRunning()).isTrue();
            assertThat(app.dialogs().confirmed()).containsExactly("Do you really want to exit?");
        }
    }

    @Test
    void exitsOnceConfirmed() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            app.commands(CounterAppCommands.class).exit().execute();

            assertThat(app.isRunning()).isFalse();
            assertThat(app.exitStatus()).isZero();
        }
    }

    @Test
    void titlesTheWindowWithTheDisplayedName() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            assertThat(app.title()).isEqualTo("Counter");
            assertThat(app.component(JLabel.class).getText()).isEqualTo("The count is: 0.");
        }
    }
}
