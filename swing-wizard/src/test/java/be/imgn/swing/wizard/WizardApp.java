package be.imgn.swing.wizard;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** An application whose tests show wizard dialogs. */
@Application
public final class WizardApp implements SingleFrame {

    @Inject
    WizardApp() {}

    @Override
    public JComponent content() {
        return new JLabel("Wizards");
    }
}
