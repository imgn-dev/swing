package be.imgn.swing.flatlaf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.ui.internal.Edt;
import javax.swing.JTextField;
import javax.swing.border.Border;
import org.junit.jupiter.api.Test;

/**
 * FlatLaf's validation feedback: the error outline, and the message as the component's texts. Each test runs on the
 * EDT, whose failures {@link Edt#invokeAndWait(Runnable)} rethrows.
 */
class OutlineValidationFeedbackTest {

    private static final String OUTLINE = "JComponent.outline";

    private final OutlineValidationFeedback feedback = new OutlineValidationFeedback();

    @Test
    void isWhatFlatLafsProvidesMethodReturns() {
        assertThat(FlatLafBeans.outlineFeedback()).isInstanceOf(OutlineValidationFeedback.class);
    }

    @Test
    void setsTheErrorOutlineAndTheTextsWithoutChangingTheBorder() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();
            Border border = field.getBorder();

            feedback.show(field, "Required");

            assertThat(field.getClientProperty(OUTLINE)).isEqualTo("error");
            assertThat(field.getBorder()).isSameAs(border);
            assertThat(field.getToolTipText()).isEqualTo("Required");
            assertThat(field.getAccessibleContext().getAccessibleDescription()).isEqualTo("Required");
        });
    }

    @Test
    void restoresTheOutlineAndTheTextsWhenTheErrorIsHidden() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();
            field.putClientProperty(OUTLINE, "warning");
            field.setToolTipText("Your name");
            field.getAccessibleContext().setAccessibleDescription("The name you go by");

            feedback.show(field, "Required");
            feedback.hide(field);

            assertThat(field.getClientProperty(OUTLINE)).isEqualTo("warning");
            assertThat(field.getToolTipText()).isEqualTo("Your name");
            assertThat(field.getAccessibleContext().getAccessibleDescription()).isEqualTo("The name you go by");
        });
    }

    @Test
    void restoresAComponentWithoutOutlineNorTexts() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();

            feedback.show(field, "Required");
            feedback.hide(field);

            assertThat(field.getClientProperty(OUTLINE)).isNull();
            assertThat(field.getToolTipText()).isNull();
            assertThat(field.getAccessibleContext().getAccessibleDescription()).isNull();
        });
    }

    @Test
    void replacesTheMessageOfAShownError() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();

            feedback.show(field, "Required");
            feedback.show(field, "At most 10 characters");

            assertThat(field.getClientProperty(OUTLINE)).isEqualTo("error");
            assertThat(field.getToolTipText()).isEqualTo("At most 10 characters");

            feedback.hide(field);

            assertThat(field.getClientProperty(OUTLINE)).isNull();
            assertThat(field.getToolTipText()).isNull();
        });
    }
}
