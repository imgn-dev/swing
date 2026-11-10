package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import javax.swing.JComponent;
import javax.swing.JTextField;

/** A dock view with a text field that has no accessible name, whose build fails its checks once docked. */
public final class UnnamedView implements DockView {

    @Inject
    UnnamedView() {}

    @Override
    public JComponent content() {
        return new JTextField(10);
    }
}
