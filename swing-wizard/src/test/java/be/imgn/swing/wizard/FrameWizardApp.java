package be.imgn.swing.wizard;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** An application whose main frame view gives a wizard steps, which only a dialog view may. */
@Application
public final class FrameWizardApp implements SingleFrame {

    @Exposed
    final JPanel step = new JPanel();

    private final Wizard wizard;

    @Inject
    FrameWizardApp(Wizard wizard) {
        this.wizard = wizard;
    }

    @Override
    public JComponent content() {
        return wizard.steps(step);
    }
}
