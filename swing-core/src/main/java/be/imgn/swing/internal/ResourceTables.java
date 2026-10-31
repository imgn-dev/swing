package be.imgn.swing.internal;

import be.imgn.gaia.core.io.ByteSource;
import be.imgn.swing.ResourceOpener;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlArray;
import be.imgn.toml.TomlBoolean;
import be.imgn.toml.TomlDocument;
import be.imgn.toml.TomlFloat;
import be.imgn.toml.TomlInteger;
import be.imgn.toml.TomlParseException;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads, merges and resolves the TOML resources of classes, through the {@link ResourceOpener}s found. */
public final class ResourceTables {

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, ResourceOpener> openers;
    private final Optional<String> platform;

    /**
     * Creates the tables.
     *
     * @param openers the openers, by package name
     * @param platform the platform id ({@code windows}, {@code macos} or {@code linux}), or empty for none
     */
    public ResourceTables(Map<String, ResourceOpener> openers, Optional<String> platform) {
        this.openers = Map.copyOf(openers);
        this.platform = platform;
    }

    /**
     * Returns the tables of the openers the {@link ServiceLoader} finds.
     *
     * @param platform the platform id, or empty for none
     * @return the tables
     */
    public static ResourceTables loaded(Optional<String> platform) {
        Map<String, ResourceOpener> found = new LinkedHashMap<>();
        for (ResourceOpener opener : ServiceLoader.load(ResourceOpener.class)) {
            found.putIfAbsent(opener.packageName(), opener);
        }
        return new ResourceTables(found, platform);
    }

    /**
     * Returns the opener of a package.
     *
     * @param packageName the package
     * @return the opener, if any
     */
    public Optional<ResourceOpener> opener(String packageName) {
        return Optional.ofNullable(openers.get(packageName));
    }

    /**
     * Returns every opener.
     *
     * @return the openers
     */
    public Collection<ResourceOpener> openers() {
        return openers.values();
    }

    /**
     * Returns the merged, resolved resources of a class in a locale.
     *
     * @param type the class
     * @param locale the locale
     * @return the table
     * @throws IllegalStateException if a file does not parse, or a reference names no value
     */
    public TomlTable merged(Class<?> type, Locale locale) {
        return resolve(unresolved(type, locale));
    }

    private TomlTable unresolved(Class<?> type, Locale locale) {
        Class<?> superclass = type.getSuperclass();
        TomlTable merged = superclass == null || superclass == Object.class
                ? TomlTable.builder().build()
                : unresolved(superclass, locale);
        for (String file : fileNames(type.getSimpleName(), locale)) {
            Optional<TomlTable> table = read(type.getPackageName(), file);
            if (table.isPresent()) {
                merged = merge(merged, table.get());
            }
        }
        return merged;
    }

    /**
     * Returns the names of the files of a class, in merge order.
     *
     * @param baseName the base name of the files, such as {@code NotesView}
     * @param locale the locale
     * @return the file names
     */
    public List<String> fileNames(String baseName, Locale locale) {
        List<String> stems = new ArrayList<>();
        stems.add(baseName);
        if (!locale.getLanguage().isEmpty()) {
            stems.add(baseName + "_" + locale.getLanguage());
            if (!locale.getCountry().isEmpty()) {
                stems.add(baseName + "_" + locale.getLanguage() + "_" + locale.getCountry());
            }
        }
        List<String> names = new ArrayList<>();
        for (String stem : stems) {
            names.add(stem + ".toml");
            platform.ifPresent(id -> names.add(stem + "." + id + ".toml"));
        }
        return names;
    }

    private Optional<TomlTable> read(String packageName, String fileName) {
        ResourceOpener opener = openers.get(packageName);
        if (opener == null) {
            return Optional.empty();
        }
        Optional<InputStream> stream = opener.open(fileName);
        if (stream.isEmpty()) {
            return Optional.empty();
        }
        String path = packageName.replace('.', '/') + "/" + fileName;
        try (InputStream in = stream.get()) {
            byte[] bytes = in.readAllBytes();
            TomlDocument document = Toml.parse(ByteSource.wrap(bytes));
            return Optional.of(document.root());
        } catch (TomlParseException e) {
            throw new IllegalStateException(path + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    /**
     * Merges two tables deeply: a table merges key by key, and any other value replaces the previous one.
     *
     * @param base the table merged into
     * @param override the table whose values win
     * @return the merged table
     */
    public static TomlTable merge(TomlTable base, TomlTable override) {
        TomlTable.Builder builder = base.toBuilder();
        override.entries().forEach((key, value) -> {
            TomlValue previous = base.entries().get(key);
            if (previous instanceof TomlTable previousTable && value instanceof TomlTable table) {
                builder.put(key, merge(previousTable, table));
            } else {
                builder.put(key, value);
            }
        });
        return builder.build();
    }

    /**
     * Resolves the {@code ${key.path}} references of every string of a table, in that table.
     *
     * @param table the table
     * @return the table with its references resolved
     * @throws IllegalStateException if a reference names no value, or references are cyclic
     */
    public static TomlTable resolve(TomlTable table) {
        return (TomlTable) resolveValue(table, table, new HashSet<>());
    }

    private static TomlValue resolveValue(TomlValue value, TomlTable root, Set<String> resolving) {
        return switch (value) {
            case TomlString string -> new TomlString(resolveString(string.value(), root, resolving));
            case TomlTable table -> {
                TomlTable.Builder builder = TomlTable.builder();
                table.entries().forEach((key, entry) -> builder.put(key, resolveValue(entry, root, resolving)));
                yield builder.build();
            }
            case TomlArray array ->
                new TomlArray(array.values().stream()
                        .map(element -> resolveValue(element, root, resolving))
                        .toList());
            default -> value;
        };
    }

    private static String resolveString(String text, TomlTable root, Set<String> resolving) {
        if (!text.contains("${")) {
            return text;
        }
        Matcher matcher = REFERENCE.matcher(text);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            if (!resolving.add(path)) {
                throw new IllegalStateException("The reference ${" + path + "} refers to itself");
            }
            TomlValue target = root.get(path)
                    .orElseThrow(() -> new IllegalStateException("The reference ${" + path + "} names no value"));
            String replacement = switch (target) {
                case TomlString string -> resolveString(string.value(), root, resolving);
                case TomlInteger integer -> Long.toString(integer.value());
                case TomlFloat number -> Double.toString(number.value());
                case TomlBoolean bool -> Boolean.toString(bool.value());
                default ->
                    throw new IllegalStateException(
                            "The reference ${" + path + "} names a value that is not a string, a number or a boolean");
            };
            resolving.remove(path);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
