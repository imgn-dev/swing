package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.SwingConstants;

/**
 * Secondary windows shown from the menu. Opening frames from code comes with multi-frame applications, in milestone 3,
 * so the port shows the three windows as dialog views owned by the main frame, each with a label and a button that
 * hides it.
 */
@Application(name = "SingleFrameExample6", vendor = "Sun", version = "1.0")
public final class SingleFrameExample6 implements SingleFrame {

    @Exposed
    final JLabel mainLabel = new JLabel("", SwingConstants.CENTER);

    private final SingleFrameExample6Commands commands;
    private final Ui ui;

    @Inject
    SingleFrameExample6(SingleFrameExample6Commands commands, Ui ui) {
        this.commands = commands;
        this.ui = ui;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(SingleFrameExample6.class, args);
    }

    @Override
    public JComponent content() {
        return mainLabel;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(ui.menus()
                .view()
                .with(
                        commands.showWindow0(),
                        commands.showWindow1(),
                        commands.showWindow2(),
                        MenuEntry.SEPARATOR,
                        commands.exit()));
    }

    @Command
    void showWindow0() {
        ui.showDialog(SecondaryWindow.Zero.class);
    }

    @Command
    void showWindow1() {
        ui.showDialog(SecondaryWindow.One.class);
    }

    @Command
    void showWindow2() {
        ui.showDialog(SecondaryWindow.Two.class);
    }
}
