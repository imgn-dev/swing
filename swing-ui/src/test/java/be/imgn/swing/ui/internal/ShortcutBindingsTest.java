package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class ShortcutBindingsTest {

    private static final KeyStroke SHORTCUT = KeyStroke.getKeyStroke("ctrl C");

    private final JRootPane rootPane = new JRootPane();
    private final ShortcutBindings bindings = new ShortcutBindings(rootPane, new UiMode(true));
    private final List<String> lost = new ArrayList<>();

    private static Action action() {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {}
        };
    }

    private void register(String command, ShortcutBindings.Priority priority) {
        bindings.register(SHORTCUT, command, new Object(), action(), priority, () -> lost.add(command));
    }

    private Object bound() {
        return rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(SHORTCUT);
    }

    @Test
    void givesTheShortcutToTheHigherPriorityWhicheverRegistersFirst() {
        register("StandardCommands#copy", ShortcutBindings.Priority.STANDARD);
        register("p.View#clear", ShortcutBindings.Priority.RESOURCES);

        assertThat(bindings.commandOf(SHORTCUT)).isEqualTo("p.View#clear");
        assertThat(bound()).isEqualTo("be.imgn.swing.command:p.View#clear");
        assertThat(lost).containsExactly("StandardCommands#copy");

        register("p.View#mapped", ShortcutBindings.Priority.KEYMAP);
        register("StandardCommands#cut", ShortcutBindings.Priority.STANDARD);

        assertThat(bindings.commandOf(SHORTCUT)).isEqualTo("p.View#mapped");
        assertThat(lost).containsExactly("StandardCommands#copy", "p.View#clear", "StandardCommands#cut");
    }

    @Test
    void reportsTwoShortcutsOfTheSamePriority() {
        register("p.View#first", ShortcutBindings.Priority.RESOURCES);

        assertThatIllegalStateException()
                .isThrownBy(() -> register("p.View#second", ShortcutBindings.Priority.RESOURCES))
                .withMessageContaining("have the same shortcut");
        assertThat(bindings.commandOf(SHORTCUT)).isEqualTo("p.View#first");
    }

    @Test
    void registersTheSameCommandOnce() {
        register("p.View#first", ShortcutBindings.Priority.RESOURCES);
        register("p.View#first", ShortcutBindings.Priority.RESOURCES);

        assertThat(lost).isEmpty();
    }

    @Test
    void takesTheShortcutFromEveryViewOfACommandThatLosesIt() {
        register("StandardCommands#copy", ShortcutBindings.Priority.STANDARD);
        register("StandardCommands#copy", ShortcutBindings.Priority.STANDARD);

        register("p.View#clear", ShortcutBindings.Priority.RESOURCES);

        assertThat(lost).containsExactly("StandardCommands#copy", "StandardCommands#copy");
    }

    @Test
    void actsOnTheViewThatRegisteredTheCommandLast() {
        Object first = new Object();
        Object second = new Object();
        Action firstAction = action();
        Action secondAction = action();
        bindings.register(SHORTCUT, "p.View#open", first, firstAction, ShortcutBindings.Priority.RESOURCES, () -> {});
        bindings.register(SHORTCUT, "p.View#open", second, secondAction, ShortcutBindings.Priority.RESOURCES, () -> {});

        assertThat(rootPane.getActionMap().get(bound())).isSameAs(secondAction);

        bindings.remove("p.View#open", second);

        assertThat(bound()).isEqualTo("be.imgn.swing.command:p.View#open");
        assertThat(rootPane.getActionMap().get(bound())).isSameAs(firstAction);

        bindings.remove("p.View#open", first);

        assertThat(bound()).isNull();
        assertThat(bindings.commandOf(SHORTCUT)).isNull();
    }

    @Test
    void keepsTheShortcutOfTheLastViewWhenAnEarlierOneLeaves() {
        Object first = new Object();
        Object second = new Object();
        Action secondAction = action();
        bindings.register(SHORTCUT, "p.View#open", first, action(), ShortcutBindings.Priority.RESOURCES, () -> {});
        bindings.register(SHORTCUT, "p.View#open", second, secondAction, ShortcutBindings.Priority.RESOURCES, () -> {});

        bindings.remove("p.View#open", first);

        assertThat(rootPane.getActionMap().get(bound())).isSameAs(secondAction);
    }
}
