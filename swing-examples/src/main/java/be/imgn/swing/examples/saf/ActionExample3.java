package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Window;

/** {@link ActionExample2}, where clearing the title is enabled only once a title was set. */
@Application(name = "ActionExample3")
public final class ActionExample3 extends ActionExample2 {

    @Exposed
    final Property<Boolean> clearEnabled = Property.of(false);

    // ActionExample2's content builds its buttons from these commands, which run this class's handlers.
    @Inject
    ActionExample3(Window window, ActionExample3Commands commands) {
        super(window, commands);
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(ActionExample3.class, args);
    }

    @Override
    @Command
    void updateTitle() {
        super.updateTitle();
        clearEnabled.set(true);
    }

    @Override
    @Command(enabled = "clearEnabled")
    void clearTitle() {
        super.clearTitle();
        clearEnabled.set(false);
    }
}
