package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.Locales;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.GridLayout;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A combo box of locales that sets the display locale, above a pane that follows it, then a combo box of the same
 * locales that changes only the pane below it.
 */
@Application
public final class LocaleExample4 implements SingleFrame, ExitVeto {

    private static final int GAP = 5;

    @Exposed
    final JComboBox<Locale> globalChoice;

    @Exposed
    final JComboBox<Locale> partChoice;

    @Exposed
    final LocalePane global;

    @Exposed
    final LocalePane part;

    private final JPanel buttons = new JPanel(new GridLayout(0, 1, GAP, GAP));
    private final Ui ui;
    private final Locales locales;
    private final LocalePaneMessages messages;

    @Inject
    LocaleExample4(Ui ui, StandardCommands commands, Locales locales, LocalePaneMessages messages) {
        this.ui = ui;
        this.locales = locales;
        this.messages = messages;
        Runnable exit = () -> commands.exit().execute();
        global = new LocalePane(exit);
        part = new LocalePane(exit);
        // The starting locale first, as the original's, then the three of the panes.
        Locale[] choices = {locales.current().get(), Locale.ENGLISH, Locale.FRENCH, LocaleExample3.SPANISH};
        globalChoice = new JComboBox<>(choices);
        partChoice = new JComboBox<>(choices);
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(LocaleExample4.class, args);
    }

    @Override
    public JComponent content() {
        ui.render(() -> global.show(messages));
        part.show(locales.messages(LocalePaneMessages.class, selected(partChoice)));
        globalChoice.addActionListener(event -> locales.set(selected(globalChoice)));
        partChoice.addActionListener(
                event -> part.show(locales.messages(LocalePaneMessages.class, selected(partChoice))));
        buttons.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        buttons.add(globalChoice);
        buttons.add(global);
        buttons.add(new JLabel(" "));
        buttons.add(partChoice);
        buttons.add(part);
        return buttons;
    }

    @Override
    public boolean canExit() {
        return ui.dialogs().confirm(messages.stop());
    }

    private static Locale selected(JComboBox<Locale> choice) {
        return choice.getItemAt(choice.getSelectedIndex());
    }
}
