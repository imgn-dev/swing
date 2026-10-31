package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessorOption;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.source.ClassName;
import java.util.List;
import java.util.Set;

/**
 * The annotation processor of swing. Its steps generate the application descriptor, the commands, the messages, the
 * injectors and state accessors of views, the menus and the resource openers, and check the TOML resources.
 *
 * <p>The option {@code -Abe.imgn.swing.resources=<dir>} adds a directory to look for resources in, for IDEs that
 * compile without copying them to the class output.
 */
public final class SwingProcessor extends StepProcessor {

    /** Creates the processor; javac does, with the service loader. */
    public SwingProcessor() {}

    @Override
    protected List<Step> steps(ProcessingContext context) {
        // gaia's register-services goal adds the provides directives to the compiled module descriptor.
        for (String service : List.of(
                Names.APPLICATION_DESCRIPTOR, Names.RESOURCE_OPENER, Names.COMPONENT_INJECTOR, Names.COMPONENT_STATE)) {
            context.services().skipModuleCheck(ClassName.bestGuess(service));
        }
        Shared shared = new Shared(context);
        return List.of(
                new ApplicationStep(shared),
                new CommandsStep(shared),
                new MessagesStep(shared),
                new ViewsStep(shared),
                new MenusStep(shared),
                new ResourcesStep(shared));
    }

    @Override
    protected Set<ProcessorOption<?>> options() {
        return Set.of(Shared.RESOURCES);
    }
}
