package be.imgn.swing.ui.internal;

import java.awt.Component;
import java.awt.Container;
import java.util.Enumeration;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;

/**
 * Finds the first component of a content that takes the focus, as a wizard's step and a dock view are focused when they
 * show. A focus traversal policy needs a window on screen, which the test harness never has: this walks the content
 * depth first, in container order, and accepts a component as Swing's {@code LayoutFocusTraversalPolicy} does.
 */
public final class FocusOrder {

    private FocusOrder() {}

    /**
     * Returns the first component of a content, itself included, that is visible up to it and takes the focus.
     *
     * @param content the content
     * @return the component, empty when none takes the focus
     */
    public static Optional<Component> first(Component content) {
        if (!content.isVisible()) {
            return Optional.empty();
        }
        if (accepts(content)) {
            return Optional.of(content);
        }
        if (content instanceof Container container) {
            for (Component child : container.getComponents()) {
                Optional<Component> found = first(child);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    // As LayoutFocusTraversalPolicy.accept: focusable and enabled, then a table always, a combo box as its UI says,
    // the selected or else the first button of a group, and another Swing component with key bindings while focused.
    private static boolean accepts(Component component) {
        if (!component.isFocusable() || !component.isEnabled()) {
            return false;
        }
        return switch (component) {
            case JTable ignored -> true;
            case JComboBox<?> box -> box.getUI().isFocusTraversable(box);
            case JToggleButton button when groupOf(button) != null -> isGroupsFocus(button, groupOf(button));
            case JComponent swing -> hasFocusedBindings(swing);
            default -> true;
        };
    }

    private static @Nullable ButtonGroup groupOf(AbstractButton button) {
        ButtonModel model = button.getModel();
        return model instanceof DefaultButtonModel defaults ? defaults.getGroup() : null;
    }

    // The selected button of a group takes the focus, else its first.
    private static boolean isGroupsFocus(JToggleButton button, @Nullable ButtonGroup group) {
        if (group == null || group.getSelection() == null) {
            return group == null || button.equals(firstOf(group));
        }
        return button.isSelected();
    }

    private static @Nullable AbstractButton firstOf(ButtonGroup group) {
        Enumeration<AbstractButton> buttons = group.getElements();
        return buttons.hasMoreElements() ? buttons.nextElement() : null;
    }

    // Read without creating the component's own map, which reading through JComponent.getInputMap would.
    private static boolean hasFocusedBindings(JComponent swing) {
        @Nullable InputMap bindings = SwingUtilities.getUIInputMap(swing, JComponent.WHEN_FOCUSED);
        while (bindings != null && bindings.size() == 0) {
            bindings = bindings.getParent();
        }
        return bindings != null;
    }
}
