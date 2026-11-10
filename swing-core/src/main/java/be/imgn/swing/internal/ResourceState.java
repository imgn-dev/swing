package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Appearance;
import be.imgn.swing.DisplayLocale;
import be.imgn.toml.TomlTable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the resources are read in, the display locale and the appearance, with the tables cached for them. A change
 * replaces the whole snapshot, its caches included, so that a reader never pairs a table with another locale.
 */
@Singleton
public final class ResourceState {

    private volatile Snapshot snapshot;

    /**
     * Creates the state, in the launch's locale and the light appearance.
     *
     * @param locale the launch's display locale
     */
    @Inject
    public ResourceState(DisplayLocale locale) {
        this.snapshot = new Snapshot(locale.locale(), Appearance.LIGHT);
    }

    /**
     * Returns the current snapshot.
     *
     * @return the snapshot
     */
    public Snapshot snapshot() {
        return snapshot;
    }

    /**
     * Switches to a locale, with empty caches.
     *
     * @param locale the locale
     */
    public synchronized void locale(Locale locale) {
        snapshot = new Snapshot(locale, snapshot.appearance());
    }

    /**
     * Switches to an appearance, with empty caches.
     *
     * @param appearance the appearance
     */
    public synchronized void appearance(Appearance appearance) {
        snapshot = new Snapshot(snapshot.locale(), appearance);
    }

    /** Drops every cached table, so that the files are read again, after a reload. */
    public synchronized void reload() {
        snapshot = new Snapshot(snapshot.locale(), snapshot.appearance());
    }

    /**
     * The locale and the appearance, with the tables merged for them.
     *
     * @param locale the display locale
     * @param appearance the appearance, whose dark variants are read while it is dark
     * @param tables the merged tables of classes in the display locale
     * @param localized the merged tables of classes in other locales
     */
    public record Snapshot(
            Locale locale,
            Appearance appearance,
            Map<Class<?>, TomlTable> tables,
            Map<Localized, TomlTable> localized) {

        Snapshot(Locale locale, Appearance appearance) {
            this(locale, appearance, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        }
    }

    /**
     * A class in a locale.
     *
     * @param type the class
     * @param locale the locale
     */
    public record Localized(Class<?> type, Locale locale) {}
}
