package be.imgn.swing.examples.saf;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** A label and a button that exits, whose texts are those of the messages it shows. */
final class LocalePane extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel label = new JLabel();
    private final JButton button = new JButton();

    LocalePane(Runnable exit) {
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        button.addActionListener(event -> exit.run());
        add(label);
        add(Box.createGlue());
        add(button);
    }

    JLabel label() {
        return label;
    }

    JButton button() {
        return button;
    }

    void show(LocalePaneMessages messages) {
        label.setText(messages.text());
        button.setText(messages.what());
    }
}
