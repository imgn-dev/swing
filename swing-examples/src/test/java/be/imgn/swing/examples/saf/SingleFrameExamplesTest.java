package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.TitledBorder;
import org.junit.jupiter.api.Test;

class SingleFrameExamplesTest {

    @Test
    void singleFrameExample1SaysHelloWorld() {
        try (AppTester<SingleFrameExample1> app = AppTester.start(SingleFrameExample1.class)) {
            JLabel label = app.component(JLabel.class);

            assertThat(label.getText()).isEqualTo("Hello World");
            assertThat(label.getFont().getSize()).isEqualTo(32);
            assertThat(app.title()).isEqualTo("SingleFrameExample1");
        }
    }

    @Test
    void singleFrameExample2TakesEverythingFromResources() {
        try (AppTester<SingleFrameExample2> app = AppTester.start(SingleFrameExample2.class)) {
            JLabel label = app.component(JLabel.class);

            assertThat(label.getText()).isEqualTo("Hello World");
            assertThat(label.getFont().getSize()).isEqualTo(48);
            assertThat(label.getIcon()).isNotNull();
            assertThat(label.getForeground()).isEqualTo(Color.WHITE);
            assertThat(label.getBackground()).isEqualTo(Color.BLACK);
            assertThat(label.isOpaque()).isTrue();
        }
    }

    @Test
    void singleFrameExample3ExitsOnceConfirmed() {
        try (AppTester<SingleFrameExample3> app = AppTester.start(SingleFrameExample3.class)) {
            JButton button = app.component(JButton.class);
            assertThat(app.title()).isEqualTo("ExitListener Demo");
            assertThat(button.getText()).isEqualTo("Click to Exit");
            assertThat(button.getIcon()).isNotNull();
            assertThat(button.getFont().getStyle()).isEqualTo(Font.ITALIC);
            assertThat(((TitledBorder) app.application().panel.getBorder()).getTitle())
                    .isEqualTo("border empty, title");

            app.dialogs().answerConfirm(false);
            app.commands(StandardCommands.class).exit().execute();
            assertThat(app.isRunning()).isTrue();

            app.dialogs().answerConfirm(true);
            app.commands(StandardCommands.class).exit().execute();
            assertThat(app.isRunning()).isFalse();
            assertThat(app.dialogs().confirmed()).containsExactly("Really exit?", "Really exit?");
        }
    }

    @Test
    void errorExampleShowsTheErrorDialog() {
        try (AppTester<ErrorExample> app = AppTester.start(ErrorExample.class)) {
            assertThat(app.component(JButton.class).getText()).isEqualTo("Try to exit");

            app.commands(ErrorExampleCommands.class).onButton().execute();

            assertThat(app.dialogs().errors()).containsExactly("Ah\nAh\nAh");
        }
    }
}
