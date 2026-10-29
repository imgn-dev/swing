package be.imgn.swing;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.internal.ResourceTables;
import be.imgn.toml.TomlTable;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The resources of classes: their TOML files, merged along the class hierarchy and across the locale and platform
 * variants, and cached per class and locale.
 *
 * <p>For a class {@code p.NotesView}, in the locale {@code fr_BE} on macOS, these files of {@code p/} are read, each
 * one overriding the previous: the resources of the superclass, merged the same way; {@code NotesView.toml};
 * {@code NotesView.macos.toml}; {@code NotesView_fr.toml}, then {@code NotesView_fr.macos.toml};
 * {@code NotesView_fr_BE.toml}, then {@code NotesView_fr_BE.macos.toml}. A table merges key by key; any other value
 * replaces the previous one. The {@code ${key.path}} references in strings are then resolved in the merged table.
 *
 * <p>The files are read through the {@link ResourceOpener} of their package, found through the
 * {@link java.util.ServiceLoader}.
 */
@Singleton
public final class Resources {

    private final ResourceTables tables;
    private final Locale locale;
    private final Map<Class<?>, TomlTable> cache = new ConcurrentHashMap<>();

    @Inject
    Resources(Platform platform, DisplayLocale locale) {
        this(ResourceTables.loaded(platformId(platform)), locale.locale());
    }

    private Resources(ResourceTables tables, Locale locale) {
        this.tables = tables;
        this.locale = locale;
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
     * Returns the merged resources of a class, in the display locale. A nested class's files are named after its simple
     * name.
     *
     * @param type the class
     * @return the merged table, empty if the class has no resources
     * @throws IllegalStateException if a file does not parse, or a reference names no string
     */
    public TomlTable table(Class<?> type) {
        return cache.computeIfAbsent(type, key -> tables.merged(key, locale));
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
