package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.toml.TomlBoolean;
import be.imgn.toml.TomlDocument;
import be.imgn.toml.TomlFloat;
import be.imgn.toml.TomlInteger;
import be.imgn.toml.TomlPosition;
import be.imgn.toml.TomlPositions;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.swing.KeyStroke;
import org.jspecify.annotations.Nullable;

/** The build-time checks of a class's TOML resources, each file against its own parse. */
final class ResourceChecks {

    private static final Set<String> COMMAND_PROPERTIES =
            Set.of("text", "icon", "shortcut", "tool-tip-text", "accessible-name", "accessible-description");
    private static final Set<String> MENU_PROPERTIES = Set.of("text");
    private static final Set<String> THEMES = Set.of("system", "light", "dark");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");
    private static final Pattern SHORTCUT = Pattern.compile("\\bshortcut\\b");
    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    private final Shared shared;
    private final ProcessingContext context;
    private final Reporter reporter;

    ResourceChecks(Shared shared) {
        this.shared = shared;
        this.context = shared.context();
        this.reporter = shared.reporter();
    }

    /** Checks the files of a class, and records its package's resources and icons. */
    void check(TypeElement type) {
        String packageName = Java.packageOf(type).getQualifiedName().toString();
        Model model = new Model(
                type,
                packageName,
                Commands.all(context, type),
                ViewModel.componentFields(context, type),
                context.annotation(type, Names.APPLICATION).isPresent(),
                context.isSubtype(type.asType(), Names.SINGLE_FRAME),
                context.isSubtype(type.asType(), Names.DIALOG_VIEW));
        List<ResourceFile> files =
                shared.files().of(packageName, type.getSimpleName().toString());
        Set<String> icons = new TreeSet<>();
        for (ResourceFile file : files) {
            if (file.error() != null) {
                reporter.error(type, file, file.error().position(), MessagesStep.message(file.error()));
            } else {
                checkFile(model, file, file.parsed().orElseThrow(), icons);
            }
        }
        checkShortcuts(model, files);
        checkTexts(model, files);
        checkTranslations(model, files);
        if (!files.isEmpty() || !icons.isEmpty()) {
            shared.resources(Java.packageOf(type), icons, type);
        }
    }

    private void checkFile(Model model, ResourceFile file, TomlDocument document, Set<String> icons) {
        TomlTable root = document.root();
        TomlPositions positions = document.positions();
        for (Map.Entry<String, TomlValue> entry : root.entries().entrySet()) {
            String key = entry.getKey();
            TomlValue value = entry.getValue();
            switch (key) {
                case "commands" ->
                    table(model.type(), file, positions, root, key)
                            .ifPresent(commands -> checkCommands(model, file, positions, commands, icons));
                case "menus" ->
                    table(model.type(), file, positions, root, key)
                            .ifPresent(menus -> checkMenus(model, file, positions, menus));
                default -> {
                    if (value instanceof TomlTable component) {
                        checkComponent(model, file, positions, root, key, component, icons);
                    } else {
                        checkClassProperty(model, file, positions, root, key, value);
                    }
                }
            }
        }
    }

    private Optional<TomlTable> table(
            Element element, ResourceFile file, TomlPositions positions, TomlTable parent, String key) {
        if (parent.entries().get(key) instanceof TomlTable table) {
            return Optional.of(table);
        }
        reporter.error(element, file, positions.value(parent, key), key + " must be a table");
        return Optional.empty();
    }

    private Element elementOf(Model model, String id) {
        CommandInfo command = model.commands().get(id);
        return command != null && command.declaring().equals(model.type()) ? command.handler() : model.type();
    }

    private void checkCommands(
            Model model, ResourceFile file, TomlPositions positions, TomlTable commands, Set<String> icons) {
        for (String id : commands.entries().keySet()) {
            if (!model.commands().containsKey(id) && !Names.STANDARD_COMMANDS_IDS.contains(id)) {
                reporter.error(
                        model.type(),
                        file,
                        positions.key(commands, id),
                        "[commands." + id + "] names no" + " command of "
                                + model.type().getQualifiedName() + ", its superclasses or StandardCommands");
                continue;
            }
            Element element = elementOf(model, id);
            Optional<TomlTable> table = table(element, file, positions, commands, id);
            if (table.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, TomlValue> property : table.get().entries().entrySet()) {
                String key = property.getKey();
                if (!COMMAND_PROPERTIES.contains(key)) {
                    reporter.error(
                            element,
                            file,
                            positions.key(table.get(), key),
                            key + " is not a property of a" + " command: use one of "
                                    + new TreeSet<>(COMMAND_PROPERTIES));
                    continue;
                }
                if (!(property.getValue() instanceof TomlString string)) {
                    reporter.error(element, file, positions.value(table.get(), key), key + " must be a string");
                    continue;
                }
                checkCommandValue(model, file, positions, table.get(), key, string.value(), element, icons);
            }
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void checkCommandValue(
            Model model,
            ResourceFile file,
            TomlPositions positions,
            TomlTable table,
            String key,
            String value,
            Element element,
            Set<String> icons) {
        switch (key) {
            case "text" -> checkMnemonics(element, file, positions, table, key, value);
            case "icon" -> checkIcon(model, element, file, positions, table, key, value, icons);
            case "shortcut" -> {
                if (!value.contains("${") && Shortcut.parse(value) == null) {
                    reporter.error(
                            element,
                            file,
                            positions.value(table, key),
                            "The shortcut \"" + value + "\" does not parse as a key stroke");
                }
            }
            default -> {
                // Free text.
            }
        }
    }

    // Two commands of the class have different shortcuts on every platform: in the base file merged with each platform
    // variant, as the runtime reads them, and with shortcut as a modifier of its own, since it is Ctrl on one platform
    // and Meta on another. A collision is reported once, at the shortcut the later file of the chain set.
    private void checkShortcuts(Model model, List<ResourceFile> files) {
        List<ResourceFile> chain = files.stream()
                .filter(file -> file.language().isEmpty() && file.document() != null)
                .toList();
        Set<String> platforms = new LinkedHashSet<>();
        platforms.add("");
        chain.forEach(file -> platforms.add(file.platform()));
        Set<List<String>> reported = new HashSet<>();
        for (String platform : platforms) {
            Map<String, Shortcut> merged = new LinkedHashMap<>();
            for (ResourceFile file : chain) {
                if (file.platform().isEmpty() || file.platform().equals(platform)) {
                    merged.putAll(shortcuts(file, file.platform().isEmpty() ? 0 : 1));
                }
            }
            checkCollisions(model, merged.values(), reported);
        }
    }

    private void checkCollisions(Model model, Collection<Shortcut> shortcuts, Set<List<String>> reported) {
        Map<Shortcut.Key, Shortcut> seen = new HashMap<>();
        for (Shortcut shortcut : shortcuts) {
            Shortcut previous = seen.putIfAbsent(shortcut.key(), shortcut);
            if (previous == null) {
                continue;
            }
            boolean later = shortcut.rank() >= previous.rank();
            Shortcut at = later ? shortcut : previous;
            Shortcut other = later ? previous : shortcut;
            if (reported.add(
                    List.of(other.id(), other.file().name(), at.id(), at.file().name()))) {
                reporter.error(
                        elementOf(model, at.id()),
                        at.file(),
                        at.position(),
                        "The commands " + other.id() + " and " + at.id() + " have the same shortcut " + at.value()
                                + (other.file().equals(at.file())
                                        ? ""
                                        : " (" + other.id() + " in "
                                                + other.file().name() + ")"));
            }
        }
    }

    // The shortcuts a file sets, by command id: those that parse, with their position in the file.
    private static Map<String, Shortcut> shortcuts(ResourceFile file, int rank) {
        Map<String, Shortcut> shortcuts = new LinkedHashMap<>();
        TomlDocument document = file.parsed().orElseThrow();
        if (!(document.root().entries().get("commands") instanceof TomlTable commands)) {
            return shortcuts;
        }
        commands.entries().forEach((id, value) -> {
            if (value instanceof TomlTable command
                    && command.entries().get("shortcut") instanceof TomlString shortcut
                    && !shortcut.value().contains("${")) {
                Shortcut.Key key = Shortcut.parse(shortcut.value());
                if (key != null) {
                    shortcuts.put(
                            id,
                            new Shortcut(
                                    id,
                                    shortcut.value(),
                                    key,
                                    file,
                                    document.positions().value(command, "shortcut"),
                                    rank));
                }
            }
        });
        return shortcuts;
    }

    private void checkMnemonics(
            Element element, ResourceFile file, TomlPositions positions, TomlTable table, String key, String value) {
        int mnemonics = 0;
        int index = 0;
        while (index < value.length() - 1) {
            if (value.charAt(index) == '&') {
                if (value.charAt(index + 1) != '&') {
                    mnemonics++;
                }
                index += 2;
            } else {
                index++;
            }
        }
        if (mnemonics > 1) {
            reporter.error(
                    element,
                    file,
                    positions.value(table, key),
                    "The text \"" + value + "\" has more than one" + " & mnemonic: write a literal ampersand &&");
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void checkIcon(
            Model model,
            Element element,
            ResourceFile file,
            TomlPositions positions,
            TomlTable table,
            String key,
            String value,
            Set<String> icons) {
        String path;
        try {
            path = resolve(value, file.parsed().orElseThrow().root(), new HashSet<>());
        } catch (IllegalStateException | IllegalArgumentException e) {
            reporter.warning(element, file, positions.value(table, key), e.getMessage() + ": the icon is not checked");
            return;
        }
        if (shared.files().exists(model.packageName(), path)) {
            icons.add(path);
        } else {
            reporter.error(
                    element,
                    file,
                    positions.value(table, key),
                    "The icon " + path + (path.equals(value) ? "" : " (" + value + ")")
                            + " names no resource of the package " + model.packageName());
        }
    }

    // Resolves the ${key.path} references of a value in its own file, with the syntax the runtime resolves them in the
    // merged table. A reference to a key of another file, which only the merged table has, cannot be resolved here.
    private static String resolve(String text, TomlTable root, Set<String> resolving) {
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
                    .orElseThrow(() ->
                            new IllegalStateException("The reference ${" + path + "} names no key of this" + " file"));
            String replacement = switch (target) {
                case TomlString string -> resolve(string.value(), root, resolving);
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

    private void checkMenus(Model model, ResourceFile file, TomlPositions positions, TomlTable menus) {
        for (String id : menus.entries().keySet()) {
            Optional<TomlTable> table = table(model.type(), file, positions, menus, id);
            if (table.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, TomlValue> property : table.get().entries().entrySet()) {
                String key = property.getKey();
                if (!MENU_PROPERTIES.contains(key)) {
                    reporter.error(
                            model.type(),
                            file,
                            positions.key(table.get(), key),
                            key + " is not a property of a menu: use text");
                } else if (!(property.getValue() instanceof TomlString string)) {
                    reporter.error(model.type(), file, positions.value(table.get(), key), key + " must be a string");
                } else {
                    checkMnemonics(model.type(), file, positions, table.get(), key, string.value());
                }
            }
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void checkComponent(
            Model model,
            ResourceFile file,
            TomlPositions positions,
            TomlTable root,
            String field,
            TomlTable properties,
            Set<String> icons) {
        VariableElement component = model.components().get(field);
        if (component == null) {
            reporter.error(
                    model.type(),
                    file,
                    positions.key(root, field),
                    "[" + field + "] names no @Exposed component field of "
                            + model.type().getQualifiedName());
            return;
        }
        for (Map.Entry<String, TomlValue> entry : properties.entries().entrySet()) {
            String key = entry.getKey();
            Optional<ComponentProperty> property = ComponentProperty.of(key);
            if (property.isEmpty() || !property.get().appliesTo(context, component.asType())) {
                reporter.error(
                        component,
                        file,
                        positions.key(properties, key),
                        key + " is not a supported property" + " of " + component.asType());
                continue;
            }
            checkComponentValue(
                    model, file, positions, properties, key, property.get(), entry.getValue(), component, icons);
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void checkComponentValue(
            Model model,
            ResourceFile file,
            TomlPositions positions,
            TomlTable properties,
            String key,
            ComponentProperty property,
            TomlValue value,
            VariableElement component,
            Set<String> icons) {
        boolean typed = switch (property.kind()) {
            case BOOLEAN -> value instanceof TomlBoolean;
            case INTEGER -> value instanceof TomlInteger;
            default -> value instanceof TomlString;
        };
        if (!typed) {
            reporter.error(
                    component,
                    file,
                    positions.value(properties, key),
                    key + " must be a " + property.kind().name().toLowerCase(Locale.ROOT));
            return;
        }
        if (value instanceof TomlString string) {
            checkComponentString(model, file, positions, properties, key, property, string.value(), component, icons);
        }
    }

    // An icon resolves its references itself; any other value with one is checked after merging, at runtime.
    @SuppressWarnings("checkstyle:ParameterNumber")
    private void checkComponentString(
            Model model,
            ResourceFile file,
            TomlPositions positions,
            TomlTable properties,
            String key,
            ComponentProperty property,
            String value,
            VariableElement component,
            Set<String> icons) {
        if (property.kind() == ComponentProperty.Kind.ICON) {
            checkIcon(model, component, file, positions, properties, key, value, icons);
        } else if (value.contains("${")) {
            return;
        } else if (property.kind() == ComponentProperty.Kind.COLOR) {
            if (!COLOR.matcher(value).matches()) {
                reporter.error(
                        component,
                        file,
                        positions.value(properties, key),
                        "The color \"" + value + "\" is not \"#rrggbb\" or \"#rrggbbaa\"");
            }
        } else if (property == ComponentProperty.TEXT
                && (context.isSubtype(component.asType(), Names.ABSTRACT_BUTTON)
                        || context.isSubtype(component.asType(), Names.JLABEL))) {
            checkMnemonics(component, file, positions, properties, key, value);
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void checkClassProperty(
            Model model, ResourceFile file, TomlPositions positions, TomlTable root, String key, TomlValue value) {
        boolean applies = switch (key) {
            case "name", "theme" -> model.application();
            case "title" -> model.dialogView() || (model.application() && model.singleFrame());
            default -> false;
        };
        if (!applies) {
            reporter.error(
                    model.type(),
                    file,
                    positions.key(root, key),
                    key + " is not a property of "
                            + model.type().getQualifiedName()
                            + ": name and theme apply to the application class, title to a"
                            + " single-frame application class and to dialog views");
            return;
        }
        if (!(value instanceof TomlString string)) {
            reporter.error(model.type(), file, positions.value(root, key), key + " must be a string");
            return;
        }
        if ("theme".equals(key) && !THEMES.contains(string.value())) {
            reporter.error(
                    model.type(),
                    file,
                    positions.value(root, key),
                    "The theme \"" + string.value() + "\" is none of system, light or dark");
        }
    }

    // Every command and menu has a text in the base file; a standard one defaults to the framework's.
    private void checkTexts(Model model, List<ResourceFile> files) {
        List<TomlTable> bases = new ArrayList<>();
        Optional<TypeElement> current = Optional.of(model.type());
        while (current.isPresent()) {
            TypeElement type = current.get();
            shared
                    .files()
                    .of(
                            Java.packageOf(type).getQualifiedName().toString(),
                            type.getSimpleName().toString())
                    .stream()
                    .filter(ResourceFile::isBase)
                    .flatMap(file -> file.parsed().stream())
                    .forEach(document -> bases.add(document.root()));
            current = Commands.superclass(type);
        }
        for (CommandInfo command : Commands.declared(context, model.type())) {
            if (!command.isStandard() && bases.stream().noneMatch(base -> hasText(base, "commands", command.id()))) {
                reporter.error(
                        command.handler(),
                        "The command " + command.id() + " has no text in "
                                + model.type().getSimpleName() + ".toml: add text under [commands." + command.id()
                                + "]");
            }
        }
        files.stream()
                .filter(ResourceFile::isBase)
                .flatMap(file -> file.parsed().stream())
                .forEach(document -> {
                    if (document.root().entries().get("menus") instanceof TomlTable menus) {
                        for (String id : menus.entries().keySet()) {
                            if (!Names.STANDARD_MENU_IDS.contains(id) && !hasText(document.root(), "menus", id)) {
                                reporter.error(
                                        model.type(),
                                        files.getFirst(),
                                        document.positions().key(menus, id),
                                        "The menu " + id + " has no text in the base file");
                            }
                        }
                    }
                });
    }

    private static boolean hasText(TomlTable root, String kind, String id) {
        return root.entries().get(kind) instanceof TomlTable all
                && all.entries().get(id) instanceof TomlTable table
                && table.entries().get("text") instanceof TomlString;
    }

    // A locale file lacks no key of the base file, and has no key the base file lacks.
    private void checkTranslations(Model model, List<ResourceFile> files) {
        Optional<ResourceFile> base =
                files.stream().filter(ResourceFile::isBase).findFirst();
        if (base.isEmpty() || base.get().document() == null) {
            return;
        }
        TomlDocument baseDocument = base.get().document();
        Map<List<String>, Leaf> baseLeaves = leaves(baseDocument.root());
        for (ResourceFile file : files) {
            if (!file.isLocale() || file.document() == null) {
                continue;
            }
            Map<List<String>, Leaf> leaves = leaves(file.document().root());
            for (Map.Entry<List<String>, Leaf> leaf : leaves.entrySet()) {
                if (!baseLeaves.containsKey(leaf.getKey())) {
                    reporter.error(
                            leafElement(model, leaf.getKey()),
                            file,
                            file.document()
                                    .positions()
                                    .key(
                                            leaf.getValue().table(),
                                            leaf.getValue().key()),
                            "The key " + keyPath(leaf.getKey()) + " is not in the base file "
                                    + base.get().name());
                }
            }
            if (file.isLanguage()) {
                for (Map.Entry<List<String>, Leaf> leaf : baseLeaves.entrySet()) {
                    if (!leaves.containsKey(leaf.getKey())) {
                        reporter.error(
                                leafElement(model, leaf.getKey()),
                                base.get(),
                                baseDocument
                                        .positions()
                                        .key(
                                                leaf.getValue().table(),
                                                leaf.getValue().key()),
                                file.name() + " lacks the key " + keyPath(leaf.getKey()));
                    }
                }
            }
        }
    }

    private Element leafElement(Model model, List<String> path) {
        return path.size() >= 2 && "commands".equals(path.get(0)) ? elementOf(model, path.get(1)) : model.type();
    }

    // The path as TOML writes a dotted key, quoting the keys that are not bare.
    private static String keyPath(List<String> path) {
        return path.stream().map(ResourceChecks::key).collect(Collectors.joining("."));
    }

    private static String key(String key) {
        return BARE_KEY.matcher(key).matches()
                ? key
                : "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Map<List<String>, Leaf> leaves(TomlTable root) {
        Map<List<String>, Leaf> leaves = new LinkedHashMap<>();
        collect(root, List.of(), leaves);
        return leaves;
    }

    private static void collect(TomlTable table, List<String> prefix, Map<List<String>, Leaf> leaves) {
        table.entries().forEach((key, value) -> {
            List<String> path = Stream.concat(prefix.stream(), Stream.of(key)).toList();
            if (value instanceof TomlTable nested) {
                collect(nested, path, leaves);
            } else {
                leaves.put(path, new Leaf(table, key));
            }
        });
    }

    /** What the checks know about the class. */
    private record Model(
            TypeElement type,
            String packageName,
            Map<String, CommandInfo> commands,
            Map<String, VariableElement> components,
            boolean application,
            boolean singleFrame,
            boolean dialogView) {}

    /**
     * A leaf of a table: a key whose value is not a table.
     *
     * @param table the table that holds the key
     * @param key the key
     */
    private record Leaf(TomlTable table, String key) {}

    /**
     * A shortcut a file sets.
     *
     * @param id the command
     * @param value the shortcut as written
     * @param key the shortcut as compared
     * @param file the file that sets it
     * @param position the position of the value in the file
     * @param rank the place of the file in its chain: the base file first, then its platform variant
     */
    private record Shortcut(String id, String value, Key key, ResourceFile file, TomlPosition position, int rank) {

        /** Parses a shortcut; {@code null} if it does not parse. */
        static @Nullable Key parse(String value) {
            KeyStroke control = KeyStroke.getKeyStroke(SHORTCUT.matcher(value).replaceAll("ctrl"));
            KeyStroke meta = KeyStroke.getKeyStroke(SHORTCUT.matcher(value).replaceAll("meta"));
            return control == null || meta == null ? null : new Key(control, meta);
        }

        /**
         * A shortcut as compared: the key stroke it is where {@code shortcut} is Ctrl, and where it is Meta. Two
         * shortcuts are the same when they are the same key stroke on both, which keeps {@code shortcut S} apart from
         * {@code ctrl S} and from {@code meta S}.
         *
         * @param control the key stroke with {@code shortcut} read as Ctrl
         * @param meta the key stroke with {@code shortcut} read as Meta
         */
        record Key(KeyStroke control, KeyStroke meta) {}
    }
}
