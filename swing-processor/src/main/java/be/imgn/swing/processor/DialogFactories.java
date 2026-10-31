package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.SourceScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

/**
 * Generates the {@code <Class>Factory} of a dialog view whose {@code @Inject} constructor has {@code @Assisted}
 * parameters: the {@code @AssistedFactory} interface that {@code Ui.showDialog} takes, which would otherwise be written
 * by hand for each such dialog.
 */
final class DialogFactories {

    private DialogFactories() {}

    static void generate(Shared shared, TypeElement type) {
        ProcessingContext context = shared.context();
        List<VariableElement> assisted = assistedParameters(context, type);
        if (assisted.isEmpty()) {
            return;
        }
        String simpleName = String.join("", Java.simpleNames(type)) + "Factory";
        String qualifiedName = Java.qualified(type, simpleName);
        Optional<String> problem = shared.claimCommandsName(qualifiedName)
                ? CommandsStep.existingClassProblem(context, type, simpleName)
                : Optional.of("Another class of the package also generates " + qualifiedName);
        if (problem.isPresent()) {
            shared.reporter().error(type, problem.get());
        } else {
            context.sources().write(qualifiedName, scope -> body(context, scope, type, simpleName, assisted), type);
        }
    }

    // The @Assisted parameters of the @Inject constructor of a dialog view; none for any other class.
    private static List<VariableElement> assistedParameters(ProcessingContext context, TypeElement type) {
        if (!context.isSubtype(type.asType(), Names.DIALOG_VIEW)) {
            return List.of();
        }
        return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .filter(constructor ->
                        context.annotation(constructor, Names.INJECT).isPresent())
                .findFirst()
                .stream()
                .flatMap(constructor -> constructor.getParameters().stream())
                .filter(parameter ->
                        context.annotation(parameter, Names.ASSISTED).isPresent())
                .map(VariableElement.class::cast)
                .toList();
    }

    private static String body(
            ProcessingContext context,
            SourceScope scope,
            TypeElement type,
            String simpleName,
            List<VariableElement> assisted) {
        List<String> parameters = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (VariableElement parameter : assisted) {
            String name = parameter.getSimpleName().toString();
            String value = context.annotation(parameter, Names.ASSISTED)
                    .map(annotation -> annotation.string("value"))
                    .orElse("");
            String annotation =
                    value.isEmpty() ? "" : "@" + scope.name(Names.ASSISTED) + "(" + Java.literal(value) + ") ";
            parameters.add(annotation + scope.name(parameter.asType()) + " " + name);
            tags.add("     * @param " + name + " the " + name + " the dialog shows");
        }
        return """
                /** Creates the {@link %1$s}s that {@code Ui.showDialog} shows. */
                %2$s
                public interface %3$s {

                    /**
                     * Returns a new dialog.
                     *
                %4$s
                     * @return the dialog
                     */
                    @%5$s
                    %1$s create(%6$s);
                }
                """.formatted(
                        scope.name(type),
                        scope.generated(),
                        simpleName,
                        String.join("\n", tags),
                        scope.name(Names.ASSISTED_FACTORY),
                        String.join(", ", parameters));
    }
}
