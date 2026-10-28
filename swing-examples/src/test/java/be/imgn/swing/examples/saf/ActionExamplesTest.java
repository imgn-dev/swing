package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

class ActionExamplesTest {

    @Test
    void actionExample2SetsAndClearsTheTitle() {
        try (AppTester<ActionExample2> app = AppTester.start(ActionExample2.class)) {
            ActionExample2Commands commands = app.commands(ActionExample2Commands.class);
            assertThat(app.components(JButton.class))
                    .extracting(JButton::getText)
                    .containsExactly("Clear Window's Title", "Set Window Title");

            commands.updateTitle().execute();
            assertThat(app.title()).isEqualTo("<Enter the window title here>");

            commands.clearTitle().execute();
            assertThat(app.title()).isEmpty();
        }
    }

    @Test
    void actionExample3EnablesClearingOnceATitleIsSet() {
        try (AppTester<ActionExample3> app = AppTester.start(ActionExample3.class)) {
            ActionExample3Commands commands = app.commands(ActionExample3Commands.class);
            assertThat(commands.clearTitle().execute()).isFalse();
            assertThat(commands.clearTitle().asAction().isEnabled()).isFalse();

            assertThat(commands.updateTitle().execute()).isTrue();

            assertThat(commands.clearTitle().asAction().isEnabled()).isTrue();
            assertThat(commands.clearTitle().execute()).isTrue();
            assertThat(app.title()).isEmpty();
            assertThat(commands.clearTitle().asAction().isEnabled()).isFalse();
        }
    }
}
