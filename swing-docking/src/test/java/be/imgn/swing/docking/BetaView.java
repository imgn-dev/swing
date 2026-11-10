package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FormPanel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** A dock view with a labelled field. */
public final class BetaView implements DockView {

    @Exposed
    final JLabel label = new JLabel();

    @Exposed
    final JTextField field = new JTextField(10);

    @Inject
    BetaView() {}

    @Override
    public JComponent content() {
        return FormPanel.create().row(label, field).build();
    }
}
