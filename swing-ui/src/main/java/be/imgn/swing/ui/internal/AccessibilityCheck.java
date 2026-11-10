package be.imgn.swing.ui.internal;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRootPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.text.JTextComponent;

/**
 * Checks the components of a window's content pane when a view's build ends, in development mode and in tests: the
 * components that need an accessible name have one, and a label with a mnemonic is the label of a component. The build
 * can't check component trees, which code builds.
 */
public final class AccessibilityCheck {

    private static final List<Class<?>> NAMED = List.of(
            JTextComponent.class,
            JComboBox.class,
            JList.class,
            JTable.class,
            JTree.class,
            JSpinner.class,
            JSlider.class,
            AbstractButton.class);
    private static final System.Logger LOGGER = System.getLogger(AccessibilityCheck.class.getName());

    private AccessibilityCheck() {}

    /**
     * Checks the components of a view's content, reporting each failure through the UI mode: a window's root pane, for
     * its content pane, or the content of an added view, itself included. Hidden components are skipped.
     *
     * @param content the window's root pane, or an added view's content
     * @param fields the paths of the view's {@code @Exposed} component fields, by component, which name them
     * @param view the view class, which the reports name
     * @param mode the mode, which throws the first failure
     */
    public static void check(Component content, Map<Component, String> fields, Class<?> view, UiMode mode) {
        if (content instanceof JRootPane rootPane) {
            checkChildren(rootPane.getContentPane(), new ArrayList<>(), fields, view, mode);
        } else {
            checkComponent(content, List.of(), fields, view, mode);
        }
    }

    private static void checkChildren(
            Container container, List<Integer> at, Map<Component, String> fields, Class<?> view, UiMode mode) {
        Component[] children = container.getComponents();
        for (int index = 0; index < children.length; index++) {
            List<Integer> path = new ArrayList<>(at);
            path.add(index);
            checkComponent(children[index], path, fields, view, mode);
        }
    }

    private static void checkComponent(
            Component child, List<Integer> path, Map<Component, String> fields, Class<?> view, UiMode mode) {
        if (!child.isVisible()) {
            return;
        }
        boolean named = NAMED.stream().anyMatch(type -> type.isInstance(child));
        if (named) {
            String name = child.getAccessibleContext().getAccessibleName();
            if (child.isFocusable() && (name == null || name.isBlank())) {
                mode.report(
                        LOGGER,
                        "The " + describe(child, path, fields) + " of " + view.getName()
                                + " has no accessible name: give it a label whose labelFor it is, or an"
                                + " accessible-name resource");
            }
        } else {
            if (child instanceof JLabel label && label.getDisplayedMnemonic() != 0 && label.getLabelFor() == null) {
                mode.report(
                        LOGGER,
                        "The " + describe(child, path, fields) + " of " + view.getName()
                                + " has a mnemonic but labels no component, so its mnemonic focuses nothing");
            }
            if (child instanceof Container nested) {
                checkChildren(nested, path, fields, view, mode);
            }
        }
    }

    // A field's path, else the component's type and its index path from the content pane or the added view's content.
    private static String describe(Component component, List<Integer> path, Map<Component, String> fields) {
        String field = fields.get(component);
        String type = component.getClass().getSimpleName();
        if (field != null) {
            return type + " " + field;
        }
        StringBuilder indices = new StringBuilder();
        for (int index : path) {
            indices.append(indices.isEmpty() ? "" : ".").append(index);
        }
        // An added view's content itself has no index.
        return indices.isEmpty() ? type : type + " at " + indices;
    }
}
