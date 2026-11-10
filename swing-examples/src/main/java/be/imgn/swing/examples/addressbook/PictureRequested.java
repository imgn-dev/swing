package be.imgn.swing.examples.addressbook;

/**
 * Asks for the picture of a contact in a tab of its own, published by the contacts list's {@code openPicture} command,
 * as a double click on a row of Guts' contacts list opens it.
 *
 * @param contact the contact
 */
public record PictureRequested(Contact contact) {}
