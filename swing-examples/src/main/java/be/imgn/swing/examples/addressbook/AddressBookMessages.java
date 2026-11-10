package be.imgn.swing.examples.addressbook;

import be.imgn.swing.Messages;

/** The messages of the address book. */
@Messages
public interface AddressBookMessages {

    /**
     * Returns the question asked before deleting a contact.
     *
     * @param firstName the contact's first name
     * @param lastName the contact's last name
     * @return the question
     */
    String confirmDelete(String firstName, String lastName);

    /**
     * Returns the message shown once a contact is deleted.
     *
     * @return the message
     */
    String deleteDone();

    /**
     * Returns the text of the picture of a contact that has none.
     *
     * @return the text
     */
    String noPicture();

    /**
     * Returns the text of the picture when no contact is selected.
     *
     * @return the text
     */
    String noContactSelected();

    /**
     * Returns the header of the column of first names.
     *
     * @return the header
     */
    String firstNameHeader();

    /**
     * Returns the header of the column of last names.
     *
     * @return the header
     */
    String lastNameHeader();

    /**
     * Returns the header of the column of home cities.
     *
     * @return the header
     */
    String homeCityHeader();

    /**
     * Returns the requirement of a contact's first name.
     *
     * @return the requirement
     */
    String firstNameRequired();

    /**
     * Returns the requirement of a contact's last name.
     *
     * @return the requirement
     */
    String lastNameRequired();

    /**
     * Returns the requirement of the text of a date.
     *
     * @return the requirement
     */
    String isoDate();

    /**
     * Returns the progress message of a test task that sleeps in chunks.
     *
     * @param chunk the chunk's number, from 1
     * @return the message
     */
    String sleepChunk(int chunk);

    /**
     * Returns the progress message of a test task that is done.
     *
     * @return the message
     */
    String taskDone();
}
