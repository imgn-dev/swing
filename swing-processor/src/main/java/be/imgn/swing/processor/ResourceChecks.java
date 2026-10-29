package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.toml.TomlBoolean;
import be.imgn.toml.TomlDocument;
import be.imgn.toml.TomlInteger;
import be.imgn.toml.TomlPositions;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.swing.KeyStroke;

/** The build-time checks of a class's TOML resources, each file against its own parse. */
final class ResourceChecks {

    private static final Set<String> COMMAND_PROPERTIES =
            Set.of("text", "icon", "shortcut", "tool-tip-text", "accessible-name", "accessible-description");
    private static final Set<String> MENU_PROPERTIES = Set.of("text");
    private static final Set<String> THEMES = Set.of("system", "light", "dark");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");
    private static final Pattern SHORTCUT = Pattern.compile("\\bshortcut\\b");
    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

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
        Map<KeyStroke, String> shortcuts = new HashMap<>();
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
                checkCommandValue(model, file, positions, table.get(), key, string.value(), element, icons)
                        .ifPresent(keyStroke -> {
                            String previous = shortcuts.putIfAbsent(keyStroke, id);
                            if (previous != null) {
                                reporter.error(
                                        element,
                                        file,
                                        positions.value(table.get(), key),
                                        "The commands " + previous + " and " + id + " have the same shortcut "
                                                + string.value());
                            }
                        });
            }
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Optional<KeyStroke> checkCommandValue(
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
                if (value.contains("${")) {
                    return Optional.empty();
                }
                KeyStroke keyStroke =
                        KeyStroke.getKeyStroke(SHORTCUT.matcher(value).replaceAll("ctrl"));
                if (keyStroke == null) {
                    reporter.error(
                            element,
                            file,
                            positions.value(table, key),
                            "The shortcut \"" + value + "\" does not parse as a key stroke");
                }
                return Optional.ofNullable(keyStroke);
            }
            default -> {
                // Free text.
            }
        }
        return Optional.empty();
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
        if (shared.files().exists(model.packageName(), value)) {
            icons.add(value);
        } else {
            reporter.error(
                    element,
                    file,
                    positions.value(table, key),
                    "The icon " + value + " names no resource of" + " the package " + model.packageName());
        }
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
                    "[" + field + "] names no non-private" + " component field of "
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
        if (!(value instanceof TomlString string) || string.value().contains("${")) {
            return;
        }
        switch (property.kind()) {
            case COLOR -> {
                if (!COLOR.matcher(string.value()).matches()) {
                    reporter.error(
                            component,
                            file,
                            positions.value(properties, key),
                            "The color \"" + string.value() + "\" is not \"#rrggbb\" or \"#rrggbbaa\"");
                }
            }
            case ICON -> checkIcon(model, component, file, positions, properties, key, string.value(), icons);
            default -> {
                if (property == ComponentProperty.TEXT
                        && (context.isSubtype(component.asType(), Names.ABSTRACT_BUTTON)
                                || context.isSubtype(component.asType(), Names.JLABEL))) {
                    checkMnemonics(component, file, positions, properties, key, string.value());
                }
            }
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
}
