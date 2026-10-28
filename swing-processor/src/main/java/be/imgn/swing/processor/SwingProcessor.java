package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import java.util.List;

/** The annotation processor of swing. */
public final class SwingProcessor extends StepProcessor {

    /** Creates the processor; javac does, with the service loader. */
    public SwingProcessor() {}

    @Override
    protected List<Step> steps(ProcessingContext context) {
        return List.of();
    }
}
