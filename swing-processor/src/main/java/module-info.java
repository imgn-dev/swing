import be.imgn.swing.processor.SwingProcessor;
import javax.annotation.processing.Processor;

/**
 * The annotation processor of swing.
 *
 * <p>It exports no package: javac finds the processor through the service it provides.
 */
module be.imgn.swing.processor {
    requires be.imgn.gaia.processing;
    requires static org.jspecify;

    provides Processor with
            SwingProcessor;
}
