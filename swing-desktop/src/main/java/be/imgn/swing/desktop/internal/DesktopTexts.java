package be.imgn.swing.desktop.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.DisplayLocale;
import be.imgn.swing.Resources;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.text.MessageFormat;
import java.util.Locale;

/** The desktop's own texts, from {@code DesktopTexts.toml} and its locale variants. */
@Singleton
public final class DesktopTexts {

    private final TomlTable table;
    private final Locale locale;

    @Inject
    DesktopTexts(Resources resources, DisplayLocale locale) {
        this.table = resources.table(DesktopTexts.class);
        this.locale = locale.locale();
    }

    private String text(String key) {
        return table.entries().get(key) instanceof TomlString text ? text.value() : key;
    }

    private String format(String key, Object... arguments) {
        return new MessageFormat(text(key), locale).format(arguments);
    }

    /**
     * Returns the name of an untitled document.
     *
     * @return the text, such as {@code Untitled}
     */
    public String untitled() {
        return text("untitled");
    }

    /**
     * Returns the title of a document view's frame.
     *
     * @param document the document's name
     * @param application the application's displayed name
     * @return the title, such as {@code notes.txt – Memo}
     */
    public String documentTitle(String document, String application) {
        return format("documentTitle", document, application);
    }

    /**
     * Returns the message of a read that failed.
     *
     * @param file the file's name
     * @param message the failure's message
     * @return the text
     */
    public String openFailed(String file, String message) {
        return format("openFailed", file, message);
    }

    /**
     * Returns the message of a write that failed.
     *
     * @param file the file's name
     * @param message the failure's message
     * @return the text
     */
    public String saveFailed(String file, String message) {
        return format("saveFailed", file, message);
    }
}
