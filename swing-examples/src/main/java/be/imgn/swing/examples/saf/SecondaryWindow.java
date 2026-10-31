package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Variants;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * A secondary window of {@link SingleFrameExample6}: a label, and a button that hides it. Its three variants differ
 * only by their title and their label's text, in the variants of its resources.
 */
public final class SecondaryWindow implements DialogView<Void> {

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

    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return List.of(commands.cancel());
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
