package be.imgn.swing;

import java.util.Locale;
import java.util.Objects;

/**
 * The locale of user-interface text, chosen once at startup and bound in the root injector. Resources and messages read
 * it; nothing in the framework reads {@link Locale#getDefault()} after startup.
 *
 * @param locale the locale
 */
public record DisplayLocale(Locale locale) {

    /**
     * Creates the display locale.
     *
     * @param locale the locale
     */
    public DisplayLocale {
        Objects.requireNonNull(locale, "locale");
    }
}
