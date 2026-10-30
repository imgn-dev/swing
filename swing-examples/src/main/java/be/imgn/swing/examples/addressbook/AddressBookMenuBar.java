package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.ViewScoped;
import javax.swing.JMenuBar;

/**
 * The menu bar of {@link AddressBookApp}, as in Guts' demo: the file, edit, contacts and task test menus. Its menus'
 * texts are in the application's resources, which give {@link AddressBookAppMenus}.
 */
@ViewScoped
public final class AddressBookMenuBar {

    private final AddressBookAppMenus menus;
    private final StandardCommands standard;
    private final ContactActionsCommands contacts;
    private final GeneralActionsCommands general;
    private final TaskTestActionsCommands tasks;
    private final Ui ui;

    @Inject
    AddressBookMenuBar(
            AddressBookAppMenus menus,
            StandardCommands standard,
            ContactActionsCommands contacts,
            GeneralActionsCommands general,
            TaskTestActionsCommands tasks,
            Ui ui) {
        this.menus = menus;
        this.standard = standard;
        this.contacts = contacts;
        this.general = general;
        this.tasks = tasks;
        this.ui = ui;
    }

    /**
     * Returns a new menu bar.
     *
     * @return the menu bar
     */
    JMenuBar create() {
        return ui.menuBar(
                menus.file().with(general.throwException(), standard.exit()),
                menus.edit().with(standard.cut(), standard.copy(), standard.paste()),
                menus.contacts()
                        .with(
                                contacts.createContact(),
                                contacts.createContactWithTabs(),
                                contacts.createContactWithWizard(),
                                MenuEntry.SEPARATOR,
                                contacts.modifyContact(),
                                contacts.modifyContactWithTabs(),
                                contacts.modifyContactWithWizard(),
                                MenuEntry.SEPARATOR,
                                contacts.deleteContact()),
                menus.taskTests()
                        .with(
                                tasks.oneTaskNoBlocker(),
                                tasks.oneTaskComponentBlocker(),
                                tasks.oneTaskActionBlocker(),
                                tasks.oneTaskWindowBlocker(),
                                tasks.oneTaskDialogBlocker(),
                                tasks.oneTaskProgressDialogBlocker(),
                                MenuEntry.SEPARATOR,
                                tasks.oneTaskSerialExecutor(),
                                tasks.fiveTasksDialogBlocker(),
                                tasks.twoSerialTaskDialogBlocker(),
                                tasks.twoSerialGroupsDialogBlocker()));
    }
}
