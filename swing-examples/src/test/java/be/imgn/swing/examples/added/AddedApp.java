package be.imgn.swing.examples.added;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.StandardCommands;
import java.awt.GridLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A single-frame application whose tests open views in its frame while it is open, and in frames of its own. */
@Application
public final class AddedApp implements SingleFrame {

    @Exposed
    final JPanel area = new JPanel(new GridLayout(0, 1));

    private final StandardCommands standard;

    @Inject
    AddedApp(StandardCommands standard) {
        this.standard = standard;
    }

    @Override
    public JComponent content() {
        // Copy's shortcut, which a tool view's command may take while it is open.
        standard.copy();
        return area;
    }
}
