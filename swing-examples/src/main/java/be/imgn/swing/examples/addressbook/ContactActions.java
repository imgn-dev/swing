package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Task;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.ViewScoped;

/**
 * The commands on contacts: create and modify with each of the three dialogs, and delete. Those that act on the
 * selected contact are enabled only while one is selected.
 */
@ViewScoped
public class ContactActions {

    private final ContactSelection selection;
    private final AddressBookService service;
    private final Ui ui;
    private final AddressBookMessages messages;

    @Inject
    ContactActions(ContactSelection selection, AddressBookService service, Ui ui, AddressBookMessages messages) {
        this.selection = selection;
        this.service = service;
        this.ui = ui;
        this.messages = messages;
    }

    Observable<Boolean> hasSelection() {
        return selection.isSelected();
    }

    @Command
    void createContact() {
        ui.showDialog(ContactDialogFactory.class, factory -> factory.create(Contact.NEW))
                .ifPresent(service::createContact);
    }

    @Command(enabled = "hasSelection")
    void modifyContact() {
        ui.showDialog(ContactDialogFactory.class, factory -> factory.create(selected()))
                .ifPresent(service::modifyContact);
    }

    @Command
    void createContactWithTabs() {
        ui.showDialog(ContactTabsDialogFactory.class, factory -> factory.create(Contact.NEW))
                .ifPresent(service::createContact);
    }

    @Command(enabled = "hasSelection")
    void modifyContactWithTabs() {
        ui.showDialog(ContactTabsDialogFactory.class, factory -> factory.create(selected()))
                .ifPresent(service::modifyContact);
    }

    @Command
    void createContactWithWizard() {
        ui.showDialog(ContactWizardDialogFactory.class, factory -> factory.create(Contact.NEW))
                .ifPresent(service::createContact);
    }

    @Command(enabled = "hasSelection")
    void modifyContactWithWizard() {
        ui.showDialog(ContactWizardDialogFactory.class, factory -> factory.create(selected()))
                .ifPresent(service::modifyContact);
    }

    /**
     * Deletes the selected contact, once confirmed, in a task that disables the command while it runs.
     *
     * @return the task, which does nothing when the deletion is not confirmed
     */
    @Command(enabled = "hasSelection", block = Block.COMMAND)
    Task<Void> deleteContact() {
        Contact contact = selected();
        if (!ui.dialogs().confirm(messages.confirmDelete(contact.firstName(), contact.lastName()))) {
            return Task.of(() -> {});
        }
        return Task.of(() -> service.removeContact(contact))
                .onSuccess(done -> ui.dialogs().inform(messages.deleteDone()));
    }

    private Contact selected() {
        return selection.contact().get().orElseThrow();
    }
}
