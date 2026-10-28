import be.imgn.swing.processor.SwingProcessor;
import javax.annotation.processing.Processor;

// The processor module as its tests see it: the main descriptor, and the modules the tests use.
open module be.imgn.swing.processor {
    requires be.imgn.gaia.processing;
    requires be.imgn.gaia.processing.testing;
    requires static org.jspecify;
    requires org.assertj.core;
    requires org.junit.jupiter.api;

    provides Processor with
            SwingProcessor;
}
