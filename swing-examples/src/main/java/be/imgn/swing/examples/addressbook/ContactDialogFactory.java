package be.imgn.swing.examples.addressbook;

import be.imgn.inject.AssistedFactory;

/** Creates the {@link ContactDialog} of a contact. */
public interface ContactDialogFactory {

    /**
     * Returns the dialog of a contact.
     *
     * @param contact the contact, {@link Contact#NEW} for a creation
     * @return the dialog
     */
    @AssistedFactory
    ContactDialog create(Contact contact);
}
