package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/** Hello World with its text, its font and the window's title, built with references, in resources. */
@Application(name = "ApplicationExample2", vendor = "Sun", version = "1.0")
// The framework reaches component fields and command targets from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class ApplicationExample2 implements SingleFrame {

    final JLabel label = new JLabel("", SwingConstants.CENTER);

    @Inject
    ApplicationExample2() {}

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(ApplicationExample2.class, args);
    }

    @Override
    public JComponent content() {
        return label;
    }
}
