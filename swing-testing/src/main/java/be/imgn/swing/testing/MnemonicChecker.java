package be.imgn.swing.testing;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/** Checks the components of a dialog for duplicate mnemonics and duplicate shortcuts. */
final class MnemonicChecker {

    private MnemonicChecker() {}

    static void check(Component root, String where) {
        Map<Integer, String> mnemonics = new HashMap<>();
        Map<KeyStroke, String> shortcuts = new HashMap<>();
        for (Component component : Components.find(root, Component.class)) {
            if (component instanceof JMenuItem) {
                continue;
            }
            int mnemonic = mnemonicOf(component);
            if (mnemonic != 0 && component.isVisible()) {
                String previous = mnemonics.putIfAbsent(mnemonic, describe(component));
                if (previous != null) {
                    throw new AssertionError(
                            "Duplicate mnemonic in " + where + ": " + previous + " and " + describe(component));
                }
            }
        }
        for (JMenuItem item : Components.find(root, JMenuItem.class)) {
            if (!(item instanceof JMenu) && item.getAccelerator() != null) {
                String previous = shortcuts.putIfAbsent(item.getAccelerator(), item.getText());
                if (previous != null) {
                    throw new AssertionError(
                            "Duplicate shortcut in " + where + ": " + previous + " and " + item.getText());
                }
            }
        }
    }

    private static int mnemonicOf(Component component) {
        if (component instanceof AbstractButton button) {
            return button.getMnemonic();
        }
        if (component instanceof JLabel label) {
            return label.getDisplayedMnemonic();
        }
        return 0;
    }

    private static String describe(Component component) {
        if (component instanceof AbstractButton button) {
            return button.getText();
        }
        return component instanceof JLabel label ? label.getText() : component.toString();
    }
}
