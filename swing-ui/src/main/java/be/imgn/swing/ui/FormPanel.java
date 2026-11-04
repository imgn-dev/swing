package be.imgn.swing.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

/**
 * Lays out a form in two columns, with {@link GridBagLayout}, in the order its methods are called: rows of a label and
 * its field, wide components across both columns, and section titles. Every cell has an inset of 4 pixels on each side,
 * and the panel an empty border of 4 pixels. The wide components share the extra height; without one, the rows stay at
 * the top of the panel.
 *
 * <p>The labels are typically {@link Exposed} fields of the view, so their texts and mnemonics come from its resources,
 * as for any component.
 *
 * <p>Every method is called on the EDT. {@link #build()} may be called once: any call afterwards throws
 * {@link IllegalStateException}.
 */
public final class FormPanel {

    private static final int GAP = 4;

    private final JPanel panel = new JPanel(new GridBagLayout());
    private int rows;
    private boolean hasWide;
    private boolean built;

    private FormPanel() {
        panel.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
    }

    /**
     * Returns a new, empty form panel.
     *
     * @return the form panel
     */
    public static FormPanel create() {
        return new FormPanel();
    }

    /**
     * Adds a label and its field: the label at the line end of the first column, the field filling the width of the
     * second. Calls {@code label.setLabelFor(field)}, so the label's mnemonic focuses the field.
     *
     * @param label the label
     * @param field the field
     * @return this form panel
     * @throws IllegalStateException if the panel was built
     */
    public FormPanel row(JLabel label, JComponent field) {
        checkNotBuilt();
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(field, "field");
        label.setLabelFor(field);
        GridBagConstraints labelCell = cell(0);
        labelCell.anchor = GridBagConstraints.LINE_END;
        panel.add(label, labelCell);
        GridBagConstraints fieldCell = cell(1);
        fieldCell.fill = GridBagConstraints.HORIZONTAL;
        fieldCell.weightx = 1;
        panel.add(field, fieldCell);
        rows++;
        return this;
    }

    /**
     * Adds a component across both columns, filling the row's width and sharing the extra height with the other wide
     * components, as a nested panel or a text area needs.
     *
     * @param component the component
     * @return this form panel
     * @throws IllegalStateException if the panel was built
     */
    public FormPanel wide(JComponent component) {
        checkNotBuilt();
        Objects.requireNonNull(component, "component");
        GridBagConstraints cell = cell(0);
        cell.gridwidth = 2;
        cell.fill = GridBagConstraints.BOTH;
        cell.weightx = 1;
        cell.weighty = 1;
        panel.add(component, cell);
        rows++;
        hasWide = true;
        return this;
    }

    /**
     * Adds a title across both columns: the label, followed by a {@link JSeparator} that fills the rest of the row, 4
     * pixels after it, on the label's trailing side whatever the component orientation.
     *
     * @param title the title
     * @return this form panel
     * @throws IllegalStateException if the panel was built
     */
    public FormPanel section(JLabel title) {
        checkNotBuilt();
        Objects.requireNonNull(title, "title");
        // The title and the separator share the one cell that spans both columns, so the separator starts where the
        // title ends, whatever the width of the first column.
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints titleCell = new GridBagConstraints();
        titleCell.gridx = 0;
        titleCell.anchor = GridBagConstraints.LINE_START;
        row.add(title, titleCell);
        GridBagConstraints separatorCell = new GridBagConstraints();
        separatorCell.gridx = 1;
        separatorCell.fill = GridBagConstraints.HORIZONTAL;
        separatorCell.weightx = 1;
        separatorCell.insets = new Insets(0, GAP, 0, 0);
        row.add(new JSeparator(), separatorCell);
        GridBagConstraints cell = cell(0);
        cell.gridwidth = 2;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.weightx = 1;
        panel.add(row, cell);
        rows++;
        return this;
    }

    /**
     * Returns the panel. It may be called once: any call on this form panel afterwards throws
     * {@link IllegalStateException}.
     *
     * @return the panel
     * @throws IllegalStateException if the panel was built
     */
    public JPanel build() {
        checkNotBuilt();
        built = true;
        if (!hasWide) {
            // GridBagLayout centres the rows when no cell takes the extra height: an empty row below them takes it.
            GridBagConstraints rest = new GridBagConstraints();
            rest.gridx = 0;
            rest.gridy = rows;
            rest.gridwidth = 2;
            rest.weighty = 1;
            panel.add(Box.createGlue(), rest);
        }
        return panel;
    }

    private GridBagConstraints cell(int column) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = column;
        cell.gridy = rows;
        cell.insets = new Insets(GAP, GAP, GAP, GAP);
        return cell;
    }

    private void checkNotBuilt() {
        if (built) {
            throw new IllegalStateException("The form panel is built: build() returned its panel");
        }
    }
}
