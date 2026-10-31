package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.ViewScoped;

/**
 * The command that throws an exception, which Guts' demo uses to show its exception handling on the EDT. Its French and
 * English commands, which change the locale at runtime, are not ported: milestone 1 chooses the locale at startup.
 */
@ViewScoped
public class GeneralActions {

    @Inject
    GeneralActions() {}

    @Command
    void throwException() {
        throw new IllegalArgumentException("Some message here");
    }
}
