package be.imgn.swing.ui.internal;

import be.imgn.swing.ui.ValidationFeedback;
import java.awt.Color;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import org.jspecify.annotations.Nullable;

/**
 * The default {@link ValidationFeedback}: it wraps the component's border in a compound border, with a red one-pixel
 * {@link LineBorder} outside it, sets the component's tool tip text and accessible description to the message, and
 * restores the three when the error is hidden.
 */
public final class BorderValidationFeedback implements ValidationFeedback {

    // The client property that holds what showing an error replaced, while the component shows one.
    private static final Object SAVED = new Object();

    @Override
    public void show(JComponent component, String error) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(error, "error");
        if (!(component.getClientProperty(SAVED) instanceof Saved)) {
            Border border = component.getBorder();
            component.putClientProperty(SAVED, new Saved(border, ErrorTexts.of(component)));
            component.setBorder(BorderFactory.createCompoundBorder(new LineBorder(Color.RED, 1), border));
        }
        ErrorTexts.show(component, error);
    }

    @Override
    public void hide(JComponent component) {
        Objects.requireNonNull(component, "component");
        if (component.getClientProperty(SAVED) instanceof Saved saved) {
            component.putClientProperty(SAVED, null);
            component.setBorder(saved.border());
            saved.texts().restore(component);
        }
    }

    private record Saved(@Nullable Border border, ErrorTexts texts) {}
}
