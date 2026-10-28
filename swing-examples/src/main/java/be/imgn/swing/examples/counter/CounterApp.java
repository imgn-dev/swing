package be.imgn.swing.examples.counter;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.Messages;
import be.imgn.swing.Property;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.StandardMenus;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

/** A counter that shows "The count is: X.", asks for confirmation before exiting, and has a menu that exits. */
@Application(name = "Counter", vendor = "imgn", qualifier = "be", version = "1.0")
public final class CounterApp implements SingleFrame, ExitVeto {

    private static final int LOCATION = 100;

    private final Property<Integer> count = Property.of(0);
    private final CounterMessages messages;
    private final CounterAppCommands commands;
    private final StandardMenus menus;
    private final Ui ui;
    private final Dialogs dialogs;

    @Inject
    CounterApp(CounterMessages messages, CounterAppCommands commands, StandardMenus menus, Ui ui, Dialogs dialogs) {
        this.messages = messages;
        this.commands = commands;
        this.menus = menus;
        this.ui = ui;
        this.dialogs = dialogs;
    }

    /**
     * Launches the counter.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(CounterApp.class, args);
    }

    @Override
    public JComponent content() {
        JLabel label = new JLabel();
        ui.bind(label::setText, count.map(messages::count));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.CENTER);
        panel.add(commands.increment().createButton(), BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(menus.file().with(commands.exit()));
    }

    @Override
    public void configureFrame(JFrame frame) {
        frame.setLocation(LOCATION, LOCATION);
    }

    @Override
    public boolean canExit() {
        return dialogs.confirm(messages.confirmExit());
    }

    @Command
    void increment() {
        count.set(count.get() + 1);
    }

    @Messages
    interface CounterMessages {
        String count(int count);

        String confirmExit();
    }
}
