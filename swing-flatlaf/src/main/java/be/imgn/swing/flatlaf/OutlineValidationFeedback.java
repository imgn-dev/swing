package be.imgn.swing.flatlaf;

import be.imgn.swing.ui.ValidationFeedback;
import be.imgn.swing.ui.internal.ErrorTexts;
import com.formdev.flatlaf.FlatClientProperties;
import java.util.Objects;
import javax.swing.JComponent;
import org.jspecify.annotations.Nullable;

/**
 * FlatLaf's {@link ValidationFeedback}: it sets the component's {@code JComponent.outline} client property to
 * {@code "error"}, which FlatLaf paints around the component, sets its tool tip text and accessible description to the
 * message, and restores them when the error is hidden.
 */
final class OutlineValidationFeedback implements ValidationFeedback {

    // The client property that holds what showing an error replaced, while the component shows one.
    private static final Object SAVED = new Object();

    @Override
    public void show(JComponent component, String error) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(error, "error");
        if (!(component.getClientProperty(SAVED) instanceof Saved)) {
            component.putClientProperty(
                    SAVED,
                    new Saved(component.getClientProperty(FlatClientProperties.OUTLINE), ErrorTexts.of(component)));
            component.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        }
        ErrorTexts.show(component, error);
    }

    @Override
    public void hide(JComponent component) {
        Objects.requireNonNull(component, "component");
        if (component.getClientProperty(SAVED) instanceof Saved saved) {
            component.putClientProperty(SAVED, null);
            component.putClientProperty(FlatClientProperties.OUTLINE, saved.outline());
            saved.texts().restore(component);
        }
    }

    private record Saved(@Nullable Object outline, ErrorTexts texts) {}
}
