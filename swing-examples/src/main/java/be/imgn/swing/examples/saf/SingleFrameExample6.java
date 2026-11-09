package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.SwingConstants;

/**
 * Secondary windows opened from the menu, each a frame of its own with a label, as in the original. Each command opens
 * a new frame of its variant; closing one leaves the application running.
 */
@Application(vendor = "Sun", version = "1.0")
public final class SingleFrameExample6 implements SingleFrame {

    @Exposed
    final JLabel mainLabel = new JLabel("", SwingConstants.CENTER);

    private final SingleFrameExample6Commands commands;
    private final Ui ui;
    private final Frames frames;

    @Inject
    SingleFrameExample6(SingleFrameExample6Commands commands, Ui ui, Frames frames) {
        this.commands = commands;
        this.ui = ui;
        this.frames = frames;
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
        frames.open(SecondaryWindow.class, SecondaryWindow.Name.ZERO);
    }

    @Command
    void showWindow1() {
        frames.open(SecondaryWindow.class, SecondaryWindow.Name.ONE);
    }

    @Command
    void showWindow2() {
        frames.open(SecondaryWindow.class, SecondaryWindow.Name.TWO);
    }
}
