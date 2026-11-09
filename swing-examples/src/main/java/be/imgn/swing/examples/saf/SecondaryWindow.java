package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FrameView;
import be.imgn.swing.ui.Variants;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** A secondary window of {@link SingleFrameExample6}: a frame of its own, whose variants differ by their resources. */
public final class SecondaryWindow implements FrameView {

    @Exposed
    final JLabel label = new JLabel("", SwingConstants.CENTER);

    @Inject
    SecondaryWindow() {}

    @Override
    public JComponent content() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    /** The variants of the window. */
    @Variants
    public enum Name {
        /** Window Zero. */
        ZERO,
        /** Window One. */
        ONE,
        /** Window Two. */
        TWO
    }
}
