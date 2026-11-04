package be.imgn.swing.examples.password;

import be.imgn.swing.Rule;
import be.imgn.swing.Validator;
import java.util.Objects;

/**
 * The rules of a new password, declared once so that a form, a command-line tool or a service tests them alike. Their
 * requirements are texts of the display locale, so they are built from typed messages.
 */
public final class PasswordRules {

    private static final int MIN_LENGTH = 8;

    private PasswordRules() {}

    /**
     * Returns the validator of a new password: at least 8 characters, then a digit.
     *
     * @param messages the texts of the requirements
     * @return the validator
     * @throws NullPointerException if {@code messages} is {@code null}
     */
    public static Validator<String> newPassword(PasswordMessages messages) {
        Objects.requireNonNull(messages, "messages");
        return Validator.of(
                Rule.of(messages.minLength(MIN_LENGTH), (String text) -> text.length() >= MIN_LENGTH),
                Rule.of(messages.digit(), (String text) -> text.chars().anyMatch(Character::isDigit)));
    }
}
