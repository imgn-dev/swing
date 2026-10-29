package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** The simplest single-frame application: a label that says Hello World. Its text and font come from resources. */
@Application(name = "SingleFrameExample1")
public final class SingleFrameExample1 implements SingleFrame {

    @Exposed
    final JLabel label = new JLabel();

    @Inject
    SingleFrameExample1() {}

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(SingleFrameExample1.class, args);
    }

    @Override
    public JComponent content() {
        return label;
    }
}
