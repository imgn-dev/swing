package be.imgn.swing.processor;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.SourceScope;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.source.ClassName;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/**
 * Checks the resources of the classes the other steps found, and generates {@code $$Resources}, the opener of each
 * package with resources, with its service entry, in the first round that adds no class to the package, so that a class
 * deferred to a later round still adds its icons. After the last round, it warns about the TOML files of the packages
 * it saw that belong to no class the processor generates for, which nothing would read.
 */
final class ResourcesStep implements Step {

    /** The simple name of the generated openers. */
    static final String OPENER = "$$Resources";

    private final Shared shared;
    private final ProcessingContext context;
    private final ResourceChecks checks;
    private final Map<String, PackageElement> packages = new LinkedHashMap<>();

    ResourcesStep(Shared shared) {
        this.shared = shared;
        this.context = shared.context();
        this.checks = new ResourceChecks(shared);
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
        for (Element root : elements.rootElements()) {
            if (root.getKind() != ElementKind.MODULE) {
                PackageElement packageElement = Java.packageOf(root);
                packages.putIfAbsent(packageElement.getQualifiedName().toString(), packageElement);
            }
        }
        context.forEach(shared.takePending(), checks::check);
        for (Shared.PackageEntry entry : shared.takeUnwrittenPackages()) {
            generate(entry);
        }
        return Set.of();
    }

    @Override
    public void processingOver() {
        packages.forEach(this::checkStrayFiles);
    }

    // A file named after a class the processor generates nothing for is read by nothing: say so rather than let it
    // vanish silently. A file that matches no class at all may be anything, and is left alone.
    private void checkStrayFiles(String packageName, PackageElement packageElement) {
        for (String baseName : shared.files().baseNames(packageName)) {
            Optional<TypeElement> type = typeNamed(packageElement, baseName);
            if (type.isPresent() && !generatesFor(type.get())) {
                Element element = context.isFromSource(type.get()) ? type.get() : packageElement;
                shared.reporter().warning(element, baseName + ".toml belongs to no class the processor generates for");
            }
        }
    }

    // The class of a package, top-level or nested, with a simple name: a nested class's files are named after it.
    private static Optional<TypeElement> typeNamed(PackageElement packageElement, String simpleName) {
        Deque<TypeElement> queue = new ArrayDeque<>(ElementFilter.typesIn(packageElement.getEnclosedElements()));
        while (!queue.isEmpty()) {
            TypeElement type = queue.removeFirst();
            if (type.getSimpleName().contentEquals(simpleName)) {
                return Optional.of(type);
            }
            queue.addAll(ElementFilter.typesIn(type.getEnclosedElements()));
        }
        return Optional.empty();
    }

    // The classes the steps generate for, and so read the files of: the application class, the classes with commands,
    // the views, abstract ones included since their files merge into their subclasses', and the messages interfaces.
    private boolean generatesFor(TypeElement type) {
        return context.annotation(type, Names.APPLICATION).isPresent()
                || context.annotation(type, Names.MESSAGES).isPresent()
                || context.isSubtype(type.asType(), Names.VIEW)
                || !Commands.all(context, type).isEmpty();
    }

    private void generate(Shared.PackageEntry entry) {
        String qualifiedName = entry.name().isEmpty() ? OPENER : entry.name() + "." + OPENER;
        Element[] origins = entry.origins().toArray(TypeElement[]::new);
        if (context.sources().write(qualifiedName, scope -> body(scope, entry), origins)) {
            context.services()
                    .add(ClassName.bestGuess(Names.RESOURCE_OPENER), ClassName.of(entry.name(), OPENER), origins);
        }
    }

    private static String body(SourceScope scope, Shared.PackageEntry entry) {
        String icons = entry.icons().stream().map(Java::literal).collect(Collectors.joining(", "));
        return """
                /** Opens the resources of the package {@code %1$s}, from its own module. */
                %2$s
                public final class %3$s implements %4$s {

                    /** Creates the opener; the service loader does. */
                    public %3$s() {}

                    @Override
                    public String packageName() {
                        return %5$s;
                    }

                    @Override
                    public %6$s<%7$s> open(String fileName) {
                        return %6$s.ofNullable(%3$s.class.getResourceAsStream(fileName));
                    }

                    @Override
                    public %8$s<String> icons() {
                        return %8$s.of(%9$s);
                    }
                }
                """.formatted(
                        entry.name(),
                        scope.generated(),
                        OPENER,
                        scope.name(Names.RESOURCE_OPENER),
                        Java.literal(entry.name()),
                        scope.name(Optional.class),
                        scope.name(InputStream.class),
                        scope.name(Set.class),
                        icons);
    }
}
