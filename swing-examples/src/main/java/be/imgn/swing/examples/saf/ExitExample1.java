package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/** Closing the window asks the exit veto, which asks for a confirmation. */
@Application(name = "ExitExample1")
public final class ExitExample1 implements SingleFrame, ExitVeto {

    private static final int MARGIN = 100;

    @Exposed
    final JLabel label = new JLabel("", SwingConstants.CENTER);

    private final Ui ui;
    private final ExampleMessages messages;

    @Inject
    ExitExample1(Ui ui, ExampleMessages messages) {
        this.ui = ui;
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(ExitExample1.class, args);
    }

    @Override
    public JComponent content() {
        label.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
        return label;
    }

    @Override
    public boolean canExit() {
        return ui.dialogs().confirm(messages.reallyExit());
    }
}
