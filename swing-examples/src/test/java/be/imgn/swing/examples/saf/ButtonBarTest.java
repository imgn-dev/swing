package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.Ui;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import org.junit.jupiter.api.Test;

/** The rows of buttons that {@link Ui#buttonBar} builds, on the commands of the selected property example. */
class ButtonBarTest {

    @Test
    void holdsOneButtonPerCommandInOrderInACenteredFlowLayout() throws Exception {
        try (AppTester<SelectedPropertyExample> app = AppTester.start(SelectedPropertyExample.class)) {
            SelectedPropertyExampleCommands commands = app.commands(SelectedPropertyExampleCommands.class);

            JPanel bar =
                    onEdt(() -> app.commands(Ui.class).buttonBar(commands.buttonAction(), commands.toggleAction()));

            assertThat(bar.getLayout()).isInstanceOf(FlowLayout.class);
            assertThat(((FlowLayout) bar.getLayout()).getAlignment()).isEqualTo(FlowLayout.CENTER);
            assertThat(bar.getComponents()).hasSize(2);
            AbstractButton button = (AbstractButton) bar.getComponent(0);
            AbstractButton toggle = (AbstractButton) bar.getComponent(1);
            assertThat(button.getClass()).isEqualTo(JButton.class);
            assertThat(button.getAction()).isSameAs(commands.buttonAction().asAction());
            assertThat(toggle).isInstanceOf(JToggleButton.class);
            assertThat(toggle.getAction()).isSameAs(commands.toggleAction().asAction());
        }
    }

    @Test
    void showsTheTextOfTheCommands() throws Exception {
        try (AppTester<SelectedPropertyExample> app = AppTester.start(SelectedPropertyExample.class)) {
            SelectedPropertyExampleCommands commands = app.commands(SelectedPropertyExampleCommands.class);

            JPanel bar =
                    onEdt(() -> app.commands(Ui.class).buttonBar(commands.buttonAction(), commands.toggleAction()));

            AbstractButton button = (AbstractButton) bar.getComponent(0);
            AbstractButton toggle = (AbstractButton) bar.getComponent(1);
            assertThat(button.getHideActionText()).isFalse();
            assertThat(button.getText()).isEqualTo("Toggle the Buttons' Value");
            assertThat(toggle.getHideActionText()).isFalse();
            assertThat(toggle.getText()).isEqualTo("Check @Command on a Property");
        }
    }

    @Test
    void followsTheSelectedStateOfAToggleCommand() throws Exception {
        try (AppTester<SelectedPropertyExample> app = AppTester.start(SelectedPropertyExample.class)) {
            SelectedPropertyExampleCommands commands = app.commands(SelectedPropertyExampleCommands.class);
            JPanel bar = onEdt(() -> app.commands(Ui.class).buttonBar(commands.toggleAction()));

            commands.toggleAction().execute();

            assertThat(((AbstractButton) bar.getComponent(0)).isSelected()).isTrue();
        }
    }

    @Test
    void isEmptyWithoutCommands() throws Exception {
        try (AppTester<SelectedPropertyExample> app = AppTester.start(SelectedPropertyExample.class)) {
            JPanel bar = onEdt(() -> app.commands(Ui.class).buttonBar());

            assertThat(bar.getComponents()).isEmpty();
        }
    }

    // Runs on the EDT, as every call of Ui must, and rethrows what it throws.
    private static <T> T onEdt(Supplier<T> call) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        try {
            EventQueue.invokeAndWait(() -> result.set(call.get()));
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
        return Objects.requireNonNull(result.get());
    }
}
