package be.imgn.swing.ui;

import be.imgn.swing.ui.internal.Mnemonics;
import java.util.List;

/** A menu of a view, with its resources, that descriptions are made from. */
public final class BoundMenu {

    private final MenuBuilder builder;
    private final String id;
    private final Mnemonics text;

    BoundMenu(MenuBuilder builder, String id, Mnemonics text) {
        this.builder = builder;
        this.id = id;
        this.text = text;
    }

    /**
     * Returns the description of this menu with entries.
     *
     * @param entries the entries, in order
     * @return the description
     */
    public MenuSpec with(MenuEntry... entries) {
        return new MenuSpec(this, List.of(entries));
    }

    /**
     * Returns the id of the menu.
     *
     * @return the id, the key of its {@code [menus.<id>]} table
     */
    public String id() {
        return id;
    }

    MenuBuilder builder() {
        return builder;
    }

    Mnemonics text() {
        return text;
    }

    @Override
    public String toString() {
        return "BoundMenu[" + id + "]";
    }
}
