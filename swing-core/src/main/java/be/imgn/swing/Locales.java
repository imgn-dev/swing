package be.imgn.swing;

import java.util.Locale;

/**
 * The display locale, which the application may change while it runs. A singleton.
 *
 * <p>The locale starts as the choice saved by an earlier run, else the launch's {@link DisplayLocale}. A change
 * switches the {@link Resources} to the new locale, refreshes the open windows with the UI module, then changes
 * {@link #current()}, and saves the choice to {@code locale.toml} in the application's config directory.
 */
public interface Locales {

    /**
     * Returns the display locale.
     *
     * @return the observable locale
     */
    Observable<Locale> current();

    /**
     * Changes the display locale, and saves the choice. It may be called from any thread, and hands the change to the
     * {@link UiExecutor}. A locale equal to the current one changes nothing.
     *
     * @param locale the new locale
     * @throws NullPointerException if {@code locale} is {@code null}
     */
    void set(Locale locale);

    /**
     * Changes the display locale back to the launch's {@link DisplayLocale}, as {@link #set(Locale)} does, and deletes
     * the saved choice.
     */
    void reset();

    /**
     * Returns an implementation of a {@code @Messages} interface that reads the files of a locale, whatever the display
     * locale. It is cached per type and locale.
     *
     * @param type the {@code @Messages} interface
     * @param locale the locale of the messages
     * @param <M> the type of the messages
     * @return the messages in that locale
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code type} is not a {@code @Messages} interface compiled by swing's
     *     processor
     */
    <M> M messages(Class<M> type, Locale locale);
}
