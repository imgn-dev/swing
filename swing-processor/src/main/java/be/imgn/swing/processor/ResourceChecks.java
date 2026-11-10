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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
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
                context.isSubtype(type.asType(), Names.FRAME_VIEW),
                isDocumentView(type),
                context.isSubtype(type.asType(), Names.DIALOG_VIEW),
                variants(type));
        List<ResourceFile> files =
                shared.files().of(packageName, type.getSimpleName().toString());
        Set<String> icons = new TreeSet<>();
        for (ResourceFile file : files) {
            if (file.isDark() && (!file.language().isEmpty() || !file.platform().isEmpty())) {
                reporter.error(
                        type,
                        file.name() + " is a dark variant with a locale or a platform part: only "
                                + type.getSimpleName() + ".dark.toml is read while the appearance is dark");
            } else if (file.error() != null) {
                reporter.error(type, file, file.error().position(), MessagesStep.message(file.error()));
            } else {
                checkFile(new Source(model, file, file.parsed().orElseThrow().positions(), icons));
            }
        }
        checkShortcuts(model, files);
        checkTexts(model, files);
        checkTranslations(model, files);
        checkInheritedNested(model);
        if (!files.isEmpty() || !icons.isEmpty()) {
            shared.resources(Java.packageOf(type), icons, type);
        }
    }

    // The generated injector of the class also reads the nested paths that the files of its superclasses name, which
    // their own checks judge from their own package: a field the class's package can't reach is an error of the class.
    // A superclass of the class's package reaches the same fields, so its own checks suffice.
    private void checkInheritedNested(Model model) {
        Optional<TypeElement> current = Commands.superclass(model.type());
        while (current.isPresent()) {
            TypeElement superclass = current.get();
            String owner = Java.packageOf(superclass).getQualifiedName().toString();
            if (!owner.equals(model.packageName())) {
                for (ResourceFile file :
                        shared.files().of(owner, superclass.getSimpleName().toString())) {
                    file.parsed().ifPresent(document -> {
                        TomlTable root = document.root();
                        checkInheritedNested(model, file, document.positions(), root);
                        if (root.entries().get("variants") instanceof TomlTable variants) {
                            for (TomlValue variant : variants.entries().values()) {
                                if (variant instanceof TomlTable table) {
                                    checkInheritedNested(model, file, document.positions(), table);
                                }
                            }
                        }
                    });
                }
            }
            current = Commands.superclass(superclass);
        }
    }

    // The tables of a base or variant table that name the class's component fields, other than its commands, menus and
    // variants.
    private void checkInheritedNested(Model model, ResourceFile file, TomlPositions positions, TomlTable table) {
        table.entries().forEach((key, value) -> {
            VariableElement component = model.components().get(key);
            if (!Set.of("commands", "menus", "variants", "flatlaf").contains(key)
                    && component != null
                    && value instanceof TomlTable properties) {
                checkInheritedNested(model, file, positions, new FieldTable(key, component, properties));
            }
        });
    }

    // The fields that a nested table names and that the superclass's checks don't reject, to any depth.
    private void checkInheritedNested(Model model, ResourceFile file, TomlPositions positions, FieldTable enclosing) {
        enclosing.properties().entries().forEach((key, value) -> {
            if (value instanceof TomlTable table) {
                ViewModel.componentField(context, enclosing.element().asType(), key)
                        .ifPresent(nested -> {
                            if (ViewModel.reachable(nested, model.packageName())) {
                                checkInheritedNested(model, file, positions, enclosing.nested(key, nested, table));
                            } else {
                                unreachable(model, file, positions, enclosing, key, nested);
                            }
                        });
            }
        });
    }

    private void checkFile(Source source) {
        Model model = source.model();
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        TomlTable root = file.parsed().orElseThrow().root();
        for (Map.Entry<String, TomlValue> entry : root.entries().entrySet()) {
            String key = entry.getKey();
            TomlValue value = entry.getValue();
            switch (key) {
                case "commands" ->
                    table(model.type(), file, positions, root, key)
                            .ifPresent(commands -> checkCommands(source, commands));
                case "menus" ->
                    table(model.type(), file, positions, root, key)
                            .ifPresent(menus -> checkMenus(model, file, positions, menus));
                case "variants" ->
                    table(model.type(), file, positions, root, key)
                            .ifPresent(variants -> checkVariants(source, root, variants));
                case "flatlaf" -> checkFlatLaf(source, root, value);
                default -> {
                    if (value instanceof TomlTable component) {
                        checkComponent(source, root, key, component);
                    } else {
                        checkClassProperty(source, root, key, value);
                    }
                }
            }
        }
    }

    // FlatLaf's keys and variables are its own and the application's: only the table's form is checked.
    private void checkFlatLaf(Source source, TomlTable root, TomlValue value) {
        Model model = source.model();
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        if (!model.application()) {
            reporter.error(
                    model.type(),
                    file,
                    positions.key(root, "flatlaf"),
                    "A [flatlaf] table is allowed in the resources of the @Application class only");
            return;
        }
        if (!(value instanceof TomlTable tokens)) {
            reporter.error(model.type(), file, positions.value(root, "flatlaf"), "flatlaf must be a table");
            return;
        }
        for (Map.Entry<String, TomlValue> token : tokens.entries().entrySet()) {
            if (token.getKey().isBlank()) {
                reporter.error(
                        model.type(), file, positions.key(tokens, token.getKey()), "A FlatLaf token has a blank key");
            } else if (!(token.getValue() instanceof TomlString
                    || token.getValue() instanceof TomlInteger
                    || token.getValue() instanceof TomlBoolean)) {
                reporter.error(
                        model.type(),
                        file,
                        positions.value(tokens, token.getKey()),
                        "The FlatLaf token " + token.getKey() + " must be a string, an integer or a boolean");
            }
        }
    }

    // swing-desktop may be absent from the class path: a class that can't implement DocumentView isn't one.
    private boolean isDocumentView(TypeElement type) {
        return context.elements().getTypeElement(Names.DOCUMENT_VIEW) != null
                && context.isSubtype(type.asType(), Names.DOCUMENT_VIEW);
    }

    // The variants of a dialog or frame view: the constants of its nested @Variants enum, in kebab case.
    private List<String> variants(TypeElement type) {
        List<String> variants = new ArrayList<>();
        for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
            if (nested.getKind() == ElementKind.ENUM
                    && context.annotation(nested, Names.VARIANTS).isPresent()) {
                for (Element constant : nested.getEnclosedElements()) {
                    if (constant.getKind() == ElementKind.ENUM_CONSTANT) {
                        variants.add(constant.getSimpleName()
                                .toString()
                                .toLowerCase(Locale.ROOT)
                                .replace('_', '-'));
                    }
                }
            }
        }
        return variants;
    }

    // Each table of [variants] names a variant, and is laid out like the base table, without commands and menus. The
    // base file has a table for every variant.
    private void checkVariants(Source source, TomlTable root, TomlTable variants) {
        Model model = source.model();
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        if (model.variants().isEmpty()) {
            reporter.error(
                    model.type(),
                    file,
                    positions.key(root, "variants"),
                    "[variants] needs an enum annotated @Variants nested in "
                            + model.type().getQualifiedName());
            return;
        }
        for (Map.Entry<String, TomlValue> entry : variants.entries().entrySet()) {
            String name = entry.getKey();
            if (!model.variants().contains(name)) {
                reporter.error(
                        model.type(),
                        file,
                        positions.key(variants, name),
                        "[variants." + name + "] names no constant of the @Variants enum of "
                                + model.type().getQualifiedName() + ": " + model.variants());
            } else if (entry.getValue() instanceof TomlTable variant) {
                checkVariant(source, variant);
            } else {
                reporter.error(
                        model.type(), file, positions.value(variants, name), "variants." + name + " must be a table");
            }
        }
        if (file.isBase()) {
            for (String name : model.variants()) {
                if (!variants.entries().containsKey(name)) {
                    reporter.error(
                            model.type(),
                            file,
                            positions.key(root, "variants"),
                            "The variant " + name + " has no table [variants." + name + "] in "
                                    + model.type().getSimpleName() + ".toml");
                }
            }
        }
    }

    private void checkVariant(Source source, TomlTable variant) {
        Model model = source.model();
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        for (Map.Entry<String, TomlValue> entry : variant.entries().entrySet()) {
            String key = entry.getKey();
            if (Set.of("commands", "menus", "variants", "flatlaf").contains(key)) {
                reporter.error(
                        model.type(),
                        file,
                        positions.key(variant, key),
                        "A variant holds no " + key + ": they are in the base table, for every variant");
            } else if (entry.getValue() instanceof TomlTable component) {
                checkComponent(source, variant, key, component);
            } else {
                checkClassProperty(source, variant, key, entry.getValue());
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
        return command != null && command.declaring().equals(model.type()) ? command.element() : model.type();
    }

    private void checkCommands(Source source, TomlTable commands) {
        Model model = source.model();
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        for (String id : commands.entries().keySet()) {
            if (!model.commands().containsKey(id)
                    && !Names.STANDARD_COMMANDS_IDS.contains(id)
                    && !(model.documentView() && Names.DOCUMENT_COMMANDS_IDS.contains(id))) {
                reporter.error(
                        model.type(),
                        file,
                        positions.key(commands, id),
                        "[commands." + id + "] names no" + " command of "
                                + model.type().getQualifiedName() + ", its superclasses or StandardCommands"
                                + (model.documentView() ? ", nor a document command" : ""));
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
                checkCommandValue(source, table.get(), key, string.value(), element);
            }
        }
    }

    private void checkCommandValue(Source source, TomlTable table, String key, String value, Element element) {
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        switch (key) {
            case "text" -> checkMnemonics(element, file, positions, table, key, value);
            case "icon" -> checkIcon(source, element, table, key, value);
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
        // The dark variant comes last, as the runtime reads it.
        List<ResourceFile> chain = files.stream()
                .filter(file -> file.language().isEmpty() && file.document() != null)
                .sorted(Comparator.comparing(ResourceFile::isDark))
                .toList();
        Set<String> platforms = new LinkedHashSet<>();
        platforms.add("");
        chain.forEach(file -> platforms.add(file.platform()));
        Set<List<String>> reported = new HashSet<>();
        for (String platform : platforms) {
            Map<String, Shortcut> merged = new LinkedHashMap<>();
            for (ResourceFile file : chain) {
                if (file.platform().isEmpty() || file.platform().equals(platform)) {
                    merged.putAll(
                            shortcuts(file, file.isDark() ? 2 : file.platform().isEmpty() ? 0 : 1));
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

    private void checkIcon(Source source, Element element, TomlTable table, String key, String value) {
        Model model = source.model();
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        String path;
        try {
            path = resolve(value, file.parsed().orElseThrow().root(), new HashSet<>());
        } catch (IllegalStateException | IllegalArgumentException e) {
            reporter.warning(element, file, positions.value(table, key), e.getMessage() + ": the icon is not checked");
            return;
        }
        if (shared.files().exists(model.packageName(), path)) {
            source.icons().add(path);
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

    private void checkComponent(Source source, TomlTable root, String field, TomlTable properties) {
        Model model = source.model();
        VariableElement component = model.components().get(field);
        if (component == null) {
            reporter.error(
                    model.type(),
                    source.file(),
                    source.positions().key(root, field),
                    "[" + field + "] names no @Exposed component field of "
                            + model.type().getQualifiedName());
            return;
        }
        checkProperties(source, new FieldTable(field, component, properties));
    }

    // The keys of a component field's table: its properties, and the tables of the component fields of its type, each
    // checked the same way, to any depth.
    private void checkProperties(Source source, FieldTable field) {
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        VariableElement component = field.element();
        TomlTable properties = field.properties();
        for (Map.Entry<String, TomlValue> entry : properties.entries().entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() instanceof TomlTable table) {
                nestedField(source.model(), file, positions, field, key)
                        .ifPresent(nested -> checkProperties(source, field.nested(key, nested, table)));
                continue;
            }
            Optional<ComponentProperty> property = ComponentProperty.of(key);
            if (property.isEmpty() || !property.get().appliesTo(context, component.asType())) {
                reporter.error(
                        field.owner(),
                        file,
                        positions.key(properties, key),
                        key + " is not a supported property" + " of " + component.asType());
                continue;
            }
            checkComponentValue(source, field, key, property.get(), entry.getValue());
        }
    }

    // A nested table names an @Exposed component field of the enclosing field's declared type, which the generated
    // injector, in the view's package, reads through the enclosing fields.
    private Optional<VariableElement> nestedField(
            Model model, ResourceFile file, TomlPositions positions, FieldTable enclosing, String key) {
        String path = enclosing.path() + "." + key;
        TypeMirror type = enclosing.element().asType();
        Optional<VariableElement> nested = ViewModel.componentField(context, type, key);
        if (nested.isEmpty()) {
            reporter.error(
                    enclosing.owner(),
                    file,
                    positions.key(enclosing.properties(), key),
                    "[" + path + "] names no @Exposed component field of " + type);
            return Optional.empty();
        }
        if (!ViewModel.reachable(nested.get(), model.packageName())) {
            unreachable(model, file, positions, enclosing, key, nested.get());
            return Optional.empty();
        }
        return nested;
    }

    private void unreachable(
            Model model,
            ResourceFile file,
            TomlPositions positions,
            FieldTable enclosing,
            String key,
            VariableElement nested) {
        reporter.error(
                enclosing.owner(),
                file,
                positions.key(enclosing.properties(), key),
                "[" + enclosing.path() + "." + key + "] names the field " + key + " of "
                        + Objects.requireNonNull(nested.getEnclosingElement())
                        + ", which the generated code of " + model.type().getQualifiedName()
                        + " can't reach: make it public, or move it to the package " + model.packageName());
    }

    private void checkComponentValue(
            Source source, FieldTable field, String key, ComponentProperty property, TomlValue value) {
        TomlTable properties = field.properties();
        boolean typed = switch (property.kind()) {
            case BOOLEAN -> value instanceof TomlBoolean;
            case INTEGER -> value instanceof TomlInteger;
            default -> value instanceof TomlString;
        };
        if (!typed) {
            reporter.error(
                    field.owner(),
                    source.file(),
                    source.positions().value(properties, key),
                    key + " must be a " + property.kind().name().toLowerCase(Locale.ROOT));
            return;
        }
        if (value instanceof TomlString string) {
            checkComponentString(source, field, key, property, string.value());
        }
    }

    // An icon resolves its references itself; any other value with one is checked after merging, at runtime.
    private void checkComponentString(
            Source source, FieldTable field, String key, ComponentProperty property, String value) {
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        TomlTable properties = field.properties();
        VariableElement component = field.element();
        if (property.kind() == ComponentProperty.Kind.ICON) {
            checkIcon(source, field.owner(), properties, key, value);
        } else if (value.contains("${")) {
            return;
        } else if (property.kind() == ComponentProperty.Kind.COLOR) {
            if (!COLOR.matcher(value).matches()) {
                reporter.error(
                        field.owner(),
                        file,
                        positions.value(properties, key),
                        "The color \"" + value + "\" is not \"#rrggbb\" or \"#rrggbbaa\"");
            }
        } else if (property == ComponentProperty.TEXT
                && (context.isSubtype(component.asType(), Names.ABSTRACT_BUTTON)
                        || context.isSubtype(component.asType(), Names.JLABEL))) {
            checkMnemonics(field.owner(), file, positions, properties, key, value);
        }
    }

    private void checkClassProperty(Source source, TomlTable root, String key, TomlValue value) {
        Model model = source.model();
        ResourceFile file = source.file();
        TomlPositions positions = source.positions();
        boolean applies = switch (key) {
            case "name", "theme", "description", "copyright", "website", "icon" -> model.application();
            case "title" -> model.dialogView() || (model.frameView() && !model.documentView());
            default -> false;
        };
        if (!applies) {
            reporter.error(
                    model.type(),
                    file,
                    positions.key(root, key),
                    key + " is not a property of "
                            + model.type().getQualifiedName()
                            + ": name, theme, description, copyright, website and icon apply to the application"
                            + " class, title to frame views and dialog views, but not to document views, whose title"
                            + " follows their document");
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
        } else if ("website".equals(key)) {
            checkWebsite(model, file, positions, root, string.value());
        } else if ("icon".equals(key)) {
            checkIcon(source, model.type(), root, key, string.value());
        }
    }

    // Resolves the website's references as an icon's are: one to a key of another file leaves it unchecked.
    private void checkWebsite(Model model, ResourceFile file, TomlPositions positions, TomlTable root, String value) {
        String website;
        try {
            website = resolve(value, file.parsed().orElseThrow().root(), new HashSet<>());
        } catch (IllegalStateException | IllegalArgumentException e) {
            reporter.warning(
                    model.type(),
                    file,
                    positions.value(root, "website"),
                    e.getMessage() + ": the website is not checked");
            return;
        }
        if (!isWebAddress(website)) {
            reporter.error(
                    model.type(),
                    file,
                    positions.value(root, "website"),
                    "The website \"" + website + "\"" + (website.equals(value) ? "" : " (" + value + ")")
                            + " is not an absolute http or https URI");
        }
    }

    private static boolean isWebAddress(String value) {
        try {
            URI uri = new URI(value);
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException e) {
            return false;
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
                        command.element(),
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
            if (!(file.isLocale() || file.isDark()) || file.document() == null) {
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
            boolean frameView,
            boolean documentView,
            boolean dialogView,
            List<String> variants) {}

    /**
     * A file being checked, with what its checks share.
     *
     * @param model what the checks know about the class
     * @param file the file
     * @param positions the positions of the file's keys and values
     * @param icons the icons the checks found, which the package's resources record
     */
    private record Source(Model model, ResourceFile file, TomlPositions positions, Set<String> icons) {}

    /**
     * A component field that a table of the resources names.
     *
     * @param path the field's path from the class's own fields, such as {@code home.streetLabel}
     * @param element the field
     * @param properties the field's table
     * @param owner the class's own field that the path starts from, which the diagnostics of the table are attached to,
     *     since a nested field may be declared in another file or another compilation
     */
    private record FieldTable(String path, VariableElement element, TomlTable properties, VariableElement owner) {

        /** A table that names one of the class's own fields. */
        FieldTable(String path, VariableElement element, TomlTable properties) {
            this(path, element, properties, element);
        }

        /** The table of a component field of this table's field. */
        FieldTable nested(String key, VariableElement nested, TomlTable table) {
            return new FieldTable(path + "." + key, nested, table, owner);
        }
    }

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
