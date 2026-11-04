package be.imgn.swing;

/**
 * Holds the value of every {@link Settings} record, loaded at startup from the config directory. A singleton.
 *
 * <p>Setting a record's property to a different value saves the record, on a virtual thread named {@code settings}.
 * Settings are observable like any property: a view follows them with {@code ui.bind(editor::setFont, settings.map(s ->
 * font(s.fontSize())))}.
 */
public interface SettingsStore {

    /**
     * Returns the property of a settings record: the same property on every call for a type.
     *
     * @param type the settings record
     * @param <T> the settings record
     * @return its property
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalArgumentException if the type has no settings descriptor
     * @throws IllegalStateException if the settings are not loaded yet, such as from a startup step that runs before
     *     theirs
     */
    <T extends Record> Property<T> get(Class<T> type);
}
