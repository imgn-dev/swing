package be.imgn.swing.examples.added;

import be.imgn.gaia.core.event.Subscribe;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.View;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** A view with a command, a subscriber and a text field with no accessible name, whose build fails. */
public final class BrokenToolView implements View {

    private final BrokenToolViewCommands commands;
    private final ToolLog log;

    @Inject
    BrokenToolView(BrokenToolViewCommands commands, ToolLog log) {
        this.commands = commands;
        this.log = log;
    }

    @Override
    public JComponent content() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(commands.jump().createButton(), BorderLayout.NORTH);
        panel.add(new JTextField(10), BorderLayout.CENTER);
        return panel;
    }

    @Command
    void jump() {
        log.add("jump");
    }

    @Subscribe
    void ping(Ping event) {
        log.add("broken ping");
    }
}
