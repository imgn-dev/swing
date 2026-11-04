package be.imgn.swing.examples.addressbook;

import java.util.Optional;

/**
 * The contact selected in the list of the main window, published on the application's bus at each change of the
 * selection, as Guts' contacts list publishes it on its event channel for the views and actions that consume it.
 *
 * @param contact the selected contact, empty when none is
 */
public record ContactSelected(Optional<Contact> contact) {}
