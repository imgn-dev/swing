package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Resources;
import be.imgn.toml.TomlString;
import java.text.MessageFormat;

/** The framework's own texts, from {@code UiTexts.toml} and its locale variants. */
@Singleton
public final class UiTexts {

    // Read at each call, so that the texts follow the display locale.
    private final Resources resources;

    @Inject
    UiTexts(Resources resources) {
        this.resources = resources;
    }

    private String text(String key) {
        return resources.table(UiTexts.class).entries().get(key) instanceof TomlString text ? text.value() : key;
    }

    // Formats a text that is a MessageFormat pattern, in the display locale.
    private String format(String key, Object... arguments) {
        return new MessageFormat(text(key), resources.locale()).format(arguments);
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

    /**
     * Returns the question a save chooser asks before replacing a file.
     *
     * @param name the file's name
     * @return the text
     */
    public String replace(String name) {
        return format("replace", name);
    }

    /**
     * Returns the question of the save prompt.
     *
     * @param name the document's name
     * @return the text
     */
    public String saveChanges(String name) {
        return format("saveChanges", name);
    }

    /**
     * Returns the text of the save prompt's Save button, with its mnemonic.
     *
     * @return the text
     */
    public String save() {
        return text("save");
    }

    /**
     * Returns the text of the save prompt's Don't Save button, with its mnemonic.
     *
     * @return the text
     */
    public String discard() {
        return text("discard");
    }

    /**
     * Returns the title of the keymap dialog, also its table's accessible name.
     *
     * @return the text
     */
    public String keyboardShortcuts() {
        return text("keyboardShortcuts");
    }

    /**
     * Returns the header of the keymap dialog's column of commands.
     *
     * @return the text
     */
    public String command() {
        return text("command");
    }

    /**
     * Returns the header of the keymap dialog's column of shortcuts.
     *
     * @return the text
     */
    public String shortcut() {
        return text("shortcut");
    }

    /**
     * Returns the label of the keymap dialog's filter, with its mnemonic.
     *
     * @return the text
     */
    public String filter() {
        return text("filter");
    }

    /**
     * Returns the label of the keymap dialog's field that records a shortcut, with its mnemonic.
     *
     * @return the text
     */
    public String shortcutLabel() {
        return text("shortcutLabel");
    }

    /**
     * Returns the text of the keymap dialog's Remove button, with its mnemonic.
     *
     * @return the text
     */
    public String remove() {
        return text("remove");
    }

    /**
     * Returns the text of the keymap dialog's Reset button, with its mnemonic.
     *
     * @return the text
     */
    public String reset() {
        return text("reset");
    }

    /**
     * Returns the text of the keymap dialog's Reset All button, with its mnemonic.
     *
     * @return the text
     */
    public String resetAll() {
        return text("resetAll");
    }

    /**
     * Returns the warning that another command has a recorded shortcut.
     *
     * @param command the other command's text
     * @return the text
     */
    public String alsoUsedBy(String command) {
        return format("alsoUsedBy", command);
    }

    /**
     * Returns the placeholder of the command palette's field, also its accessible name.
     *
     * @return the text
     */
    public String typeCommand() {
        return text("typeCommand");
    }

    /**
     * Returns what the command palette shows when no command matches.
     *
     * @return the text
     */
    public String noCommand() {
        return text("noCommand");
    }

    /**
     * Returns the accessible name of the About dialog's website field.
     *
     * @return the text
     */
    public String website() {
        return text("website");
    }
}
