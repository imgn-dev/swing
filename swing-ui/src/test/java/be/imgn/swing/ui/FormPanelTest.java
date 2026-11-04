package be.imgn.swing.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import org.junit.jupiter.api.Test;

/** The layout of a form in two columns, in the order of the calls, on the EDT as every call must be. */
class FormPanelTest {

    private static final Insets CELL = new Insets(4, 4, 4, 4);

    @Test
    void laysOutARowAsALabelAtTheLineEndAndAFieldFillingTheSecondColumn() throws Exception {
        JLabel label = new JLabel("Name");
        JTextField field = new JTextField();

        JPanel panel = onEdt(() -> FormPanel.create().row(label, field).build());

        assertThat(panel.getComponents()).startsWith(label, field);
        GridBagConstraints labelCell = constraints(panel, label);
        assertThat(labelCell.gridx).isZero();
        assertThat(labelCell.gridy).isZero();
        assertThat(labelCell.gridwidth).isEqualTo(1);
        assertThat(labelCell.anchor).isEqualTo(GridBagConstraints.LINE_END);
        assertThat(labelCell.fill).isEqualTo(GridBagConstraints.NONE);
        assertThat(labelCell.weightx).isZero();
        GridBagConstraints fieldCell = constraints(panel, field);
        assertThat(fieldCell.gridx).isEqualTo(1);
        assertThat(fieldCell.gridy).isZero();
        assertThat(fieldCell.fill).isEqualTo(GridBagConstraints.HORIZONTAL);
        assertThat(fieldCell.weightx).isEqualTo(1);
        assertThat(fieldCell.weighty).isZero();
    }

    @Test
    void setsTheLabelForItsField() throws Exception {
        JLabel label = new JLabel("Name");
        JTextField field = new JTextField();

        onEdt(() -> FormPanel.create().row(label, field).build());

        assertThat(label.getLabelFor()).isSameAs(field);
    }

    @Test
    void laysOutAWideComponentAcrossBothColumnsSharingTheExtraHeight() throws Exception {
        JLabel label = new JLabel("Name");
        JTextField field = new JTextField();
        JScrollPane notes = new JScrollPane();
        JPanel nested = new JPanel();

        JPanel panel = onEdt(() ->
                FormPanel.create().row(label, field).wide(notes).wide(nested).build());

        GridBagConstraints notesCell = constraints(panel, notes);
        assertThat(notesCell.gridx).isZero();
        assertThat(notesCell.gridy).isEqualTo(1);
        assertThat(notesCell.gridwidth).isEqualTo(2);
        assertThat(notesCell.fill).isEqualTo(GridBagConstraints.BOTH);
        assertThat(notesCell.weightx).isEqualTo(1);
        assertThat(notesCell.weighty).isEqualTo(1);
        GridBagConstraints nestedCell = constraints(panel, nested);
        assertThat(nestedCell.gridy).isEqualTo(2);
        assertThat(nestedCell.weighty).isEqualTo(notesCell.weighty);
        assertThat(constraints(panel, field).weighty).isZero();
    }

    @Test
    void keepsTheRowsAtTheTopWithoutAWideComponent() throws Exception {
        JLabel label = new JLabel("Name");
        JTextField field = new JTextField();

        Rectangle bounds = onEdt(() -> {
            JPanel panel = FormPanel.create().row(label, field).build();
            panel.setSize(300, 400);
            panel.doLayout();
            return field.getBounds();
        });

        // The panel's border, then the cell's inset.
        assertThat(bounds.y).isEqualTo(8);
    }

    @Test
    void laysOutASectionAsItsTitleFollowedByASeparatorAcrossBothColumns() throws Exception {
        JLabel title = new JLabel("Address");
        JLabel label = new JLabel("Street");
        JTextField field = new JTextField();

        JPanel panel =
                onEdt(() -> FormPanel.create().section(title).row(label, field).build());

        Component section = panel.getComponent(0);
        GridBagConstraints sectionCell = constraints(panel, section);
        assertThat(sectionCell.gridx).isZero();
        assertThat(sectionCell.gridy).isZero();
        assertThat(sectionCell.gridwidth).isEqualTo(2);
        assertThat(sectionCell.fill).isEqualTo(GridBagConstraints.HORIZONTAL);
        assertThat(sectionCell.weightx).isEqualTo(1);
        assertThat(constraints(panel, label).gridy).isEqualTo(1);
        JPanel row = (JPanel) section;
        assertThat(row.getComponents()).hasSize(2);
        assertThat(row.getComponent(0)).isSameAs(title);
        assertThat(row.getComponent(1)).isInstanceOf(JSeparator.class);
        GridBagConstraints titleCell = constraints(row, title);
        GridBagConstraints separatorCell = constraints(row, row.getComponent(1));
        assertThat(titleCell.gridx).isZero();
        assertThat(titleCell.weightx).isZero();
        assertThat(separatorCell.gridx).isEqualTo(1);
        assertThat(separatorCell.fill).isEqualTo(GridBagConstraints.HORIZONTAL);
        assertThat(separatorCell.weightx).isEqualTo(1);
    }

    @Test
    void putsTheGapOfASectionOnTheTrailingSideOfItsTitle() throws Exception {
        JLabel title = new JLabel("Address");

        Bounds leftToRight = onEdt(() -> section(title, ComponentOrientation.LEFT_TO_RIGHT));
        Bounds rightToLeft = onEdt(() -> section(title, ComponentOrientation.RIGHT_TO_LEFT));

        assertThat(leftToRight.title().x).isZero();
        assertThat(leftToRight.separator().x).isEqualTo(leftToRight.title().x + leftToRight.title().width + 4);
        assertThat(leftToRight.separator().x + leftToRight.separator().width).isEqualTo(400);
        assertThat(rightToLeft.title().x + rightToLeft.title().width).isEqualTo(400);
        assertThat(rightToLeft.separator().x + rightToLeft.separator().width + 4)
                .isEqualTo(rightToLeft.title().x);
        assertThat(rightToLeft.separator().x).isZero();
    }

    // Lays out a section's nested panel, 400 pixels wide, in an orientation, and returns where its title and separator
    // are.
    private static Bounds section(JLabel title, ComponentOrientation orientation) {
        JPanel panel = FormPanel.create().section(title).build();
        JPanel row = (JPanel) panel.getComponent(0);
        row.applyComponentOrientation(orientation);
        row.setSize(400, row.getPreferredSize().height);
        row.doLayout();
        return new Bounds(title.getBounds(), row.getComponent(1).getBounds());
    }

    @Test
    void givesEveryCellAnInsetOfFourPixelsAndThePanelAnEmptyBorderOfFourPixels() throws Exception {
        JLabel title = new JLabel("Address");
        JLabel label = new JLabel("Street");
        JTextField field = new JTextField();
        JPanel nested = new JPanel();

        JPanel panel = onEdt(() ->
                FormPanel.create().section(title).row(label, field).wide(nested).build());

        for (Component component : panel.getComponents()) {
            assertThat(constraints(panel, component).insets).isEqualTo(CELL);
        }
        assertThat(panel.getBorder()).isInstanceOf(EmptyBorder.class);
        assertThat(((EmptyBorder) panel.getBorder()).getBorderInsets()).isEqualTo(CELL);
    }

    @Test
    void mayBeBuiltOnceThenRejectsEveryCall() throws Exception {
        FormPanel form = onEdt(FormPanel::create);
        onEdt(form::build);

        assertThatThrownBy(() -> onEdt(form::build)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> onEdt(() -> form.row(new JLabel(), new JTextField())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> onEdt(() -> form.wide(new JPanel()))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> onEdt(() -> form.section(new JLabel()))).isInstanceOf(IllegalStateException.class);
    }

    private static GridBagConstraints constraints(JPanel panel, Component component) {
        return ((GridBagLayout) panel.getLayout()).getConstraints(component);
    }

    // Runs on the EDT and rethrows what it throws.
    private static <T> T onEdt(Supplier<T> call) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        try {
            EventQueue.invokeAndWait(() -> result.set(call.get()));
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
        return Objects.requireNonNull(result.get());
    }

    private record Bounds(Rectangle title, Rectangle separator) {}
}
