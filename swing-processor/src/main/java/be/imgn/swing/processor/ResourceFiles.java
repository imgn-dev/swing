package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlDocument;
import be.imgn.toml.TomlParseException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Reads the TOML files of classes from {@code CLASS_OUTPUT}, where Maven copies resources before compiling, and from
 * the directory {@code -Abe.imgn.swing.resources} names. The locale and platform variants of a file are found by
 * listing its directory.
 */
final class ResourceFiles {

    private static final Pattern VARIANT =
            Pattern.compile("(?:_(?<language>[a-z]{2,8})(?:_(?<country>[A-Z]{2}|[0-9]{3}))?)?"
                    + "(?:\\.(?<platform>windows|macos|linux))?\\.toml");

    private final ProcessingContext context;
    private final Optional<Path> directory;
    private final Map<String, List<ResourceFile>> cache = new HashMap<>();

    ResourceFiles(ProcessingContext context, Optional<Path> directory) {
        this.context = context;
        this.directory = directory;
    }

    /**
     * Returns the files of a class: its base file and its variants, base first.
     *
     * @param packageName the package
     * @param baseName the base name, the simple name of the class
     * @return the files, empty if the class has none
     */
    List<ResourceFile> of(String packageName, String baseName) {
        return cache.computeIfAbsent(packageName + "/" + baseName, key -> read(packageName, baseName));
    }

    private List<ResourceFile> read(String packageName, String baseName) {
        Set<String> names = new LinkedHashSet<>();
        names.add(baseName + ".toml");
        directories(packageName, baseName + ".toml").forEach(dir -> names.addAll(list(dir, baseName)));
        List<ResourceFile> files = new ArrayList<>();
        for (String name : names) {
            Matcher matcher = VARIANT.matcher(name.substring(baseName.length()));
            if (!matcher.matches()) {
                continue;
            }
            open(packageName, name).ifPresent(text -> files.add(parse(name, matcher, text)));
        }
        files.sort(Comparator.comparing((ResourceFile file) -> !file.isBase()).thenComparing(ResourceFile::name));
        return List.copyOf(files);
    }

    private static ResourceFile parse(String name, Matcher matcher, String text) {
        String language = Optional.ofNullable(matcher.group("language")).orElse("");
        String country = Optional.ofNullable(matcher.group("country")).orElse("");
        String platform = Optional.ofNullable(matcher.group("platform")).orElse("");
        try {
            TomlDocument document = Toml.parse(text);
            return new ResourceFile(name, language, country, platform, document, null);
        } catch (TomlParseException e) {
            return new ResourceFile(name, language, country, platform, null, e);
        }
    }

    private List<Path> directories(String packageName, String probe) {
        List<Path> directories = new ArrayList<>();
        try {
            FileObject file = context.filer().getResource(StandardLocation.CLASS_OUTPUT, packageName, probe);
            URI uri = file.toUri();
            if ("file".equals(uri.getScheme())) {
                directories.add(Path.of(uri).getParent());
            }
        } catch (IOException | IllegalArgumentException e) {
            // Without a directory to list, only the base file is read.
        }
        directory.ifPresent(root -> directories.add(root.resolve(packageName.replace('.', '/'))));
        return directories;
    }

    private static List<String> list(Path directory, String baseName) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.startsWith(baseName) && name.endsWith(".toml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Reads a resource of a package as text.
     *
     * @param packageName the package
     * @param relativeName the name relative to the package
     * @return the text, if the resource exists
     */
    Optional<String> open(String packageName, String relativeName) {
        return bytes(packageName, relativeName).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Returns whether a resource of a package exists.
     *
     * @param packageName the package
     * @param relativeName the name relative to the package
     * @return {@code true} if it exists
     */
    boolean exists(String packageName, String relativeName) {
        return bytes(packageName, relativeName).isPresent();
    }

    private Optional<byte[]> bytes(String packageName, String relativeName) {
        try {
            FileObject file = context.filer().getResource(StandardLocation.CLASS_OUTPUT, packageName, relativeName);
            try (InputStream in = file.openInputStream()) {
                return Optional.of(in.readAllBytes());
            }
        } catch (IOException | IllegalArgumentException e) {
            // Not in the class output: try the extra directory.
        }
        if (directory.isPresent()) {
            Path file = directory.get().resolve(packageName.replace('.', '/')).resolve(relativeName);
            if (Files.isRegularFile(file)) {
                try {
                    return Optional.of(Files.readAllBytes(file));
                } catch (IOException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
