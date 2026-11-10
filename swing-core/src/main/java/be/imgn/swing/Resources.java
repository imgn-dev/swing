package be.imgn.swing;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.internal.ResourceState;
import be.imgn.swing.internal.ResourceTables;
import be.imgn.toml.TomlTable;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The resources of classes: their TOML files, merged along the class hierarchy and across the locale and platform
 * variants, and cached per class and locale.
 *
 * <p>For a class {@code p.NotesView}, in the locale {@code fr_BE} on macOS, these files of {@code p/} are read, each
 * one overriding the previous: the resources of the superclass, merged the same way; {@code NotesView.toml};
 * {@code NotesView.macos.toml}; {@code NotesView_fr.toml}, then {@code NotesView_fr.macos.toml};
 * {@code NotesView_fr_BE.toml}, then {@code NotesView_fr_BE.macos.toml}. While the {@link Appearances appearance} is
 * dark, {@code NotesView.dark.toml} is read right after {@code NotesView.macos.toml}. A table merges key by key; any
 * other value replaces the previous one. The {@code ${key.path}} references in strings are then resolved in the merged
 * table.
 *
 * <p>{@link #table(Class)} reads the current display locale, which {@link Locales} changes: a change drops the cached
 * tables, so that the next call merges the files of the new locale.
 *
 * <p>The files are read through the {@link ResourceOpener} of their package, found through the
 * {@link java.util.ServiceLoader}.
 */
@Singleton
public final class Resources {

    private final ResourceTables tables;
    private final ResourceState state;

    @Inject
    Resources(Platform platform, ResourceState state) {
        this(ResourceTables.loaded(platformId(platform)), state);
    }

    Resources(ResourceTables tables, ResourceState state) {
        this.tables = tables;
        this.state = state;
    }

    private static Optional<String> platformId(Platform platform) {
        if (platform.isWindows()) {
            return Optional.of("windows");
        }
        if (platform.isMacOS()) {
            return Optional.of("macos");
        }
        return platform.isLinux() ? Optional.of("linux") : Optional.empty();
    }

    /**
     * Returns the merged resources of a class, in the current display locale. A nested class's files are named after
     * its simple name. The table is cached until the locale changes: two calls in one locale return the same instance.
     *
     * @param type the class
     * @return the merged table, empty if the class has no resources
     * @throws IllegalStateException if a file does not parse, or a reference names no string
     */
    public TomlTable table(Class<?> type) {
        ResourceState.Snapshot snapshot = state.snapshot();
        return snapshot.tables().computeIfAbsent(type, key -> tables.merged(key, snapshot.locale(), dark(snapshot)));
    }

    /**
     * Returns the merged resources of a class, in a given locale, whatever the display locale. The table is cached.
     *
     * @param type the class
     * @param locale the locale
     * @return the merged table, empty if the class has no resources
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if a file does not parse, or a reference names no string
     */
    public TomlTable table(Class<?> type, Locale locale) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(locale, "locale");
        ResourceState.Snapshot snapshot = state.snapshot();
        return snapshot.localized()
                .computeIfAbsent(
                        new ResourceState.Localized(type, locale), key -> tables.merged(type, locale, dark(snapshot)));
    }

    private static boolean dark(ResourceState.Snapshot snapshot) {
        return snapshot.appearance() == Appearance.DARK;
    }

    /**
     * Returns the display locale that {@link #table(Class)} reads now.
     *
     * @return the locale
     */
    public Locale locale() {
        return state.snapshot().locale();
    }

    /**
     * Returns the opener of a package's resources.
     *
     * @param packageName the package
     * @return the opener, or empty if the package has no resources
     */
    public Optional<ResourceOpener> opener(String packageName) {
        return tables.opener(packageName);
    }

    /**
     * Returns every opener found.
     *
     * @return the openers, one per package with resources
     */
    public Collection<ResourceOpener> openers() {
        return tables.openers();
    }
}
