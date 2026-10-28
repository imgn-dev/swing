package be.imgn.swing.processor;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.SourceScope;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.source.ClassName;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Checks the resources of the classes the other steps found, and generates {@code $$Resources}, the opener of each
 * package with resources, with its service entry.
 */
final class ResourcesStep implements Step {

    /** The simple name of the generated openers. */
    static final String OPENER = "$$Resources";

    private final Shared shared;
    private final ProcessingContext context;
    private final ResourceChecks checks;

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
        context.forEach(shared.takePending(), checks::check);
        for (Shared.PackageEntry entry : shared.takeUnwrittenPackages()) {
            generate(entry);
        }
        return Set.of();
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
