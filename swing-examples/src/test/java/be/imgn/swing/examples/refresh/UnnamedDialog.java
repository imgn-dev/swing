package be.imgn.swing.examples.refresh;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.DialogView;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** A dialog whose field has no accessible name, which the accessibility checks report. */
public final class UnnamedDialog implements DialogView<Void> {

    @Inject
    UnnamedDialog() {}

    @Override
    public JComponent content() {
        JPanel panel = new JPanel();
        panel.add(new JTextField(10));
        return panel;
    }

    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }
}
