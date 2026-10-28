package be.imgn.swing.ui;

import be.imgn.inject.Inject;

/**
 * The standard menus of a view, with text and mnemonics from the framework's resources, and the superclass of every
 * generated {@code <Class>Menus}. Injectable on its own by classes that declare no {@code [menus.*]} table. A
 * {@code [menus.file]} table in the application's resources overrides the text of the File menu.
 */
@ViewScoped
public class StandardMenus {

    private final Ui ui;

    /**
     * Creates the standard menus of a view.
     *
     * @param ui the view's {@link Ui}
     */
    @Inject
    public StandardMenus(Ui ui) {
        this.ui = ui;
    }

    /**
     * Returns a menu built from a class's resources, its {@code [menus.<id>]} table.
     *
     * @param owner the class
     * @param id the id of the menu
     * @return the menu, the same for a class and id within the view
     */
    protected final BoundMenu menu(Class<?> owner, String id) {
        return ui.menu(owner, id);
    }

    /**
     * Returns the File menu.
     *
     * @return the menu
     */
    public BoundMenu file() {
        return menu(StandardMenus.class, "file");
    }

    /**
     * Returns the Edit menu.
     *
     * @return the menu
     */
    public BoundMenu edit() {
        return menu(StandardMenus.class, "edit");
    }

    /**
     * Returns the View menu.
     *
     * @return the menu
     */
    public BoundMenu view() {
        return menu(StandardMenus.class, "view");
    }

    /**
     * Returns the Window menu.
     *
     * @return the menu
     */
    public BoundMenu window() {
        return menu(StandardMenus.class, "window");
    }

    /**
     * Returns the Help menu.
     *
     * @return the menu
     */
    public BoundMenu help() {
        return menu(StandardMenus.class, "help");
    }
}
