package be.imgn.swing.examples.password;

import be.imgn.swing.Messages;

/** The texts of the requirements of the passwords, and of the change of password. */
@Messages
public interface PasswordMessages {

    /**
     * Returns the requirement of a current password that isn't blank.
     *
     * @return the requirement
     */
    String currentRequired();

    /**
     * Returns the requirement of a current password equal to the account's.
     *
     * @return the requirement
     */
    String currentMatches();

    /**
     * Returns the requirement of a new password of a minimum length.
     *
     * @param length the minimum number of characters
     * @return the requirement
     */
    String minLength(int length);

    /**
     * Returns the requirement of a new password with a digit.
     *
     * @return the requirement
     */
    String digit();

    /**
     * Returns the requirement of a confirmation equal to the new password.
     *
     * @return the requirement
     */
    String confirmationMatches();

    /**
     * Returns the status of a password that was changed.
     *
     * @return the status
     */
    String changed();
}
