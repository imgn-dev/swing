package be.imgn.swing.ui;

/**
 * The separator of menus and toolbars, which {@link MenuEntry#SEPARATOR}, {@link DynamicEntry#SEPARATOR} and
 * {@link ToolBarEntry#SEPARATOR} name.
 */
public final class Separator implements MenuEntry, DynamicEntry, ToolBarEntry {

    static final Separator INSTANCE = new Separator();

    private Separator() {}

    @Override
    public String toString() {
        return "SEPARATOR";
    }
}
