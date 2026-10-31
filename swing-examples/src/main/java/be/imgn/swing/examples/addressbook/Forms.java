package be.imgn.swing.examples.addressbook;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jspecify.annotations.Nullable;

/** Lays out forms of labelled fields, which Guts' demo does with DesignGridLayout, and reads their dates. */
final class Forms {

    private static final int GAP = 4;

    private Forms() {}

    /**
     * Returns a new panel for a form.
     *
     * @return the panel
     */
    static JPanel panel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        return panel;
    }

    /**
     * Adds a row of a label and its field to a form.
     *
     * @param form the form
     * @param label the label
     * @param field the field
     */
    static void row(JPanel form, JLabel label, JComponent field) {
        label.setLabelFor(field);
        GridBagConstraints left = new GridBagConstraints();
        left.anchor = GridBagConstraints.LINE_END;
        left.insets = new Insets(GAP, GAP, GAP, GAP);
        form.add(label, left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridwidth = GridBagConstraints.REMAINDER;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.weightx = 1;
        right.insets = new Insets(GAP, GAP, GAP, GAP);
        form.add(field, right);
    }

    /**
     * Adds a component spanning the width of a form.
     *
     * @param form the form
     * @param component the component
     */
    static void wide(JPanel form, JComponent component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        form.add(component, constraints);
    }

    /**
     * Returns the text of a date, in ISO format.
     *
     * @param date the date, if known
     * @return the text, empty for no date
     */
    static String text(@Nullable LocalDate date) {
        return date == null ? "" : date.toString();
    }

    /**
     * Returns the date of a field, in ISO format.
     *
     * @param field the field
     * @return the date, or {@code null} when the field is empty or is not a date
     */
    static @Nullable LocalDate date(JTextField field) {
        String text = field.getText().strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
