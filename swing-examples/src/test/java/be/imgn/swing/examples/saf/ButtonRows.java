package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.BoundCommand;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JPanel;

/** Checks the rows of buttons that {@code Ui.buttonBar} builds in the examples' main windows. */
final class ButtonRows {

    private ButtonRows() {}

    /**
     * Asserts that the main window has a row of buttons as {@code Ui.buttonBar} builds it for commands without a
     * selected state: a panel with a centered {@link FlowLayout} with the default gaps, holding one {@link JButton} per
     * command, in order, and nothing else.
     *
     * @param app the tester
     * @param commands the commands of the row
     */
    static void assertButtonBar(AppTester<?> app, BoundCommand... commands) {
        List<Action> actions = Stream.of(commands).map(BoundCommand::asAction).toList();
        Row row = app.call(() -> {
            AbstractButton first = app.components(AbstractButton.class).stream()
                    .filter(button -> button.getParent() instanceof JPanel
                            && actions.getFirst().equals(button.getAction()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No button of the first command in a panel"));
            Container bar = first.getParent();
            return new Row(bar.getLayout(), List.of(bar.getComponents()));
        });

        FlowLayout defaults = new FlowLayout();
        assertThat(row.layout()).isInstanceOfSatisfying(FlowLayout.class, layout -> {
            assertThat(layout.getAlignment()).isEqualTo(FlowLayout.CENTER);
            assertThat(layout.getHgap()).isEqualTo(defaults.getHgap());
            assertThat(layout.getVgap()).isEqualTo(defaults.getVgap());
        });
        assertThat(row.components())
                .allSatisfy(component -> assertThat(component.getClass()).isEqualTo(JButton.class));
        assertThat(row.components())
                .extracting(component -> ((AbstractButton) component).getAction())
                .containsExactlyElementsOf(actions);
    }

    private record Row(LayoutManager layout, List<Component> components) {}
}
