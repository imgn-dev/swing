package be.imgn.swing.examples.added;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.View;
import javax.swing.JComponent;

/** A view whose command takes copy's shortcut while it is open. */
public final class GrabbingView implements View {

    private final GrabbingViewCommands commands;

    @Inject
    GrabbingView(GrabbingViewCommands commands) {
        this.commands = commands;
    }

    @Override
    public JComponent content() {
        return commands.grab().createButton();
    }

    @Command
    void grab() {
        // Holds the shortcut only.
    }
}
