package be.imgn.swing.examples.counter;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.StandardCommands;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * A dialog whose {@link CountWatcher} hears the counter's events while it is open. It announces its opening on the
 * application's bus as it builds its content, which its own watcher, built in the same build, doesn't hear.
 */
public final class CountDialog implements DialogView<Void> {

    private final CountWatcher watcher;
    private final EventBus bus;

    @Inject
    CountDialog(CountWatcher watcher, EventBus bus) {
        this.watcher = watcher;
        this.bus = bus;
    }

    @Override
    public JComponent content() {
        bus.publish(new Opened());
        return new JLabel("Watching the counts");
    }

    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return List.of(commands.cancel());
    }

    /**
     * Returns the watcher of this dialog.
     *
     * @return the watcher
     */
    CountWatcher watcher() {
        return watcher;
    }

    /** The opening of a count dialog. */
    record Opened() {}
}
