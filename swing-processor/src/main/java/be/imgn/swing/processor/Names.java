package be.imgn.swing.processor;

import java.util.List;
import java.util.Set;

/** The names of the framework's types and annotations, which the processor reads but does not depend on. */
final class Names {

    static final String APPLICATION = "be.imgn.swing.Application";
    static final String APPLICATION_DESCRIPTOR = "be.imgn.swing.ApplicationDescriptor";
    static final String MESSAGES = "be.imgn.swing.Messages";
    static final String RESOURCE_OPENER = "be.imgn.swing.ResourceOpener";
    static final String RESOURCES = "be.imgn.swing.Resources";
    static final String DISPLAY_LOCALE = "be.imgn.swing.DisplayLocale";
    static final String MESSAGE_PATTERN = "be.imgn.swing.support.MessagePattern";
    static final String OBSERVABLE = "be.imgn.swing.Observable";
    static final String PROPERTY = "be.imgn.swing.Property";
    static final String TASK = "be.imgn.swing.Task";

    static final String COMMAND = "be.imgn.swing.ui.Command";
    static final String BLOCK = "be.imgn.swing.ui.Block";
    static final String BOUND_COMMAND = "be.imgn.swing.ui.BoundCommand";
    static final String BOUND_MENU = "be.imgn.swing.ui.BoundMenu";
    static final String COMMAND_CONTEXT = "be.imgn.swing.ui.CommandContext";
    static final String COMMAND_REGISTRY = "be.imgn.swing.ui.CommandRegistry";
    static final String STANDARD_COMMANDS = "be.imgn.swing.ui.StandardCommands";
    static final String STANDARD_MENUS = "be.imgn.swing.ui.StandardMenus";
    static final String UI = "be.imgn.swing.ui.Ui";
    static final String VIEW = "be.imgn.swing.ui.View";
    static final String SINGLE_FRAME = "be.imgn.swing.ui.SingleFrame";
    static final String DIALOG_VIEW = "be.imgn.swing.ui.DialogView";
    static final String WINDOW_SCOPED = "be.imgn.swing.ui.WindowScoped";
    static final String VIEW_SCOPED = "be.imgn.swing.ui.ViewScoped";
    static final String COMPONENT_INJECTOR = "be.imgn.swing.ui.support.ComponentInjector";
    static final String COMPONENT_RESOURCES = "be.imgn.swing.ui.support.ComponentResources";
    static final String COMPONENT_STATE = "be.imgn.swing.ui.support.ComponentState";

    static final String INJECT = "be.imgn.inject.Inject";
    static final String SINGLETON = "be.imgn.inject.Singleton";
    static final String PROVIDER = "be.imgn.inject.Provider";
    static final String EXPOSE_AS = "be.imgn.inject.ExposeAs";

    static final String COMPONENT = "java.awt.Component";
    static final String JCOMPONENT = "javax.swing.JComponent";
    static final String ABSTRACT_BUTTON = "javax.swing.AbstractButton";
    static final String JLABEL = "javax.swing.JLabel";
    static final String ICON = "javax.swing.Icon";
    static final String SPLIT_PANE = "javax.swing.JSplitPane";
    static final String TABBED_PANE = "javax.swing.JTabbedPane";
    static final String TABLE = "javax.swing.JTable";

    /** The ids of the standard commands, which are also the names of their methods in {@code StandardCommands}. */
    static final List<String> STANDARD_COMMANDS_IDS =
            List.of("exit", "about", "preferences", "cut", "copy", "paste", "selectAll", "closeWindow", "ok", "cancel");

    /** The ids of the standard menus. */
    static final Set<String> STANDARD_MENU_IDS = Set.of("file", "edit", "view", "window", "help");

    private Names() {}
}
