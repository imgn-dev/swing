package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class AccessibilityCheckTest {

    private final JRootPane rootPane = new JRootPane();
    private final JPanel content = new JPanel();

    AccessibilityCheckTest() {
        rootPane.setContentPane(content);
    }

    private void check(Map<Component, String> fields) {
        AccessibilityCheck.check(rootPane, fields, AccessibilityCheckTest.class, new UiMode(true));
    }

    @Test
    void reportsAFieldWithNoAccessibleNameByItsFieldPath() {
        JTextField field = new JTextField();
        content.add(field);

        assertThatIllegalStateException()
                .isThrownBy(() -> check(Map.of(field, "home.street")))
                .withMessageContaining("The JTextField home.street of " + AccessibilityCheckTest.class.getName()
                        + " has no accessible name");
    }

    @Test
    void namesAComponentThatIsNoFieldByItsIndexPath() {
        JPanel row = new JPanel();
        row.add(new JLabel("Hello"));
        row.add(new JTextField());
        content.add(row);

        assertThatIllegalStateException()
                .isThrownBy(() -> check(Map.of()))
                .withMessageContaining("The JTextField at 0.1 of");
    }

    @Test
    void acceptsANameFromALabelATextOrAResource() {
        JTextField labelled = new JTextField();
        JLabel label = new JLabel("Name");
        label.setDisplayedMnemonic(KeyEvent.VK_N);
        label.setLabelFor(labelled);
        JTextField named = new JTextField();
        named.getAccessibleContext().setAccessibleName("Street");
        content.add(label);
        content.add(labelled);
        content.add(named);
        content.add(new JButton("OK"));

        assertThatCode(() -> check(Map.of())).doesNotThrowAnyException();
    }

    @Test
    void skipsWhatIsHiddenOrNotFocusableAndDoesNotLookInsideANamedComponent() {
        JTextField hidden = new JTextField();
        hidden.setVisible(false);
        JTextField unfocusable = new JTextField();
        unfocusable.setFocusable(false);
        JPanel hiddenPanel = new JPanel();
        hiddenPanel.add(new JTextField());
        hiddenPanel.setVisible(false);
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.getAccessibleContext().setAccessibleName("Choice");
        content.add(hidden);
        content.add(unfocusable);
        content.add(hiddenPanel);
        content.add(combo);

        assertThatCode(() -> check(Map.of())).doesNotThrowAnyException();
    }

    @Test
    void reportsALabelWithAMnemonicThatLabelsNothing() {
        JLabel label = new JLabel("Name");
        label.setDisplayedMnemonic(KeyEvent.VK_N);
        content.add(label);

        assertThatIllegalStateException()
                .isThrownBy(() -> check(Map.of()))
                .withMessageContaining("has a mnemonic but labels no component");
    }

    @Test
    void onlyWarnsOutsideStrictMode() {
        content.add(new JTextField());

        assertThatCode(() -> AccessibilityCheck.check(rootPane, Map.of(), Object.class, new UiMode(false)))
                .doesNotThrowAnyException();
    }
}
