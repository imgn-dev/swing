package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.StandardCommands;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** A secondary window of {@link SingleFrameExample6}: a label, and a button that hides it. */
// The framework reaches component fields and command targets from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public abstract class SecondaryWindow implements DialogView<Void> {

    final JLabel label = new JLabel("", SwingConstants.CENTER);

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

    /** Window Zero. */
    public static final class Zero extends SecondaryWindow {
        @Inject
        Zero() {}
    }

    /** Window One. */
    public static final class One extends SecondaryWindow {
        @Inject
        One() {}
    }

    /** Window Two. */
    public static final class Two extends SecondaryWindow {
        @Inject
        Two() {}
    }
}
