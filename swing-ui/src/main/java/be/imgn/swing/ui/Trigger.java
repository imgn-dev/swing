package be.imgn.swing.ui;

import java.awt.Component;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/**
 * The path that activated a command, with the objects that path always has. A handler that uses them switches over the
 * trigger, exhaustively, so that it deals with the paths that have no component, such as {@link Programmatic}.
 */
public sealed interface Trigger {

    /**
     * A menu item of the menu bar, or of a menu built with {@link MenuSpec#createMenu()}, was selected.
     *
     * @param item the menu item
     */
    record MenuSelection(JMenuItem item) implements Trigger {}

    /**
     * A menu item of a popup menu was selected.
     *
     * @param item the menu item
     * @param invoker the component the popup menu was opened on, such as the table that was right-clicked
     */
    record PopupMenuSelection(JMenuItem item, Component invoker) implements Trigger {}

    /**
     * A button was clicked.
     *
     * @param button the button
     */
    record ButtonClick(AbstractButton button) implements Trigger {}

    /**
     * A shortcut was typed.
     *
     * @param keyStroke the key stroke
     * @param focusOwner the component the key event came from
     */
    record Shortcut(KeyStroke keyStroke, Component focusOwner) implements Trigger {}

    /**
     * The operating system's application menu ran the command.
     *
     * @param action the action of the menu
     */
    record OperatingSystem(SystemAction action) implements Trigger {}

    /** Code ran the command, as with {@link BoundCommand#execute()}. */
    record Programmatic() implements Trigger {}
}
