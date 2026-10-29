package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Hello World in a frame that the application builds itself. In swing the framework builds the main window, so the port
 * only gives its content, and the frame's title and centering are the framework's; the label's text and font, set in
 * code in the original, are in the resources.
 */
@Application(name = "ApplicationExample1")
// The framework reaches component fields from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class ApplicationExample1 implements SingleFrame {

    final JLabel label = new JLabel("", SwingConstants.CENTER);

    @Inject
    ApplicationExample1() {}

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(ApplicationExample1.class, args);
    }

    @Override
    public JComponent content() {
        return label;
    }
}
