package be.imgn.swing.processor;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.AnnotationReader;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessingException;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.source.ClassName;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/** Generates the {@code $X$$Application} descriptor of the {@code @Application} class, and its service entry. */
final class ApplicationStep implements Step {

    private final Shared shared;
    private final ProcessingContext context;

    ApplicationStep(Shared shared) {
        this.shared = shared;
        this.context = shared.context();
    }

    @Override
    public Set<String> annotations() {
        return Set.of(Names.APPLICATION);
    }

    @Override
    public Set<Element> process(AnnotatedElements elements) {
        context.forEach(elements.annotatedWith(Names.APPLICATION), this::process);
        return Set.of();
    }

    private void process(Element element) {
        if (element.getKind() != ElementKind.CLASS || element.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new ProcessingException("@Application must annotate a class that is not abstract", element);
        }
        TypeElement type = (TypeElement) element;
        AnnotationReader annotation =
                context.annotation(type, Names.APPLICATION).orElseThrow();
        String name = annotation.string("name");
        Optional<String> vendor = optional(annotation.string("vendor"));
        Optional<String> qualifier = optional(annotation.string("qualifier"));
        Optional<String> version = optional(annotation.string("version"));
        boolean valid = check(type, name, "name");
        valid &= vendor.map(value -> check(type, value, "vendor")).orElse(true);
        valid &= qualifier.map(value -> check(type, value, "qualifier")).orElse(true);
        if (qualifier.isPresent() && vendor.isEmpty()) {
            context.diagnostics()
                    .on(type)
                    .annotation(Names.APPLICATION)
                    .attribute("qualifier")
                    .error("A qualifier is only given with a vendor: add vendor, or remove qualifier");
            valid = false;
        }
        if (!context.isSubtype(type.asType(), Names.SINGLE_FRAME)) {
            context.diagnostics()
                    .on(type)
                    .annotation(Names.APPLICATION)
                    .error("The @Application class must implement " + Names.SINGLE_FRAME + " in milestone 1");
            valid = false;
        }
        shared.pending(type);
        if (valid) {
            generate(type, name, vendor, qualifier, version);
        }
    }

    private boolean check(TypeElement type, String value, String attribute) {
        try {
            ApplicationDirectories.checkName(value, attribute);
            return true;
        } catch (IllegalArgumentException e) {
            context.diagnostics()
                    .on(type)
                    .annotation(Names.APPLICATION)
                    .attribute(attribute)
                    .error(String.valueOf(e.getMessage()));
            return false;
        }
    }

    private static Optional<String> optional(String value) {
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private void generate(
            TypeElement type,
            String name,
            Optional<String> vendor,
            Optional<String> qualifier,
            Optional<String> version) {
        String simpleName = Java.dollarName(type, "Application");
        String qualifiedName = Java.qualified(type, simpleName);
        context.sources()
                .write(
                        qualifiedName,
                        scope -> """
                /** The descriptor of {@link %1$s}. */
                %2$s
                public final class %3$s implements %4$s {

                    /** Creates the descriptor; the service loader does. */
                    public %3$s() {}

                    @Override
                    public Class<?> type() {
                        return %1$s.class;
                    }

                    @Override
                    public String name() {
                        return %5$s;
                    }

                    @Override
                    public %6$s<String> vendor() {
                        return %7$s;
                    }

                    @Override
                    public %6$s<String> qualifier() {
                        return %8$s;
                    }

                    @Override
                    public %6$s<String> version() {
                        return %9$s;
                    }
                }
                """.formatted(
                                        scope.name(type),
                                        scope.generated(),
                                        simpleName,
                                        scope.name(Names.APPLICATION_DESCRIPTOR),
                                        Java.literal(name),
                                        scope.name(Optional.class),
                                        optionalLiteral(scope.name(Optional.class), vendor),
                                        optionalLiteral(scope.name(Optional.class), qualifier),
                                        optionalLiteral(scope.name(Optional.class), version)),
                        type);
        context.services()
                .add(
                        ClassName.bestGuess(Names.APPLICATION_DESCRIPTOR),
                        ClassName.of(Java.packageOf(type).getQualifiedName().toString(), simpleName),
                        type);
    }

    private static String optionalLiteral(String optional, Optional<String> value) {
        return value.map(text -> optional + ".of(" + Java.literal(text) + ")").orElse(optional + ".empty()");
    }
}
