package be.imgn.swing.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.ActionEvent;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

/**
 * The mapping from the {@link ActionEvent} the JDK delivers to a {@link CommandContext}, with constructed events: the
 * trigger follows the source, the modifiers follow the event, except for a programmatic trigger.
 */
class CommandActionTest {

    @Test
    void anItemOfTheMenuBarIsAMenuSelection() {
        JMenuItem item = new JMenuItem("Save");
        JMenu menu = new JMenu("File");
        menu.add(item);
        JMenuBar bar = new JMenuBar();
        bar.add(menu);

        CommandContext context = CommandAction.contextOf(shiftClick(item), false);

        assertThat(context.trigger()).isEqualTo(new Trigger.MenuSelection(item));
        assertThat(context.modifiers()).containsExactly(Modifier.SHIFT);
    }

    @Test
    void anItemOfAPopupMenuIsAPopupMenuSelectionWithTheInvoker() {
        JTable table = new JTable();
        JPopupMenu popup = new JPopupMenu();
        // Ui.popupMenu marks the component a popup menu is attached to, since the invoker is set only when it is shown.
        popup.putClientProperty(CommandAction.INVOKER, table);
        JMenuItem item = new JMenuItem("Copy");
        popup.add(item);

        CommandContext context = CommandAction.contextOf(shiftClick(item), false);

        assertThat(context.trigger()).isEqualTo(new Trigger.PopupMenuSelection(item, table));
        assertThat(context.modifiers()).containsExactly(Modifier.SHIFT);
    }

    @Test
    void anItemOfASubmenuOfAPopupMenuIsAPopupMenuSelectionWithTheInvoker() {
        JTable table = new JTable();
        JPopupMenu popup = new JPopupMenu();
        popup.putClientProperty(CommandAction.INVOKER, table);
        JMenu submenu = new JMenu("More");
        popup.add(submenu);
        JMenuItem item = new JMenuItem("Copy");
        submenu.add(item);

        CommandContext context = CommandAction.contextOf(shiftClick(item), false);

        assertThat(context.trigger()).isEqualTo(new Trigger.PopupMenuSelection(item, table));
    }

    @Test
    void aButtonIsAButtonClick() {
        JButton button = new JButton("Save");

        CommandContext context = CommandAction.contextOf(shiftClick(button), false);

        assertThat(context.trigger()).isEqualTo(new Trigger.ButtonClick(button));
        assertThat(context.modifiers()).containsExactly(Modifier.SHIFT);
    }

    @Test
    void anotherSourceIsProgrammaticWithoutModifiers() {
        CommandContext context = CommandAction.contextOf(shiftClick(new Object()), false);

        assertThat(context.trigger()).isEqualTo(new Trigger.Programmatic());
        assertThat(context.modifiers()).isEmpty();
    }

    @Test
    void theShortcutModifierFollowsThePlatform() {
        JButton button = new JButton("Save");
        ActionEvent control = event(button, ActionEvent.CTRL_MASK);
        ActionEvent meta = event(button, ActionEvent.META_MASK);

        assertThat(CommandAction.contextOf(control, false).modifiers())
                .containsExactlyInAnyOrder(Modifier.CONTROL, Modifier.SHORTCUT);
        assertThat(CommandAction.contextOf(control, true).modifiers()).containsExactly(Modifier.CONTROL);
        assertThat(CommandAction.contextOf(meta, true).modifiers())
                .containsExactlyInAnyOrder(Modifier.META, Modifier.SHORTCUT);
        assertThat(CommandAction.contextOf(meta, false).modifiers()).containsExactly(Modifier.META);
    }

    @Test
    void everyModifierOfTheEventIsRead() {
        ActionEvent all = event(
                new JButton(),
                ActionEvent.SHIFT_MASK | ActionEvent.CTRL_MASK | ActionEvent.ALT_MASK | ActionEvent.META_MASK);

        assertThat(CommandAction.contextOf(all, false).modifiers())
                .containsExactlyInAnyOrder(
                        Modifier.SHIFT, Modifier.CONTROL, Modifier.ALT, Modifier.META, Modifier.SHORTCUT);
    }

    private static ActionEvent shiftClick(Object source) {
        return event(source, ActionEvent.SHIFT_MASK);
    }

    private static ActionEvent event(Object source, int modifiers) {
        return new ActionEvent(source, ActionEvent.ACTION_PERFORMED, "save", 0L, modifiers);
    }
}
