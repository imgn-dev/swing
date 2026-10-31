import be.imgn.swing.processor.SwingProcessor;
import javax.annotation.processing.Processor;

// The processor module as its tests see it: the main descriptor, and the modules the tests use. The tests compile
// sources against swing's UI module, which brings core and inject along.
open module be.imgn.swing.processor {
    requires be.imgn.gaia.core;
    requires be.imgn.gaia.processing;
    requires be.imgn.gaia.processing.testing;
    requires be.imgn.swing.ui;
    requires be.imgn.toml;
    requires java.desktop;
    requires static org.jspecify;
    requires org.assertj.core;
    requires org.junit.jupiter.api;

    provides Processor with
            SwingProcessor;
}
