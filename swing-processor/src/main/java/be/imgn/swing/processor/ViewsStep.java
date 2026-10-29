package be.imgn.swing.processor;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.SourceScope;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.source.ClassName;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * Generates the {@code $X$$Injector} and {@code $X$$State} of each view class, and checks the scope widening of every
 * class. Views carry no annotation, so the step gets every root element.
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
        if (type.getKind() != ElementKind.CLASS) {
            return;
        }
        checkScopes(type);
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
        shared.pending(type);
        generateInjector(type);
        generateState(type);
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
        String simpleName = Java.dollarName(type, "Injector");
        String qualifiedName = Java.qualified(type, simpleName);
        if (context.sources().write(qualifiedName, scope -> injector(scope, type, simpleName), type)) {
            context.services()
                    .add(
                            ClassName.bestGuess(Names.COMPONENT_INJECTOR),
                            ClassName.of(Java.packageOf(type).getQualifiedName().toString(), simpleName),
                            type);
        }
    }

    private String injector(SourceScope scope, TypeElement type, String simpleName) {
        String view = scope.name(type);
        String componentResources = scope.name(Names.COMPONENT_RESOURCES);
        List<String> properties = new ArrayList<>();
        List<String> tabTitles = new ArrayList<>();
        ViewModel.componentFields(context, type).forEach((name, field) -> {
            for (ComponentProperty property : ComponentProperty.values()) {
                if (property.appliesTo(context, field.asType())) {
                    String statement = property.statement(context, field.asType(), name, componentResources);
                    (property == ComponentProperty.TAB_TITLE ? tabTitles : properties).add(statement);
                }
            }
        });
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
                }
                """.formatted(
                        view,
                        scope.generated(),
                        simpleName,
                        scope.name(Names.COMPONENT_INJECTOR),
                        componentResources,
                        String.join("\n", properties),
                        String.join("\n", tabTitles),
                        scope.name(Names.COMMAND_REGISTRY),
                        String.join("\n", overrides));
    }

    // The standard commands that the nearest class of the hierarchy declaring commands overrides, with its generated
    // class.
    private List<String> overrides(SourceScope scope, TypeElement type) {
        List<String> overrides = new ArrayList<>();
        Commands.nearestDeclaring(context, type).ifPresent(declaring -> {
            String commands = Java.qualified(declaring, Commands.generatedName(declaring));
            for (CommandInfo command : Commands.all(context, declaring).values()) {
                if (command.isStandard()) {
                    overrides.add("        registry.override(%s, %s.class);"
                            .formatted(Java.literal(command.id()), scope.name(commands)));
                }
            }
        });
        return overrides;
    }

    private void generateState(TypeElement type) {
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (field.getModifiers().contains(Modifier.PRIVATE) && ViewModel.hasState(context, field)) {
                shared.reporter()
                        .warning(
                                field,
                                "The state of the private field " + field.getSimpleName()
                                        + " is not saved with the window: make it package-private to save it");
            }
        }
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        ViewModel.accessibleFields(type).forEach((name, field) -> {
            if (ViewModel.hasState(context, field)) {
                fields.put(name, field);
            }
        });
        String simpleName = Java.dollarName(type, "State");
        String qualifiedName = Java.qualified(type, simpleName);
        if (context.sources().write(qualifiedName, scope -> state(scope, type, simpleName, fields), type)) {
            context.services()
                    .add(
                            ClassName.bestGuess(Names.COMPONENT_STATE),
                            ClassName.of(Java.packageOf(type).getQualifiedName().toString(), simpleName),
                            type);
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
