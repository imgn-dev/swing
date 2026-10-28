package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** A label whose text, font, icon and colors all come from resources. */
@Application(name = "SingleFrameExample2")
// The framework reaches component fields and command targets from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class SingleFrameExample2 implements SingleFrame {

    final JLabel label = new JLabel();

    @Inject
    SingleFrameExample2() {}

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(SingleFrameExample2.class, args);
    }

    @Override
    public JComponent content() {
        label.setOpaque(true);
        return label;
    }
}
