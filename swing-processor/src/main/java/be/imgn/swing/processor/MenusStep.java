package be.imgn.swing.processor;

import be.imgn.gaia.core.text.NamingConvention;
import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.SourceScope;
import be.imgn.gaia.processing.Step;
import be.imgn.toml.TomlDocument;
import be.imgn.toml.TomlTable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

/**
 * Generates the {@code <Class>Menus} of each class whose resources have {@code [menus.*]} tables other than the
 * standard menus': the application class, the classes with commands and the views.
 */
final class MenusStep implements Step {

    private static final Set<String> RESERVED = Set.of("file", "edit", "view", "window", "help", "menu");

    private final Shared shared;
    private final ProcessingContext context;
    private final Set<String> generated = new HashSet<>();

    MenusStep(Shared shared) {
        this.shared = shared;
        this.context = shared.context();
    }

    @Override
    public Set<String> annotations() {
        return Set.of();
    }

    @Override
    public boolean processesRootElements() {
        return true;
    }

    @Override
    public Set<Element> process(AnnotatedElements elements) {
        context.forEach(shared.peekPending(), this::generate);
        context.forEach(heldBack(elements), this::generate);
        return Set.of();
    }

    /**
     * Returns the classes of the packages of this round's root elements whose base file has {@code [menus.*]} tables. A
     * view that injects its own generated menus class is held back until that class exists, so it is found through its
     * file rather than as a root element.
     */
    private List<TypeElement> heldBack(AnnotatedElements elements) {
        Set<String> packages = new LinkedHashSet<>();
        for (Element root : elements.rootElements()) {
            if (root.getKind() != ElementKind.MODULE) {
                packages.add(Java.packageOf(root).getQualifiedName().toString());
            }
        }
        List<TypeElement> classes = new ArrayList<>();
        for (String packageName : packages) {
            for (String name : shared.files().baseNames(packageName)) {
                TypeElement type =
                        context.elements().getTypeElement(packageName.isEmpty() ? name : packageName + "." + name);
                if (type != null
                        && type.getKind() == ElementKind.CLASS
                        && context.isSubtype(type.asType(), Names.VIEW)) {
                    classes.add(type);
                }
            }
        }
        return classes;
    }

    private void generate(TypeElement type) {
        if (!generated.add(type.getQualifiedName().toString())) {
            return;
        }
        Map<String, String> menus = new LinkedHashMap<>();
        Map<String, String> byMethod = new LinkedHashMap<>();
        boolean valid = true;
        for (String id : menuIds(type)) {
            if (Names.STANDARD_MENU_IDS.contains(id) || menus.containsKey(id)) {
                continue;
            }
            Optional<String> method = methodName(id);
            if (method.isEmpty() || RESERVED.contains(method.get())) {
                shared.reporter()
                        .error(
                                type,
                                "The menu id " + id + " does not convert to the name of a method of "
                                        + Menus.generatedName(type)
                                        + (method.isPresent() ? ": " + method.get() + " is taken" : ""));
                valid = false;
                continue;
            }
            String previous = byMethod.put(method.get(), id);
            if (previous != null) {
                shared.reporter()
                        .error(type, "The menu ids " + previous + " and " + id + " both convert to " + method.get());
                valid = false;
                continue;
            }
            menus.put(id, method.get());
        }
        if (valid && !menus.isEmpty()) {
            String simpleName = Menus.generatedName(type);
            context.sources()
                    .write(Java.qualified(type, simpleName), scope -> body(scope, type, simpleName, menus), type);
        }
    }

    private List<String> menuIds(TypeElement type) {
        List<String> ids = new ArrayList<>();
        Optional<TypeElement> current = Optional.of(type);
        while (current.isPresent()) {
            TypeElement element = current.get();
            shared
                    .files()
                    .of(
                            Java.packageOf(element).getQualifiedName().toString(),
                            element.getSimpleName().toString())
                    .stream()
                    .filter(ResourceFile::isBase)
                    .flatMap(file -> file.parsed().stream())
                    .map(TomlDocument::root)
                    .forEach(root -> {
                        if (root.entries().get("menus") instanceof TomlTable menus) {
                            ids.addAll(menus.entries().keySet());
                        }
                    });
            current = Commands.superclass(element);
        }
        return ids;
    }

    /** Returns the method of a menu: its id in lower camel case, if that is a Java identifier. */
    static Optional<String> methodName(String id) {
        try {
            String name = NamingConvention.LOWER_CAMEL.from(id);
            return Java.isIdentifier(name) ? Optional.of(name) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String body(SourceScope scope, TypeElement type, String simpleName, Map<String, String> menus) {
        String owner = scope.name(type);
        String boundMenu = scope.name(Names.BOUND_MENU);
        List<String> fields = new ArrayList<>();
        List<String> assignments = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        menus.forEach((id, method) -> {
            fields.add("    private final %s %s;".formatted(boundMenu, method));
            assignments.add("        this.%s = menu(%s.class, %s);".formatted(method, owner, Java.literal(id)));
            methods.add("""

                        /**
                         * Returns the menu {@code %3$s}.
                         *
                         * @return the menu
                         */
                        public %1$s %2$s() {
                            return %2$s;
                        }
                    """.formatted(boundMenu, method, id));
        });
        return """
                /** The menus of {@link %1$s}. */
                %2$s
                @%3$s
                public final class %4$s extends %5$s {

                %6$s

                    @%7$s
                    %4$s(%8$s ui) {
                        super(ui);
                %9$s
                    }
                %10$s}
                """.formatted(
                        owner,
                        scope.generated(),
                        scope.name(Names.VIEW_SCOPED),
                        simpleName,
                        scope.name(Names.STANDARD_MENUS),
                        String.join("\n", fields),
                        scope.name(Names.INJECT),
                        scope.name(Names.UI),
                        String.join("\n", assignments),
                        String.join("", methods));
    }

    /** The names of generated menus classes. */
    static final class Menus {
        private Menus() {}

        static String generatedName(TypeElement type) {
            return String.join("", Java.simpleNames(type)) + "Menus";
        }
    }
}
