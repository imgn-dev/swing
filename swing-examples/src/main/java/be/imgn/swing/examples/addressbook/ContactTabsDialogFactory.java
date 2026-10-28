package be.imgn.swing.examples.addressbook;

import be.imgn.inject.AssistedFactory;

/** Creates the {@link ContactTabsDialog} of a contact. */
public interface ContactTabsDialogFactory {

    /**
     * Returns the dialog of a contact.
     *
     * @param contact the contact, {@link Contact#NEW} for a creation
     * @return the dialog
     */
    @AssistedFactory
    ContactTabsDialog create(Contact contact);
}
