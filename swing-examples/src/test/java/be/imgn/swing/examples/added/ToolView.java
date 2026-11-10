package be.imgn.swing.examples.added;

import be.imgn.gaia.core.event.Subscribe;
import be.imgn.inject.Inject;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.View;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** A view that tests add to the frame: a caption, a table whose columns are saved, a command, a subscriber, a veto. */
public final class ToolView implements View, ExitVeto {

    @Exposed
    final JLabel caption = new JLabel();

    @Exposed
    final JTable table = new JTable(new DefaultTableModel(new Object[] {"Name", "Size"}, 0));

    private final ToolLog log;
    private final int number;
    private final ToolViewCommands commands;

    @Inject
    ToolView(ToolLog log, ToolViewCommands commands) {
        this.log = log;
        this.number = log.created();
        this.commands = commands;
    }

    @Override
    public JComponent content() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(caption, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(commands.act().createButton(), BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public boolean canExit() {
        log.add("veto " + number);
        return log.closable();
    }

    @Command
    void act() {
        log.add("act " + number);
    }

    @Subscribe
    void ping(Ping event) {
        log.add("ping " + number);
    }
}
