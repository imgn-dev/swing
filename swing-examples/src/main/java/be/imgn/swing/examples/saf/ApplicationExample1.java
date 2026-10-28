package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Hello World in a frame that the application builds itself. In swing the framework builds the main window, so the port
 * only gives its content, and the frame's title and centering are the framework's.
 */
@Application(name = "ApplicationExample1")
public final class ApplicationExample1 implements SingleFrame {

    private static final int SIZE = 32;

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
        JLabel label = new JLabel("Hello World", SwingConstants.CENTER);
        label.setFont(new Font("LucidaSans", Font.PLAIN, SIZE));
        return label;
    }
}
