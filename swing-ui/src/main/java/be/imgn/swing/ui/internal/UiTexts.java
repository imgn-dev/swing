package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.DisplayLocale;
import be.imgn.swing.Resources;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.text.MessageFormat;
import java.util.Locale;

/** The framework's own texts, from {@code UiTexts.toml} and its locale variants. */
@Singleton
public final class UiTexts {

    private final TomlTable table;
    private final Locale locale;

    @Inject
    UiTexts(Resources resources, DisplayLocale locale) {
        this.table = resources.table(UiTexts.class);
        this.locale = locale.locale();
    }

    private String text(String key) {
        return table.entries().get(key) instanceof TomlString text ? text.value() : key;
    }

    // Formats a text that is a MessageFormat pattern, in the display locale.
    private String format(String key, Object... arguments) {
        return new MessageFormat(text(key), locale).format(arguments);
    }

    /**
     * Returns the text of the Cancel button of the progress indicator.
     *
     * @return the text
     */
    public String cancel() {
        return text("cancel");
    }

    /**
     * Returns the text of the Copy button of the error dialog.
     *
     * @return the text
     */
    public String copy() {
        return text("copy");
    }

    /**
     * Returns the label of the details of the error dialog.
     *
     * @return the text
     */
    public String details() {
        return text("details");
    }

    /**
     * Returns the title of the error dialog.
     *
     * @return the text
     */
    public String error() {
        return text("error");
    }

    /**
     * Returns the line of the About dialog that gives the version.
     *
     * @param version the application's version
     * @return the text, such as {@code Version 1.0}
     */
    public String version(String version) {
        return format("version", version);
    }

    /**
     * Returns the line of the About dialog that gives the vendor.
     *
     * @param vendor the application's vendor
     * @return the text, such as {@code By imgn}
     */
    public String by(String vendor) {
        return format("by", vendor);
    }

    /**
     * Returns the text of the button that closes a message.
     *
     * @return the text
     */
    public String close() {
        return text("close");
    }
}
