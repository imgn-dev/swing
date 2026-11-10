package be.imgn.swing.desktop.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Resources;
import be.imgn.toml.TomlString;
import java.text.MessageFormat;

/** The desktop's own texts, from {@code DesktopTexts.toml} and its locale variants. */
@Singleton
public final class DesktopTexts {

    // Read at each call, so that the texts follow the display locale.
    private final Resources resources;

    @Inject
    DesktopTexts(Resources resources) {
        this.resources = resources;
    }

    private String text(String key) {
        return resources.table(DesktopTexts.class).entries().get(key) instanceof TomlString text ? text.value() : key;
    }

    private String format(String key, Object... arguments) {
        return new MessageFormat(text(key), resources.locale()).format(arguments);
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
