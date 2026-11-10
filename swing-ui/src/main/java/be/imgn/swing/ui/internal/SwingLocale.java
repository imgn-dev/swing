package be.imgn.swing.ui.internal;

import java.util.Locale;

/**
 * Gives Swing's own components the display locale, which they read their texts from when they are created, such as
 * {@code JOptionPane}'s Yes and No. The test harness replaces it with one that does nothing, so that tests running in
 * parallel don't share Swing's default locale.
 */
public interface SwingLocale {

    /**
     * Makes the locale Swing's default, on the EDT.
     *
     * @param locale the display locale
     */
    void apply(Locale locale);
}
