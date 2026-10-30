package be.imgn.swing.processor;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.SourceScope;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.source.ClassName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Generates the {@code <Class>Commands} of each class that declares or inherits {@code @Command} methods, which extends
 * the generated class of its superclass, so that a superclass's code receives the commands of its subclass.
 */
final class CommandsStep implements Step {

    private final Shared shared;
    private final ProcessingContext context;
    // A class reaches the step through its annotations and as a root element, possibly in several rounds.
    private final Set<String> processed = new HashSet<>();

    CommandsStep(Shared shared) {
        this.shared = shared;
        this.context = shared.context();
    }

    @Override
    public Set<String> annotations() {
        return Set.of(Names.COMMAND);
    }

    @Override
    public boolean processesRootElements() {
        return true;
    }

    // A class that only inherits commands names its generated class in its constructor, which only this step writes.
    @Override
    public boolean processesUnresolvedRootElements() {
        return true;
    }

    @Override
    public Set<Element> process(AnnotatedElements elements) {
        Set<TypeElement> classes = new LinkedHashSet<>();
        for (Element element : elements.annotatedWith(Names.COMMAND)) {
            if (element.getEnclosingElement() instanceof TypeElement type) {
                classes.add(type);
            }
        }
        // A class that only inherits commands gets its own generated class too, whose commands act on it.
        for (TypeElement root : ElementFilter.typesIn(elements.rootElements())) {
            collectInheriting(root, classes);
        }
        classes.removeIf(type -> processed.contains(type.getQualifiedName().toString()));
        context.forEach(classes, this::processClass);
        return Set.of();
    }

    private void collectInheriting(TypeElement type, Set<TypeElement> classes) {
        if (type.getKind() == ElementKind.CLASS && !Commands.all(context, type).isEmpty()) {
            classes.add(type);
        }
        ElementFilter.typesIn(type.getEnclosedElements()).forEach(nested -> collectInheriting(nested, classes));
    }

    private void processClass(TypeElement type) {
        shared.pending(type);
        boolean valid = checkClass(type);
        Map<String, CommandInfo> commands = Commands.all(context, type);
        for (CommandInfo command : commands.values()) {
            valid &= check(type, command);
        }
        valid &= checkNames(type, commands);
        if (valid) {
            generate(type, commands);
        }
        processed.add(type.getQualifiedName().toString());
    }

    private boolean checkClass(TypeElement type) {
        Optional<String> problem = classProblem(type);
        problem.ifPresent(message -> shared.reporter().error(type, message));
        return problem.isEmpty();
    }

    private Optional<String> classProblem(TypeElement type) {
        if (type.getKind() != ElementKind.CLASS || type.getModifiers().contains(Modifier.PRIVATE)) {
            return Optional.of("Commands are declared in classes that are not private, not in a " + type.getKind()
                    + (type.getModifiers().contains(Modifier.PRIVATE) ? " that is private" : ""));
        }
        if (Scopes.of(context, type).isEmpty()) {
            return Optional.of(type.getQualifiedName() + " declares commands but has no scope: annotate it with"
                    + " @Singleton, @WindowScoped or @ViewScoped, or implement a view, so that its commands run on the"
                    + " instance that received them");
        }
        String simpleName = Commands.generatedName(type);
        String name = Java.qualified(type, simpleName);
        if (!shared.claimCommandsName(name)) {
            return Optional.of("Another class of the package also generates " + name);
        }
        return existingClassProblem(context, type, simpleName);
    }

    /**
     * Returns why a generated class cannot be written, if a class of that name already exists: one of an earlier
     * compilation on the class path, which the per-compilation claim of the name does not see, or one written by hand.
     */
    static Optional<String> existingClassProblem(ProcessingContext context, TypeElement type, String simpleName) {
        String packageName = Java.packageOf(type).getQualifiedName().toString();
        return context.sources().isAvailable(ClassName.of(packageName, simpleName))
                ? Optional.empty()
                : Optional.of("The class " + Java.qualified(type, simpleName) + " that " + type.getQualifiedName()
                        + " generates already exists, from an earlier compilation or written by hand");
    }

    private boolean check(TypeElement type, CommandInfo command) {
        boolean own = command.declaring().equals(type);
        Element where = own ? command.handler() : type;
        List<String> problems = new ArrayList<>(handlerProblems(type, command));
        if (!command.enabled().isEmpty()) {
            targetProblem(type, command, command.enabled(), Names.OBSERVABLE, "enabled")
                    .ifPresent(problems::add);
        }
        if (!command.selected().isEmpty()) {
            targetProblem(type, command, command.selected(), Names.PROPERTY, "selected")
                    .ifPresent(problems::add);
        }
        if (own && command.isStandard()) {
            String overrides = "Command `" + command.id() + "` of `" + type.getQualifiedName()
                    + "` overrides the standard command `" + command.id() + "`";
            // An override applies in the views whose class, or a superclass of it, declares it: one declared in a
            // helper that is not a view, nor abstract and so possibly extended by one, applies nowhere.
            boolean view = context.isSubtype(type.asType(), Names.VIEW)
                    || type.getModifiers().contains(Modifier.ABSTRACT);
            shared.reporter()
                    .warning(
                            command.handler(),
                            view
                                    ? overrides
                                            + ". Overriding standard commands may become an error in a future"
                                            + " version."
                                    : overrides + ", but `" + type.getQualifiedName()
                                            + "` is not a view: the override applies nowhere");
        }
        problems.forEach(problem -> shared.reporter().error(where, problem));
        return problems.isEmpty();
    }

    private List<String> handlerProblems(TypeElement type, CommandInfo command) {
        ExecutableElement handler = command.handler();
        List<String> problems = new ArrayList<>();
        Set<Modifier> modifiers = handler.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
            problems.add("The handler of a command must be neither private nor static");
        }
        if (!command.declaring().equals(type)
                && !samePackage(type, command.declaring())
                && !modifiers.contains(Modifier.PUBLIC)) {
            problems.add("The inherited handler " + handler.getSimpleName() + " of "
                    + command.declaring().getQualifiedName() + " is in another package, so it must be public");
        }
        problems.addAll(signatureProblems(command));
        return problems;
    }

    private static List<String> signatureProblems(CommandInfo command) {
        ExecutableElement handler = command.handler();
        List<String> problems = new ArrayList<>();
        if (handler.getReturnType().getKind() != TypeKind.VOID && !command.returnsTask()) {
            problems.add("The handler of a command returns void or Task<?>");
        }
        if (!handler.getParameters().isEmpty() && !command.takesContext()) {
            problems.add("The handler of a command takes no parameter, or a single CommandContext");
        }
        if (!"NONE".equals(command.block()) && !command.returnsTask()) {
            problems.add("block = Block." + command.block() + " applies only to a handler that returns a Task");
        }
        String name = command.handlerName();
        if (Names.STANDARD_COMMANDS_IDS.contains(name) && !name.equals(command.id())) {
            problems.add("The handler " + name + " is named like the standard command " + name + " but its id is "
                    + command.id() + ": its generated method would override the standard method with an unrelated"
                    + " command");
        }
        return problems;
    }

    private boolean checkNames(TypeElement type, Map<String, CommandInfo> commands) {
        Map<String, CommandInfo> byHandler = new HashMap<>();
        boolean valid = true;
        for (CommandInfo command : commands.values()) {
            CommandInfo previous = byHandler.put(command.handlerName(), command);
            if (previous != null) {
                shared.reporter()
                        .error(
                                command.declaring().equals(type) ? command.handler() : type,
                                "The commands " + previous.id() + " and " + command.id()
                                        + " have handlers of the same name " + command.handlerName());
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Returns why a target does not name an {@code @Exposed} field or a non-private no-argument method of the right
     * type, if it does not.
     */
    private Optional<String> targetProblem(
            TypeElement type, CommandInfo command, String name, String expected, String attribute) {
        Optional<Element> target = target(command.declaring(), name);
        String described = attribute + " = \"" + name + "\"";
        if (target.isEmpty()) {
            return Optional.of(described + " names no field or no-argument method of "
                    + command.declaring().getQualifiedName());
        }
        Element element = target.get();
        Optional<String> access = accessProblem(type, command, element, described);
        if (access.isPresent()) {
            return access;
        }
        TypeMirror actual = element instanceof ExecutableElement method ? method.getReturnType() : element.asType();
        TypeMirror wanted = context.type(expected, context.type(Boolean.class));
        return context.types().isAssignable(actual, wanted)
                ? Optional.empty()
                : Optional.of(described + " must be of type " + simple(expected) + "<Boolean>, not " + actual);
    }

    // Returns why the generated class, in the package of type, can't read the target, if it can't.
    private Optional<String> accessProblem(TypeElement type, CommandInfo command, Element element, String described) {
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            return Optional.of(
                    described + " names a private member: the generated class in the same package must" + " reach it");
        }
        if (element instanceof VariableElement
                && context.annotation(element, Names.EXPOSED).isEmpty()) {
            return Optional.of(
                    described + " names a field that is not @Exposed: generated code reads only @Exposed fields");
        }
        if (!command.declaring().equals(type)
                && !samePackage(type, command.declaring())
                && !element.getModifiers().contains(Modifier.PUBLIC)) {
            return Optional.of(described + " of " + command.declaring().getQualifiedName()
                    + " is in another package, so it must be public");
        }
        return Optional.empty();
    }

    private static String simple(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private static Optional<Element> target(TypeElement type, String name) {
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (field.getSimpleName().contentEquals(name)
                    && !field.getModifiers().contains(Modifier.STATIC)) {
                return Optional.of(field);
            }
        }
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (method.getSimpleName().contentEquals(name)
                    && method.getParameters().isEmpty()
                    && !method.getModifiers().contains(Modifier.STATIC)) {
                return Optional.of(method);
            }
        }
        return Commands.superclass(type).flatMap(superclass -> target(superclass, name));
    }

    private static boolean samePackage(TypeElement a, TypeElement b) {
        return Java.packageOf(a).equals(Java.packageOf(b));
    }

    private void generate(TypeElement type, Map<String, CommandInfo> commands) {
        String simpleName = Commands.generatedName(type);
        String qualifiedName = Java.qualified(type, simpleName);
        context.sources().write(qualifiedName, scope -> body(scope, type, simpleName, commands), type);
    }

    private String body(SourceScope scope, TypeElement type, String simpleName, Map<String, CommandInfo> commands) {
        String owner = scope.name(type);
        Optional<TypeElement> parent = Commands.superclass(type)
                .filter(superclass -> !Commands.all(context, superclass).isEmpty());
        String extended = parent.map(
                        superclass -> scope.name(Java.qualified(superclass, Commands.generatedName(superclass))))
                .orElse(scope.name(Names.STANDARD_COMMANDS));
        Set<String> inherited = new HashSet<>();
        parent.ifPresent(
                superclass -> Commands.all(context, superclass).values().forEach(command -> {
                    inherited.add(command.handlerName());
                    inherited.add(command.id());
                }));
        boolean isAbstract = type.getModifiers().contains(Modifier.ABSTRACT);
        Parts parts = new Parts(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (CommandInfo command : commands.values()) {
            String field = command.handlerName();
            parts.fields().add("    private final %s %s;".formatted(scope.name(Names.BOUND_COMMAND), field));
            parts.bindings().add(binding(scope, owner, command));
            parts.unbound().add("        this.%s = null;".formatted(field));
            boolean overrides = inherited.contains(field) || Names.STANDARD_COMMANDS_IDS.contains(field);
            parts.methods().add(method(scope, field, field, overrides, isAbstract));
            if (command.isStandard() && !field.equals(command.id())) {
                parts.methods().add(method(scope, command.id(), field, true, isAbstract));
            }
        }
        return isAbstract
                ? abstractBody(scope, owner, simpleName, extended, parts)
                : concreteBody(scope, type, simpleName, extended, parts);
    }

    // An abstract class has no instance to run commands on: its generated class only declares them, for the generated
    // classes of its subclasses.
    private static String abstractBody(
            SourceScope scope, String owner, String simpleName, String extended, Parts parts) {
        return """
                /** The commands of {@link %1$s}, which the generated classes of its subclasses implement. */
                %2$s
                public abstract class %3$s extends %4$s {

                    /**
                     * Creates the commands of a subclass, whose generated class binds them.
                     *
                     * @param registry the commands of the view
                     */
                    protected %3$s(%5$s registry) {
                        super(registry);
                    }
                %6$s}
                """.formatted(
                        owner,
                        scope.generated(),
                        simpleName,
                        extended,
                        scope.name(Names.COMMAND_REGISTRY),
                        String.join("", parts.methods()));
    }

    // A class that may be extended also gets a constructor that binds nothing, which the generated class of a subclass
    // calls: that one binds every command, its inherited ones included, on the subclass's instance.
    private static String concreteBody(
            SourceScope scope, TypeElement type, String simpleName, String extended, Parts parts) {
        boolean isFinal = type.getModifiers().contains(Modifier.FINAL);
        String registry = scope.name(Names.COMMAND_REGISTRY);
        String unbound = String.join("\n", parts.unbound());
        String forSubclasses = isFinal ? "" : """

                            /**
                             * Creates the commands of a subclass, whose generated class binds them.
                             *
                             * @param registry the commands of the view
                             */
                            protected %1$s(%2$s registry) {
                                super(registry);
                        %3$s
                            }
                        """.formatted(simpleName, registry, unbound);
        return """
                /** The commands of {@link %1$s}. */
                %2$s
                @%3$s
                public %4$sclass %5$s extends %6$s {

                %7$s

                    @%8$s
                    %5$s(%9$s registry, %10$s<%1$s> owner) {
                        super(registry);
                %11$s
                    }
                %12$s%13$s}
                """.formatted(
                        scope.name(type),
                        scope.generated(),
                        scope.name(Names.VIEW_SCOPED),
                        isFinal ? "final " : "",
                        simpleName,
                        extended,
                        String.join("\n", parts.fields()),
                        scope.name(Names.INJECT),
                        scope.name(Names.COMMAND_REGISTRY),
                        scope.name(Names.PROVIDER),
                        String.join("\n", parts.bindings()),
                        forSubclasses,
                        String.join("", parts.methods()));
    }

    private String binding(SourceScope scope, String owner, CommandInfo command) {
        StringBuilder binding = new StringBuilder();
        String call = "owner.get()." + command.handlerName() + "(" + (command.takesContext() ? "context" : "") + ")";
        binding.append("        this.")
                .append(command.handlerName())
                .append(" = registry.command(")
                .append(owner)
                .append(".class, ")
                .append(Java.literal(command.id()))
                .append(")");
        binding.append("\n                .")
                .append(command.returnsTask() ? "taskHandler" : "handler")
                .append("(context -> ")
                .append(call)
                .append(")");
        if (!command.enabled().isEmpty()) {
            binding.append("\n                .enabled(() -> owner.get().")
                    .append(access(command, command.enabled()))
                    .append(")");
        }
        if (!command.selected().isEmpty()) {
            binding.append("\n                .selected(() -> owner.get().")
                    .append(access(command, command.selected()))
                    .append(")");
        }
        if (!"NONE".equals(command.block())) {
            binding.append("\n                .block(")
                    .append(scope.name(Names.BLOCK))
                    .append(".")
                    .append(command.block())
                    .append(")");
        }
        return binding.append("\n                .bind();").toString();
    }

    private static String access(CommandInfo command, String name) {
        return target(command.declaring(), name).orElseThrow() instanceof ExecutableElement ? name + "()" : name;
    }

    private static String method(SourceScope scope, String name, String field, boolean override, boolean isAbstract) {
        String annotation = override ? "@Override\n    " : "";
        if (isAbstract) {
            return """

                        /**
                         * Returns the command %3$s.
                         *
                         * @return the command
                         */
                        %4$spublic abstract %1$s %2$s();
                    """.formatted(scope.name(Names.BOUND_COMMAND), name, field, annotation);
        }
        return """

                    /**
                     * Returns the command %3$s.
                     *
                     * @return the command
                     */
                    %4$spublic %1$s %2$s() {
                        return %3$s;
                    }
                """.formatted(scope.name(Names.BOUND_COMMAND), name, field, annotation);
    }

    // The pieces of a generated class, one per command.
    private record Parts(List<String> fields, List<String> bindings, List<String> unbound, List<String> methods) {}
}
