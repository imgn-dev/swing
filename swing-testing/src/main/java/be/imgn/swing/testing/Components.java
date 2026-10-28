package be.imgn.swing.testing;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;

/** Finds components in a tree, depth first, the items of menus included. */
final class Components {

    private Components() {}

    static <T> List<T> find(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <T> void collect(Component component, Class<T> type, List<T> found) {
        if (type.isInstance(component)) {
            found.add(type.cast(component));
        }
        if (component instanceof JMenu menu) {
            for (Component item : menu.getMenuComponents()) {
                collect(item, type, found);
            }
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, found);
            }
        }
    }

    static <T> T single(Component root, Class<T> type, String where) {
        List<T> found = find(root, type);
        if (found.size() != 1) {
            throw new IllegalStateException((found.isEmpty() ? "No " : found.size() + " components of type ")
                    + type.getName() + " in " + where + (found.isEmpty() ? "" : ", not one"));
        }
        return found.getFirst();
    }
}
