package be.imgn.swing.ui.internal;

import java.util.Objects;
import javax.accessibility.AccessibleContext;
import javax.swing.JComponent;
import org.jspecify.annotations.Nullable;

/**
 * The tool tip text and accessible description of a component, which validation feedback sets to an error's message,
 * saved to be restored once the error is hidden.
 *
 * @param toolTip the tool tip text
 * @param description the accessible description
 */
public record ErrorTexts(@Nullable String toolTip, @Nullable String description) {

    /**
     * Returns the texts a component has.
     *
     * @param component the component
     * @return its texts
     */
    public static ErrorTexts of(JComponent component) {
        AccessibleContext context = component.getAccessibleContext();
        return new ErrorTexts(component.getToolTipText(), context == null ? null : context.getAccessibleDescription());
    }

    /**
     * Sets a component's tool tip text and accessible description to an error's message.
     *
     * @param component the component
     * @param message the message
     */
    public static void show(JComponent component, String message) {
        component.setToolTipText(message);
        AccessibleContext context = component.getAccessibleContext();
        if (context != null) {
            context.setAccessibleDescription(message);
        }
    }

    /**
     * Restores these texts on a component.
     *
     * @param component the component
     */
    public void restore(JComponent component) {
        component.setToolTipText(toolTip);
        AccessibleContext context = component.getAccessibleContext();
        if (context == null) {
            return;
        }
        // A component without a description of its own describes itself with its tool tip, which a description set
        // back explicitly would no longer follow: it is left unset when that gives the saved one.
        context.setAccessibleDescription(null);
        if (!Objects.equals(context.getAccessibleDescription(), description)) {
            context.setAccessibleDescription(description);
        }
    }
}
