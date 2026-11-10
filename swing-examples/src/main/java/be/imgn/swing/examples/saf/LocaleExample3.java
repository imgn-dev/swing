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
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Three panes, each in its own locale, English, French and Spanish, whatever the display locale: each button exits, and
 * an exit veto asks for a confirmation, in the display locale. The original's confirmation titles are dropped:
 * {@code Dialogs.confirm} takes a message only.
 */
@Application
public final class LocaleExample3 implements SingleFrame, ExitVeto {

    static final Locale SPANISH = Locale.of("es");

    private static final int GAP = 5;

    @Exposed
    final LocalePane en;

    @Exposed
    final LocalePane fr;

    @Exposed
    final LocalePane es;

    private final JPanel buttons = new JPanel(new GridLayout(0, 1, GAP, GAP));
    private final Ui ui;
    private final Locales locales;
    private final LocalePaneMessages messages;

    @Inject
    LocaleExample3(Ui ui, StandardCommands commands, Locales locales, LocalePaneMessages messages) {
        this.ui = ui;
        this.locales = locales;
        this.messages = messages;
        Runnable exit = () -> commands.exit().execute();
        en = new LocalePane(exit);
        fr = new LocalePane(exit);
        es = new LocalePane(exit);
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(LocaleExample3.class, args);
    }

    @Override
    public JComponent content() {
        buttons.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        en.show(locales.messages(LocalePaneMessages.class, Locale.ENGLISH));
        fr.show(locales.messages(LocalePaneMessages.class, Locale.FRENCH));
        es.show(locales.messages(LocalePaneMessages.class, SPANISH));
        buttons.add(en);
        buttons.add(fr);
        buttons.add(es);
        return buttons;
    }

    @Override
    public boolean canExit() {
        return ui.dialogs().confirm(messages.stop());
    }
}
