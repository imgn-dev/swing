package be.imgn.swing.examples.refresh;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.ui.Column;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.CommandContext;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Trigger;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

/**
 * A single-frame application whose resources and messages, in English and French, reach every part that a refresh gives
 * again: a label, a tab, the title, a command, a menu, a column header and a rendered text.
 */
@Application(name = "Refresh", vendor = "imgn", qualifier = "be")
public final class RefreshApp implements SingleFrame {

    @Exposed
    final JLabel label = new JLabel();

    @Exposed
    final JLabel status = new JLabel();

    @Exposed
    final JTabbedPane tabs = new JTabbedPane();

    @Exposed
    final JPanel first = new JPanel();

    // Not a tab when the view is built: its tab-title is skipped then.
    @Exposed
    final JPanel later = new JPanel();

    @Exposed
    final JTable table = new JTable();

    private final Property<Integer> count = Property.of(1);
    private Trigger lastTrigger = new Trigger.Programmatic();

    private final Ui ui;
    private final RefreshAppCommands commands;
    private final RefreshAppMenus menus;
    private final RefreshMessages messages;

    @Inject
    RefreshApp(Ui ui, RefreshAppCommands commands, RefreshAppMenus menus, RefreshMessages messages) {
        this.ui = ui;
        this.commands = commands;
        this.menus = menus;
        this.messages = messages;
    }

    @Override
    public JComponent content() {
        ui.render(() -> status.setText(messages.count(count.get())), count);
        tabs.add(first);
        ui.bind(
                table,
                ObservableList.of("Ann", "Bob"),
                List.of(
                        Column.of(messages::nameHeader, String.class, name -> name),
                        Column.of("Fixed", Integer.class, String::length)));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        panel.add(table, BorderLayout.EAST);
        panel.add(status, BorderLayout.SOUTH);
        panel.add(commands.greet().createButton(), BorderLayout.WEST);
        return panel;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(menus.things().with(commands.greet(), commands.copy(), commands.clear()));
    }

    @Command
    void greet(CommandContext context) {
        lastTrigger = context.trigger();
        count.set(count.get() + 1);
    }

    // How greet was activated last, for the tests of the palette.
    Trigger lastTrigger() {
        return lastTrigger;
    }

    @Command
    void clear() {
        count.set(0);
    }
}
