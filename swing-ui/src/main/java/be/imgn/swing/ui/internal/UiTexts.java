package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Resources;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;

/** The framework's own texts, from {@code UiTexts.toml} and its locale variants. */
@Singleton
public final class UiTexts {

    private final TomlTable table;

    @Inject
    UiTexts(Resources resources) {
        this.table = resources.table(UiTexts.class);
    }

    private String text(String key) {
        return table.entries().get(key) instanceof TomlString text ? text.value() : key;
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
     * Returns the label of the version in the About dialog.
     *
     * @return the text
     */
    public String version() {
        return text("version");
    }

    /**
     * Returns the label of the vendor in the About dialog.
     *
     * @return the text
     */
    public String vendor() {
        return text("vendor");
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
