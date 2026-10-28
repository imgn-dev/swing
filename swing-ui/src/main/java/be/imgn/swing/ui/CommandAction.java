package be.imgn.swing.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.jspecify.annotations.Nullable;

/** The Swing action of a command: it maps the {@link ActionEvent} the JDK delivers to a {@link CommandContext}. */
final class CommandAction extends AbstractAction {

    /** The client property of a popup menu that holds the component it is attached to. */
    static final String INVOKER = "be.imgn.swing.invoker";

    private static final long serialVersionUID = 1L;

    private final transient BoundCommand command;

    CommandAction(BoundCommand command) {
        this.command = command;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        command.run(contextOf(event, command.registry().services().isMacOS()));
    }

    static CommandContext contextOf(ActionEvent event, boolean macOS) {
        return CommandContext.of(triggerOf(event.getSource()), modifiersOf(event.getModifiers()), macOS);
    }

    static Set<Modifier> modifiersOf(int modifiers) {
        EnumSet<Modifier> set = EnumSet.noneOf(Modifier.class);
        if ((modifiers & ActionEvent.SHIFT_MASK) != 0) {
            set.add(Modifier.SHIFT);
        }
        if ((modifiers & ActionEvent.CTRL_MASK) != 0) {
            set.add(Modifier.CONTROL);
        }
        if ((modifiers & ActionEvent.ALT_MASK) != 0) {
            set.add(Modifier.ALT);
        }
        if ((modifiers & ActionEvent.META_MASK) != 0) {
            set.add(Modifier.META);
        }
        return set;
    }

    static Trigger triggerOf(Object source) {
        if (source instanceof JMenuItem item) {
            Component invoker = popupInvoker(item);
            return invoker == null ? new Trigger.MenuSelection(item) : new Trigger.PopupMenuSelection(item, invoker);
        }
        if (source instanceof AbstractButton button) {
            return new Trigger.ButtonClick(button);
        }
        return new Trigger.Programmatic();
    }

    // The component a popup menu holding the item is attached to; null for an item of a menu.
    private static @Nullable Component popupInvoker(JMenuItem item) {
        Container parent = item.getParent();
        while (parent instanceof JPopupMenu popup) {
            Component invoker = popup.getInvoker();
            if (invoker instanceof JMenu menu) {
                parent = menu.getParent();
                continue;
            }
            if (invoker != null) {
                return invoker;
            }
            return popup.getClientProperty(INVOKER) instanceof JComponent attached ? attached : null;
        }
        return null;
    }
}
