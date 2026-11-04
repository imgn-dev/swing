package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import org.junit.jupiter.api.Test;

/**
 * The default validation feedback: a red line around the component's border, and the message as its texts. Each test
 * runs on the EDT, whose failures {@link Edt#invokeAndWait(Runnable)} rethrows.
 */
class BorderValidationFeedbackTest {

    private final BorderValidationFeedback feedback = new BorderValidationFeedback();

    @Test
    void isWhatTheUiModulesProvidesMethodReturns() {
        assertThat(UiBeans.borderFeedback()).isInstanceOf(BorderValidationFeedback.class);
    }

    @Test
    void wrapsTheBorderInARedOnePixelLineAndSetsTheTexts() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();
            Border original = BorderFactory.createEmptyBorder(2, 2, 2, 2);
            field.setBorder(original);

            feedback.show(field, "Required");

            assertThat(field.getBorder()).isInstanceOf(CompoundBorder.class);
            CompoundBorder border = (CompoundBorder) field.getBorder();
            assertThat(border.getInsideBorder()).isSameAs(original);
            assertThat(border.getOutsideBorder()).isInstanceOf(LineBorder.class);
            LineBorder line = (LineBorder) border.getOutsideBorder();
            assertThat(line.getLineColor()).isEqualTo(Color.RED);
            assertThat(line.getThickness()).isEqualTo(1);
            assertThat(field.getToolTipText()).isEqualTo("Required");
            assertThat(field.getAccessibleContext().getAccessibleDescription()).isEqualTo("Required");
        });
    }

    @Test
    void restoresTheBorderAndTheTextsWhenTheErrorIsHidden() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();
            Border original = field.getBorder();
            field.setToolTipText("Your name");
            field.getAccessibleContext().setAccessibleDescription("The name you go by");

            feedback.show(field, "Required");
            feedback.hide(field);

            assertThat(field.getBorder()).isSameAs(original);
            assertThat(field.getToolTipText()).isEqualTo("Your name");
            assertThat(field.getAccessibleContext().getAccessibleDescription()).isEqualTo("The name you go by");
        });
    }

    @Test
    void restoresAComponentWithoutTexts() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();

            feedback.show(field, "Required");
            feedback.hide(field);

            assertThat(field.getToolTipText()).isNull();
            assertThat(field.getAccessibleContext().getAccessibleDescription()).isNull();
        });
    }

    @Test
    void leavesADescriptionThatFollowedTheToolTipFollowingIt() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();
            field.setToolTipText("Your name");

            feedback.show(field, "Required");
            feedback.hide(field);
            field.setToolTipText("Your full name");

            assertThat(field.getAccessibleContext().getAccessibleDescription()).isEqualTo("Your full name");
        });
    }

    @Test
    void replacesTheMessageOfAShownErrorWithoutWrappingTheBorderAgain() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();
            Border original = field.getBorder();

            feedback.show(field, "Required");
            feedback.show(field, "At most 10 characters");

            assertThat(((CompoundBorder) field.getBorder()).getInsideBorder()).isSameAs(original);
            assertThat(field.getToolTipText()).isEqualTo("At most 10 characters");
            assertThat(field.getAccessibleContext().getAccessibleDescription()).isEqualTo("At most 10 characters");

            feedback.hide(field);

            assertThat(field.getBorder()).isSameAs(original);
            assertThat(field.getToolTipText()).isNull();
        });
    }

    @Test
    void hidingAnErrorThatIsNotShownChangesNothing() {
        Edt.invokeAndWait(() -> {
            JTextField field = new JTextField();
            Border original = field.getBorder();
            field.setToolTipText("Your name");

            feedback.hide(field);

            assertThat(field.getBorder()).isSameAs(original);
            assertThat(field.getToolTipText()).isEqualTo("Your name");
        });
    }
}
