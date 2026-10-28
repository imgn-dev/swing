package be.imgn.swing.ui;

import java.util.List;
import javax.swing.JMenu;

/** An immutable description of a menu: its menu and its entries. It is also a submenu entry of another menu. */
public final class MenuSpec implements MenuEntry, DynamicEntry {

    private final BoundMenu menu;
    private final List<MenuEntry> entries;

    MenuSpec(BoundMenu menu, List<MenuEntry> entries) {
        this.menu = menu;
        this.entries = List.copyOf(entries);
    }

    /**
     * Returns a new menu built from this description, for a menu outside the menu bar.
     *
     * @return the menu
     */
    public JMenu createMenu() {
        return menu.builder().createMenu(this);
    }

    BoundMenu menu() {
        return menu;
    }

    List<MenuEntry> entries() {
        return entries;
    }

    @Override
    public String toString() {
        return "MenuSpec[" + menu.id() + ", " + entries + "]";
    }
}
