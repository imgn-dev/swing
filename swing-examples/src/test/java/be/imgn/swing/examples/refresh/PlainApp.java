package be.imgn.swing.examples.refresh;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** A single-frame application with no command, whose frame still has the palette's shortcut. */
@Application(vendor = "imgn", qualifier = "be")
public final class PlainApp implements SingleFrame {

    @Inject
    PlainApp() {}

    @Override
    public JComponent content() {
        return new JLabel("Plain");
    }
}
