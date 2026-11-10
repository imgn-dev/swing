package be.imgn.swing.examples.added;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.View;
import javax.swing.JComponent;
import javax.swing.JTextField;

/** A view whose content has a text field with no accessible name. */
public final class UnnamedView implements View {

    @Inject
    UnnamedView() {}

    @Override
    public JComponent content() {
        return new JTextField(10);
    }
}
