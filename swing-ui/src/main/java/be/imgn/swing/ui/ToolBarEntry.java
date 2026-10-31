package be.imgn.swing.ui;

/** An entry of a toolbar: a command or a separator. */
@SuppressWarnings("checkstyle:InterfaceIsType")
public sealed interface ToolBarEntry permits BoundCommand, Separator {

    /** The separator. */
    Separator SEPARATOR = Separator.INSTANCE;
}
