package be.imgn.swing.examples.password;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Property;

/** The user's account, whose password the {@link ChangePasswordDialog} changes. */
@Singleton
public final class Account {

    private final Property<String> password = Property.of("secret1");

    @Inject
    Account() {}

    /**
     * Returns the password.
     *
     * @return the password, {@code secret1} at first
     */
    public Property<String> password() {
        return password;
    }
}
