package be.imgn.swing.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An entry of a dynamic section of a menu: a command, a separator, a submenu or a plain item. */
public sealed interface DynamicEntry permits BoundCommand, Separator, MenuSpec, DynamicEntry.Item {

    /** The separator. */
    Separator SEPARATOR = Separator.INSTANCE;

    /** The number of items {@link #numbered(List)} numbers. */
    int NUMBERED = 9;

    /**
     * Returns a plain item. Items are data, like a file name: they have no resources, no fixed id and no shortcut.
     *
     * @param text the text, where {@code &} marks the mnemonic and {@code &&} is a literal ampersand
     * @param action the action, run on the EDT
     * @return the item
     */
    static Item item(String text, Runnable action) {
        return new Item(text, action);
    }

    /**
     * Returns the items with {@code &1} to {@code &9} prefixed to the texts of the first nine.
     *
     * @param items the items
     * @return the numbered items
     */
    static List<Item> numbered(List<Item> items) {
        List<Item> numbered = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            numbered.add(index < NUMBERED ? new Item("&" + (index + 1) + " " + item.text(), item.action()) : item);
        }
        return List.copyOf(numbered);
    }

    /**
     * A plain item of a dynamic section.
     *
     * @param text the text, where {@code &} marks the mnemonic
     * @param action the action, run on the EDT
     */
    record Item(String text, Runnable action) implements DynamicEntry {

        /**
         * Creates an item.
         *
         * @param text the text
         * @param action the action
         */
        public Item {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(action, "action");
        }
    }
}
