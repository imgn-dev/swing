package be.imgn.swing.processor;

import be.imgn.gaia.core.text.NamingConvention;
import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.AnnotationReader;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessingException;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.source.ClassName;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;

/**
 * Checks the {@code @Settings} records, and generates the {@code $X$$Settings} descriptor of each, with its service
 * entry.
 */
final class SettingsStep implements Step {

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final ProcessingContext context;
    // The ids of the compilation, across its rounds, with the record that claimed each.
    private final Map<String, String> ids = new HashMap<>();

    SettingsStep(Shared shared) {
        this.context = shared.context();
    }

    @Override
    public Set<String> annotations() {
        return Set.of(Names.SETTINGS);
    }

    @Override
    public Set<Element> process(AnnotatedElements elements) {
        context.forEach(elements.annotatedWith(Names.SETTINGS), this::process);
        return Set.of();
    }

    private void process(Element element) {
        if (element.getKind() != ElementKind.RECORD) {
            throw new ProcessingException("@Settings must annotate a record", element);
        }
        TypeElement type = (TypeElement) element;
        String id = context.annotation(type, Names.SETTINGS).orElseThrow().string("value");
        boolean valid = true;
        if (context.annotation(type, Names.TOML_CONFIG).isEmpty()) {
            error(type, "A settings record must also be annotated @" + Names.TOML_CONFIG + ", which binds it to TOML");
            valid = false;
        }
        if (!hasPublicConstructorWithoutParameter(type)) {
            error(type, "A settings record must have a public constructor with no parameter, which gives the defaults");
            valid = false;
        }
        if (!ID.matcher(id).matches()) {
            context.diagnostics()
                    .on(type)
                    .annotation(Names.SETTINGS)
                    .attribute("value")
                    .error("The settings id " + Java.literal(id) + " is not lower-case kebab case: one or more groups"
                            + " of a-z and 0-9, separated by single -");
            valid = false;
        } else {
            String name = type.getQualifiedName().toString();
            String previous = ids.putIfAbsent(id, name);
            if (previous != null && !previous.equals(name)) {
                context.diagnostics()
                        .on(type)
                        .annotation(Names.SETTINGS)
                        .attribute("value")
                        .error("The settings records " + previous + " and " + name + " have the same id " + id);
                valid = false;
            }
        }
        if (valid) {
            generate(type, id, nullableKeys(type));
        }
    }

    private void error(TypeElement type, String message) {
        context.diagnostics().on(type).annotation(Names.SETTINGS).error(message);
    }

    private static boolean hasPublicConstructorWithoutParameter(TypeElement type) {
        return type.getEnclosedElements().stream()
                .filter(member -> member.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .anyMatch(constructor -> constructor.getParameters().isEmpty()
                        && constructor.getModifiers().contains(Modifier.PUBLIC));
    }

    // The TOML keys of the @Nullable components, derived as toml's processor derives them: the @TomlKey, else the
    // component's name in kebab case. Like toml's, any annotation named Nullable counts.
    private Set<String> nullableKeys(TypeElement type) {
        Set<String> keys = new LinkedHashSet<>();
        for (RecordComponentElement component : type.getRecordComponents()) {
            if (isNullable(component)) {
                keys.add(key(component));
            }
        }
        return keys;
    }

    private static boolean isNullable(RecordComponentElement component) {
        return Stream.concat(
                        component.getAnnotationMirrors().stream(), component.asType().getAnnotationMirrors().stream())
                .map(AnnotationMirror::getAnnotationType)
                .anyMatch(annotation -> annotation.asElement().getSimpleName().contentEquals("Nullable"));
    }

    private String key(RecordComponentElement component) {
        String name = component.getSimpleName().toString();
        return context.annotation(component, Names.TOML_KEY)
                .map((AnnotationReader key) -> key.string("value"))
                .orElseGet(() -> {
                    try {
                        return NamingConvention.LOWER_CAMEL.to(NamingConvention.LOWER_HYPHEN, name);
                    } catch (IllegalArgumentException e) {
                        throw new ProcessingException(
                                "The TOML key of " + name + " can't be derived: give it one with @" + Names.TOML_KEY,
                                component);
                    }
                });
    }

    private void generate(TypeElement type, String id, Set<String> nullable) {
        ClassName name = ClassName.of(type).generatedName("Settings");
        context.sources()
                .write(
                        name.canonicalName(),
                        scope -> """
                /** The descriptor of the settings record {@link %1$s}. */
                %2$s
                public final class %3$s implements %4$s<%1$s> {

                    /** Creates the descriptor; the service loader does. */
                    public %3$s() {}

                    @Override
                    public Class<%1$s> type() {
                        return %1$s.class;
                    }

                    @Override
                    public String id() {
                        return %5$s;
                    }

                    @Override
                    public %1$s defaults() {
                        return new %1$s();
                    }

                    @Override
                    public %6$s<String> nullable() {
                        return %6$s.of(%7$s);
                    }
                }
                """.formatted(
                                        scope.name(type),
                                        scope.generated(),
                                        name.simpleName(),
                                        scope.name(Names.SETTINGS_DESCRIPTOR),
                                        Java.literal(id),
                                        scope.name(Set.class),
                                        nullable.stream().map(Java::literal).collect(Collectors.joining(", "))),
                        type);
        context.services().add(ClassName.bestGuess(Names.SETTINGS_DESCRIPTOR), name, type);
    }
}
