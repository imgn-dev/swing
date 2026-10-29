package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.inject.Provider;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Window;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** {@link ActionExample2}, where clearing the title is enabled only once a title was set. */
@Application(name = "ActionExample3")
public final class ActionExample3 extends ActionExample2 {

    @Exposed
    final Property<Boolean> clearEnabled = Property.of(false);

    private final ActionExample3Commands commands;

    @Inject
    ActionExample3(Window window, Provider<ActionExample2Commands> inherited, ActionExample3Commands commands) {
        super(window, inherited);
        this.commands = commands;
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
    public JComponent content() {
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(commands.clearTitle().createButton());
        buttons.add(commands.updateTitle().createButton());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(field, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.PAGE_END);
        return panel;
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
