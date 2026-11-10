package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.Locales;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.ViewScoped;
import java.util.Locale;

/**
 * The commands of Guts' demo that throw an exception, to show its exception handling on the EDT, and that set the
 * display locale to French or English.
 */
@ViewScoped
public class GeneralActions {

    private final Locales locales;

    @Inject
    GeneralActions(Locales locales) {
        this.locales = locales;
    }

    @Command
    void throwException() {
        throw new IllegalArgumentException("Some message here");
    }

    @Command
    void french() {
        locales.set(Locale.FRENCH);
    }

    @Command
    void english() {
        locales.set(Locale.ENGLISH);
    }
}
