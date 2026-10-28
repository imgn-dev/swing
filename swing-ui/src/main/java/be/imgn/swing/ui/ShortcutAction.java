package be.imgn.swing.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

/** The action a command's shortcut runs, from its window's input map. */
final class ShortcutAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient BoundCommand command;
    private final KeyStroke keyStroke;

    ShortcutAction(BoundCommand command, KeyStroke keyStroke) {
        this.command = command;
        this.keyStroke = keyStroke;
    }

    @Override
    public boolean isEnabled() {
        return command.asAction().isEnabled();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        command.run(CommandContext.of(
                new Trigger.Shortcut(keyStroke, focusOwner(event)),
                modifiersOf(keyStroke.getModifiers()),
                command.registry().services().isMacOS()));
    }

    // The component the key event came from.
    private static Component focusOwner(ActionEvent event) {
        AWTEvent current = EventQueue.getCurrentEvent();
        if (current instanceof KeyEvent key && key.getComponent() != null) {
            return key.getComponent();
        }
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner != null ? owner : (Component) event.getSource();
    }

    static Set<Modifier> modifiersOf(int modifiers) {
        EnumSet<Modifier> set = EnumSet.noneOf(Modifier.class);
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            set.add(Modifier.SHIFT);
        }
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            set.add(Modifier.CONTROL);
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            set.add(Modifier.ALT);
        }
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
            set.add(Modifier.META);
        }
        return set;
    }
}
