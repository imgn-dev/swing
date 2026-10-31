import be.imgn.swing.processor.SwingProcessor;
import javax.annotation.processing.Processor;

/**
 * The annotation processor of swing.
 *
 * <p>It exports no package: javac finds the processor through the service it provides. It requires {@code java.desktop},
 * to parse key strokes, fonts and colors with the JDK's own parsers, and {@code be.imgn.gaia.core} itself, which
 * {@code gaia-processing} doesn't require transitively.
 */
module be.imgn.swing.processor {
    requires be.imgn.gaia.core;
    requires be.imgn.gaia.processing;
    requires be.imgn.toml;
    requires java.desktop;
    requires static org.jspecify;

    provides Processor with
            SwingProcessor;
}
