package be.imgn.swing.processor;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.SourceScope;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.source.ClassName;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Generates the {@code $X$$Injector} and {@code $X$$State} of each view class, and checks the scope widening and the
 * {@code @Exposed} fields of every class. Views carry no annotation, so the step gets every root element.
 */
final class ViewsStep implements Step {

    private final Shared shared;
    private final ProcessingContext context;

    ViewsStep(Shared shared) {
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
        List<TypeElement> types = new ArrayList<>();
        for (TypeElement root : ElementFilter.typesIn(elements.rootElements())) {
            collect(root, types);
        }
        context.forEach(types, this::processType);
        return Set.of();
    }

    private static void collect(TypeElement type, List<TypeElement> types) {
        types.add(type);
        ElementFilter.typesIn(type.getEnclosedElements()).forEach(nested -> collect(nested, types));
    }

    private void processType(TypeElement type) {
        checkVariants(type);
        if (type.getKind() != ElementKind.CLASS) {
            return;
        }
        checkScopes(type);
        checkExposed(type);
        if (!type.getModifiers().contains(Modifier.ABSTRACT) && context.isSubtype(type.asType(), Names.VIEW)) {
            processView(type);
        }
    }

    private void processView(TypeElement type) {
        if (type.getModifiers().contains(Modifier.PRIVATE)) {
            shared.reporter()
                    .error(type, "A view class must not be private: the framework's generated code reaches it");
            return;
        }
        if (isDocumentView(type) && !context.isSubtype(type.asType(), Names.FRAME_VIEW)) {
            shared.reporter()
                    .error(type, "A document view is shown in a frame of its own: implement " + Names.FRAME_VIEW);
        }
        shared.pending(type);
        generateInjector(type);
        generateState(type);
        DialogFactories.generate(shared, type);
    }

    // swing-desktop may be absent from the class path: a class that can't implement DocumentView isn't one.
    private boolean isDocumentView(TypeElement type) {
        return context.elements().getTypeElement(Names.DOCUMENT_VIEW) != null
                && context.isSubtype(type.asType(), Names.DOCUMENT_VIEW);
    }

    // A bean of a wider scope reaches a bean of a narrower one only through a Provider. The injection points include
    // the inherited ones, since a field or method of an unscoped superclass is injected into a bean of this scope.
    private void checkScopes(TypeElement type) {
        Optional<Scopes> scope = Scopes.of(context, type);
        if (scope.isEmpty()) {
            return;
        }
        for (Element member : context.elements().getAllMembers(type)) {
            boolean injected = context.annotation(member, Names.INJECT).isPresent();
            boolean executable = member.getKind() == ElementKind.CONSTRUCTOR || member.getKind() == ElementKind.METHOD;
            if (executable && injected) {
                for (VariableElement parameter : ((ExecutableElement) member).getParameters()) {
                    checkDependency(type, scope.get(), parameter);
                }
            } else if (member.getKind() == ElementKind.FIELD && injected) {
                checkDependency(type, scope.get(), member);
            }
        }
    }

    // @Variants marks the one enum, nested in a dialog view or a frame view, whose constants name the view's variants.
    private void checkVariants(TypeElement type) {
        if (context.annotation(type, Names.VARIANTS).isPresent()
                && (type.getKind() != ElementKind.ENUM
                        || !(type.getEnclosingElement() instanceof TypeElement owner)
                        || !(context.isSubtype(owner.asType(), Names.DIALOG_VIEW)
                                || context.isSubtype(owner.asType(), Names.FRAME_VIEW)))) {
            shared.reporter()
                    .error(
                            type,
                            "@Variants marks an enum nested in a dialog view or a frame view, whose constants name"
                                    + " its variants");
        }
        long count = ElementFilter.typesIn(type.getEnclosedElements()).stream()
                .filter(nested -> context.annotation(nested, Names.VARIANTS).isPresent())
                .count();
        if (count > 1) {
            shared.reporter().error(type, "A view has one @Variants enum, not " + count);
        }
    }

    // Generated code reads an @Exposed field from the class's package, as a component or as the target of a command, so
    // the annotation exempts no other field from Checkstyle's VisibilityModifier.
    private void checkExposed(TypeElement type) {
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (context.annotation(field, Names.EXPOSED).isEmpty()) {
                continue;
            }
            if (field.getModifiers().contains(Modifier.PRIVATE)
                    || field.getModifiers().contains(Modifier.STATIC)) {
                shared.reporter()
                        .error(
                                field,
                                "An @Exposed field must be neither private nor static: generated code in the same"
                                        + " package reads it from an instance");
            } else if (!context.isSubtype(field.asType(), Names.COMPONENT)
                    && !context.types()
                            .isAssignable(
                                    field.asType(), context.type(Names.OBSERVABLE, context.type(Boolean.class)))) {
                shared.reporter()
                        .error(
                                field,
                                "An @Exposed field must be a java.awt.Component or an Observable<Boolean>, not "
                                        + field.asType() + ": generated code reads no other field");
            }
        }
    }

    private void checkDependency(TypeElement type, Scopes scope, Element point) {
        TypeMirror dependency = point.asType();
        if (dependency.getKind() != TypeKind.DECLARED) {
            return;
        }
        TypeElement element = (TypeElement) ((DeclaredType) dependency).asElement();
        if (element.getQualifiedName().contentEquals(Names.PROVIDER)) {
            return;
        }
        Optional<Scopes> narrower = Scopes.of(context, element);
        if (narrower.isPresent() && narrower.get().isNarrowerThan(scope)) {
            shared.reporter()
                    .error(
                            point,
                            scope.label() + " " + type.getQualifiedName() + " depends directly on "
                                    + narrower.get().label() + " " + element.getQualifiedName()
                                    + ": a bean reaches a bean of a"
                                    + " narrower scope only through a Provider<" + element.getSimpleName() + ">");
        }
    }

    private void generateInjector(TypeElement type) {
        ClassName name = ClassName.of(type).generatedName("Injector");
        String simpleName = name.simpleName();
        String qualifiedName = name.canonicalName();
        if (context.sources().write(qualifiedName, scope -> injector(scope, type, simpleName), type)) {
            context.services().add(ClassName.bestGuess(Names.COMPONENT_INJECTOR), name, type);
        }
    }

    private String injector(SourceScope scope, TypeElement type, String simpleName) {
        String view = scope.name(type);
        String componentResources = scope.name(Names.COMPONENT_RESOURCES);
        List<String> properties = new ArrayList<>();
        List<String> tabTitles = new ArrayList<>();
        ViewModel.componentFields(context, type)
                .forEach((name, field) ->
                        statements(field, name, "view." + name, componentResources, properties, tabTitles));
        Map<String, VariableElement> nested = ViewModel.nestedFields(context, shared.files(), type);
        nested.forEach((path, field) ->
                statements(field, path, nestedTarget(path), componentResources, properties, tabTitles));
        List<String> overrides = overrides(scope, type);
        return """
                /** Writes the resources of {@link %1$s} to the components of its fields. */
                %2$s
                public final class %3$s implements %4$s<%1$s> {

                    /** Creates the injector; the service loader does. */
                    public %3$s() {}

                    @Override
                    public Class<%1$s> type() {
                        return %1$s.class;
                    }

                    @Override
                    public void inject(%1$s view, %5$s resources) {
                %6$s
                    }

                    @Override
                    public void injectTabTitles(%1$s view, %5$s resources) {
                %7$s
                    }

                    @Override
                    public void registerOverrides(%8$s registry) {
                %9$s
                    }
                %11$s%10$s}
                """.formatted(
                        view,
                        scope.generated(),
                        simpleName,
                        scope.name(Names.COMPONENT_INJECTOR),
                        componentResources,
                        String.join("\n", properties),
                        String.join("\n", tabTitles),
                        scope.name(Names.COMMAND_REGISTRY),
                        String.join("\n", overrides),
                        nested.isEmpty() ? "" : nestedMethod(type),
                        componentsMethod(scope, type));
    }

    // The statements that write every property that applies to a component field, read at its field path and reached
    // through an expression; tab-title goes apart, since it is applied once tabs exist.
    private void statements(
            VariableElement field,
            String path,
            String target,
            String componentResources,
            List<String> properties,
            List<String> tabTitles) {
        for (ComponentProperty property : ComponentProperty.values()) {
            if (property.appliesTo(context, field.asType())) {
                String statement = property.statement(context, field.asType(), path, target, componentResources);
                (property == ComponentProperty.TAB_TITLE ? tabTitles : properties).add(statement);
            }
        }
    }

    private String componentsMethod(SourceScope scope, TypeElement type) {
        List<String> components = new ArrayList<>();
        String packageName = Java.packageOf(type).getQualifiedName().toString();
        Set<String> path = new HashSet<>(Set.of(type.getQualifiedName().toString()));
        components(type.asType(), "", "view", List.of(), path, packageName, components);
        // The paths the resources name, which the cut of a type that holds itself may have left out.
        Map<String, VariableElement> fields = ViewModel.componentFields(context, type);
        ViewModel.nestedFields(context, shared.files(), type).keySet().forEach(nested -> {
            if (fields.containsKey(nested.split("\\.", -1)[0])
                    && components.stream().noneMatch(listed -> listed.contains("(" + Java.literal(nested) + ","))) {
                components.add(componentStatement(nested));
            }
        });
        return """

                    @Override
                    public void components(%s view, %s<String, %s> component) {
                %s
                    }
                """.formatted(
                        scope.name(type),
                        scope.name(BiConsumer.class),
                        scope.name(Names.COMPONENT),
                        String.join("\n", components));
    }

    // The statements that give each exposed component field, to any depth, read through its enclosing fields, each
    // checked for null on the way. A type already on the path is not followed again, so that a type that holds itself
    // ends.
    private void components(
            TypeMirror type,
            String prefix,
            String access,
            List<String> guards,
            Set<String> path,
            String packageName,
            List<String> statements) {
        if (type.getKind() != TypeKind.DECLARED) {
            return;
        }
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        ViewModel.componentFields(context, element).forEach((name, field) -> {
            if (!ViewModel.reachable(field, packageName)) {
                return;
            }
            String fieldPath = prefix.isEmpty() ? name : prefix + "." + name;
            String fieldAccess = access + "." + name;
            List<String> fieldGuards = new ArrayList<>(guards);
            fieldGuards.add(fieldAccess + " != null");
            statements.add(statement(fieldGuards, fieldPath, fieldAccess));
            String fieldType = context.types().erasure(field.asType()).toString();
            if (path.add(fieldType)) {
                components(field.asType(), fieldPath, fieldAccess, fieldGuards, path, packageName, statements);
                path.remove(fieldType);
            }
        });
    }

    private static String statement(List<String> guards, String path, String access) {
        return "        if (%s) {\n            component.accept(%s, %s);\n        }"
                .formatted(String.join(" && ", guards), Java.literal(path), access);
    }

    // The statement that gives a nested field the resources name, each enclosing field checked for null.
    private static String componentStatement(String path) {
        List<String> guards = new ArrayList<>();
        String access = "view";
        for (String name : path.split("\\.", -1)) {
            access = access + "." + name;
            guards.add(access + " != null");
        }
        return statement(guards, path, access);
    }

    // The expression that reads a nested component through its enclosing fields, each checked on the way, so that a
    // null field throws with its key path rather than a NullPointerException: home.phone.label reads
    // nested(nested(nested(view.home, "home").phone, "home.phone").label, "home.phone.label").
    private static String nestedTarget(String path) {
        String target = "view";
        String prefix = "";
        for (String name : path.split("\\.", -1)) {
            prefix = prefix.isEmpty() ? name : prefix + "." + name;
            target = "nested(" + target + "." + name + ", " + Java.literal(prefix) + ")";
        }
        return target;
    }

    private static String nestedMethod(TypeElement type) {
        return """

                    private static <C> C nested(C component, String path) {
                        if (component == null) {
                            throw new IllegalStateException("The field " + path + " of " + %s
                                    + " is null, but its resources [" + path + "] set its properties");
                        }
                        return component;
                    }
                """.formatted(Java.literal(type.getQualifiedName().toString()));
    }

    // The standard commands that the view class or its superclasses override, with the view class's generated class,
    // which every class with commands, declared or inherited, has.
    private List<String> overrides(SourceScope scope, TypeElement type) {
        List<String> overrides = new ArrayList<>();
        String commands = Java.qualified(type, Commands.generatedName(type));
        for (CommandInfo command : Commands.all(context, type).values()) {
            if (command.isStandard()) {
                overrides.add("        registry.override(%s, %s.class);"
                        .formatted(Java.literal(command.id()), scope.name(commands)));
            }
        }
        return overrides;
    }

    private void generateState(TypeElement type) {
        for (VariableElement field : ViewModel.hiddenFields(context, type)) {
            // A private field of the class itself is an error of checkExposed. An inherited field may come from a
            // class file, so the view carries the warning.
            if (ViewModel.hasState(context, field)
                    && field.getEnclosingElement() instanceof TypeElement owner
                    && !owner.equals(type)) {
                shared.reporter()
                        .warning(
                                type,
                                field.getSimpleName() + " of " + owner.getQualifiedName()
                                        + (field.getModifiers().contains(Modifier.PRIVATE)
                                                ? " is private"
                                                : " is not public")
                                        + ", so the state of " + type.getQualifiedName() + " can't save it");
            }
        }
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        ViewModel.accessibleFields(context, type).forEach((name, field) -> {
            if (ViewModel.hasState(context, field)) {
                fields.put(name, field);
            }
        });
        ClassName name = ClassName.of(type).generatedName("State");
        String simpleName = name.simpleName();
        String qualifiedName = name.canonicalName();
        if (context.sources().write(qualifiedName, scope -> state(scope, type, simpleName, fields), type)) {
            context.services().add(ClassName.bestGuess(Names.COMPONENT_STATE), name, type);
        }
    }

    private static String state(
            SourceScope scope, TypeElement type, String simpleName, Map<String, VariableElement> fields) {
        String view = scope.name(type);
        String component = scope.name("java.awt.Component");
        List<String> puts = new ArrayList<>();
        fields.keySet()
                .forEach(name -> puts.add(
                        "        if (view.%1$s != null) {\n            components.put(%2$s, view.%1$s);\n        }"
                                .formatted(name, Java.literal(name))));
        return """
                /** Gives the components of {@link %1$s} whose state is saved with its window. */
                %2$s
                public final class %3$s implements %4$s<%1$s> {

                    /** Creates the accessor; the service loader does. */
                    public %3$s() {}

                    @Override
                    public Class<%1$s> type() {
                        return %1$s.class;
                    }

                    @Override
                    public %5$s<String, %6$s> components(%1$s view) {
                        %5$s<String, %6$s> components = new %7$s<>();
                %8$s
                        return components;
                    }
                }
                """.formatted(
                        view,
                        scope.generated(),
                        simpleName,
                        scope.name(Names.COMPONENT_STATE),
                        scope.name(Map.class),
                        component,
                        scope.name(LinkedHashMap.class),
                        String.join("\n", puts));
    }
}
