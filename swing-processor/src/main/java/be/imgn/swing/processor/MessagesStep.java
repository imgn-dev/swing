package be.imgn.swing.processor;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessingException;
import be.imgn.gaia.processing.SourceScope;
import be.imgn.gaia.processing.Step;
import be.imgn.toml.TomlDocument;
import be.imgn.toml.TomlParseException;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

/** Generates the implementation of each {@code @Messages} interface, checked against every locale file. */
final class MessagesStep implements Step {

    private final Shared shared;
    private final ProcessingContext context;

    MessagesStep(Shared shared) {
        this.shared = shared;
        this.context = shared.context();
    }

    @Override
    public Set<String> annotations() {
        return Set.of(Names.MESSAGES);
    }

    @Override
    public Set<Element> process(AnnotatedElements elements) {
        context.forEach(elements.annotatedWith(Names.MESSAGES), this::process);
        return Set.of();
    }

    private void process(Element element) {
        if (element.getKind() != ElementKind.INTERFACE) {
            throw new ProcessingException("@Messages must annotate an interface", element);
        }
        TypeElement type = (TypeElement) element;
        if (type.getModifiers().contains(Modifier.PRIVATE)) {
            throw new ProcessingException("A @Messages interface must not be private", type);
        }
        Map<String, ExecutableElement> methods = methods(type);
        List<ResourceFile> files = shared.files()
                .of(
                        Java.packageOf(type).getQualifiedName().toString(),
                        type.getSimpleName().toString());
        Optional<ResourceFile> base =
                files.stream().filter(ResourceFile::isBase).findFirst();
        if (base.isEmpty()) {
            throw new ProcessingException(
                    "The messages " + type.getQualifiedName() + " have no file " + type.getSimpleName()
                            + ".toml next to them",
                    type);
        }
        boolean valid = true;
        for (ResourceFile file : files) {
            valid &= check(type, methods, file, base.get());
        }
        shared.resources(Java.packageOf(type), Set.of(), type);
        if (valid) {
            generate(type, methods);
        }
    }

    private Map<String, ExecutableElement> methods(TypeElement type) {
        Map<String, ExecutableElement> methods = new LinkedHashMap<>();
        for (ExecutableElement method :
                ElementFilter.methodsIn(context.elements().getAllMembers(type))) {
            if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }
            if (!context.isSubtype(method.getReturnType(), String.class)
                    || !method.getReturnType().toString().equals("java.lang.String")) {
                throw new ProcessingException("A message method returns String", method);
            }
            if (!method.getTypeParameters().isEmpty()) {
                throw new ProcessingException("A message method has no type parameter", method);
            }
            if (methods.put(method.getSimpleName().toString(), method) != null) {
                throw new ProcessingException("Two message methods are named " + method.getSimpleName(), method);
            }
        }
        return methods;
    }

    private boolean check(
            TypeElement type, Map<String, ExecutableElement> methods, ResourceFile file, ResourceFile base) {
        if (file.error() != null) {
            shared.reporter().error(type, file, file.error().position(), message(file.error()));
            return false;
        }
        TomlDocument document = file.parsed().orElseThrow();
        boolean valid = true;
        for (Map.Entry<String, TomlValue> entry : document.root().entries().entrySet()) {
            valid &= checkPattern(type, methods, file, document, entry.getKey(), entry.getValue());
        }
        if (file.isBase()) {
            valid &= checkBaseKeys(methods, file, document.root());
        } else if (file.isLanguage() && base.document() != null) {
            valid &= checkTranslation(methods, file, document.root(), base, base.document());
        }
        return valid;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private boolean checkPattern(
            TypeElement type,
            Map<String, ExecutableElement> methods,
            ResourceFile file,
            TomlDocument document,
            String key,
            TomlValue value) {
        Reporter reporter = shared.reporter();
        TomlTable root = document.root();
        ExecutableElement method = methods.get(key);
        if (method == null) {
            reporter.error(
                    type,
                    file,
                    document.positions().key(root, key),
                    "The key " + key + " names no method of " + type.getQualifiedName());
            return false;
        }
        if (!(value instanceof TomlString pattern)) {
            reporter.error(
                    method, file, document.positions().value(root, key), "The message " + key + " is not a string");
            return false;
        }
        Optional<String> problem = Patterns.problem(pattern.value(), parameters(method));
        problem.ifPresent(
                text -> reporter.error(method, file, document.positions().value(root, key), text));
        return problem.isEmpty();
    }

    private boolean checkBaseKeys(Map<String, ExecutableElement> methods, ResourceFile file, TomlTable root) {
        boolean valid = true;
        for (Map.Entry<String, ExecutableElement> method : methods.entrySet()) {
            if (!root.entries().containsKey(method.getKey())) {
                shared.reporter()
                        .error(
                                method.getValue(),
                                "The message " + method.getKey() + " has no pattern in " + file.name());
                valid = false;
            }
        }
        return valid;
    }

    private boolean checkTranslation(
            Map<String, ExecutableElement> methods,
            ResourceFile file,
            TomlTable root,
            ResourceFile base,
            TomlDocument baseDocument) {
        boolean valid = true;
        TomlTable baseRoot = baseDocument.root();
        for (String key : baseRoot.entries().keySet()) {
            ExecutableElement method = methods.get(key);
            if (!root.entries().containsKey(key) && method != null) {
                shared.reporter()
                        .error(
                                method,
                                base,
                                baseDocument.positions().key(baseRoot, key),
                                file.name() + " lacks the key " + key);
                valid = false;
            }
        }
        return valid;
    }

    static String message(TomlParseException error) {
        String message = String.valueOf(error.getMessage());
        int colon = message.indexOf(": ");
        return message.startsWith("line ") && colon >= 0 ? message.substring(colon + 2) : message;
    }

    private static List<String> parameters(ExecutableElement method) {
        return method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .toList();
    }

    private void generate(TypeElement type, Map<String, ExecutableElement> methods) {
        String simpleName = Java.dollarName(type, "Messages");
        context.sources()
                .write(Java.qualified(type, simpleName), scope -> body(scope, type, simpleName, methods), type);
    }

    private String body(
            SourceScope scope, TypeElement type, String simpleName, Map<String, ExecutableElement> methods) {
        String messages = scope.name(type);
        List<String> fields = new ArrayList<>();
        List<String> assignments = new ArrayList<>();
        List<String> implementations = new ArrayList<>();
        String list = scope.name(List.class);
        for (Map.Entry<String, ExecutableElement> entry : methods.entrySet()) {
            String name = entry.getKey();
            ExecutableElement method = entry.getValue();
            List<String> parameters = parameters(method);
            fields.add("    private final %s %s;".formatted(scope.name(Names.MESSAGE_PATTERN), name));
            assignments.add("        this.%1$s = %2$s.of(messages, %3$s, %4$s.of(%5$s), locale.locale());"
                    .formatted(
                            name,
                            scope.name(Names.MESSAGE_PATTERN),
                            Java.literal(name),
                            list,
                            parameters.stream().map(Java::literal).collect(Collectors.joining(", "))));
            List<String> declared = new ArrayList<>();
            for (VariableElement parameter : method.getParameters()) {
                declared.add(scope.name(parameter.asType()) + " " + parameter.getSimpleName());
            }
            implementations.add("""

                        @Override
                        public String %1$s(%2$s) {
                            return this.%1$s.format(%3$s);
                        }
                    """.formatted(name, String.join(", ", declared), String.join(", ", parameters)));
        }
        return """
                /** The messages {@link %1$s}, read from its TOML files in the display locale. */
                %2$s
                @%3$s
                @%4$s(%1$s.class)
                public final class %5$s implements %1$s {

                %6$s

                    @%7$s
                    %5$s(%8$s resources, %9$s locale) {
                        %10$s messages = resources.table(%1$s.class);
                %11$s
                    }
                %12$s}
                """.formatted(
                        messages,
                        scope.generated(),
                        scope.name(Names.SINGLETON),
                        scope.name(Names.EXPOSE_AS),
                        simpleName,
                        String.join("\n", fields),
                        scope.name(Names.INJECT),
                        scope.name(Names.RESOURCES),
                        scope.name(Names.DISPLAY_LOCALE),
                        scope.name("be.imgn.toml.TomlTable"),
                        String.join("\n", assignments),
                        String.join("", implementations));
    }
}
