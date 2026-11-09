package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import java.awt.Color;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.TitledBorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void singleFrameExample4LoadsTheFileItsChooserGives(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("page.txt");
        Files.writeString(file, "Chosen text");
        try (AppTester<SingleFrameExample4> app = AppTester.start(SingleFrameExample4.class)) {
            app.dialogs().answerOpenFile(file);

            app.commands(SingleFrameExample4Commands.class).open().execute();

            assertThat(app.dialogs().fileChoices()).hasSize(1);
            for (int attempt = 0;
                    attempt < 500
                            && !app.call(() -> app.application().textPane().getText())
                                    .contains("Chosen text");
                    attempt++) {
                Thread.sleep(10);
            }
            assertThat(app.call(() -> app.application().textPane().getText())).contains("Chosen text");
        }
    }

    @Test
    void everyExampleWithoutANameIsNamedAfterItsClass() {
        List<Class<?>> unnamed = List.of(
                ActionExample2.class,
                ActionExample3.class,
                ActionExample4.class,
                ActionMapExample.class,
                ApplicationExample1.class,
                ApplicationExample2.class,
                BlockingExample1.class,
                DocumentExample.class,
                ErrorExample.class,
                ExitExample1.class,
                LocalStorageExample1.class,
                PeriodicTaskExample.class,
                SelectedPropertyExample.class,
                SessionStorageExample1.class,
                SingleFrameExample1.class,
                SingleFrameExample2.class,
                SingleFrameExample3.class,
                SingleFrameExample4.class,
                SingleFrameExample5.class,
                SingleFrameExample6.class,
                Tabbed.class);
        for (Class<?> example : unnamed) {
            try (AppTester<?> app = AppTester.start(example)) {
                assertThat(app.info().name()).as(example.getName()).isEqualTo(example.getSimpleName());
            }
        }
    }
}
