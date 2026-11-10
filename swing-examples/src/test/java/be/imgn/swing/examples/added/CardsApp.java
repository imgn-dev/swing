package be.imgn.swing.examples.added;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.SingleFrame;
import java.awt.CardLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Two required fields on two cards, the second hidden, as a wizard's steps are. */
@Application
public final class CardsApp implements SingleFrame {

    @Exposed
    final JLabel firstLabel = new JLabel();

    @Exposed
    final JLabel secondLabel = new JLabel();

    private final JTextField firstField = new JTextField(10);
    private final JTextField secondField = new JTextField(10);
    private final JPanel cards = new JPanel(new CardLayout());
    private final JPanel empty = new JPanel();
    private final Form form;

    @Inject
    CardsApp(Form form) {
        this.form = form;
    }

    @Override
    public JComponent content() {
        form.bind(firstField, Property.of("")).check(Rule.required("A first value is required"));
        form.bind(secondField, Property.of("")).check(Rule.required("A second value is required"));
        cards.add(FormPanel.create().row(firstLabel, firstField).build(), "first");
        cards.add(FormPanel.create().row(secondLabel, secondField).build(), "second");
        cards.add(empty, "empty");
        return cards;
    }

    JPanel card(int index) {
        return (JPanel) cards.getComponent(index);
    }

    void show(String card) {
        ((CardLayout) cards.getLayout()).show(cards, card);
    }

    Form form() {
        return form;
    }

    JTextField firstField() {
        return firstField;
    }

    JTextField secondField() {
        return secondField;
    }
}
