package be.imgn.swing.testing;

import java.awt.Component;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the inspector overlay shows for a component of an open window.
 *
 * @param type the component's class
 * @param view the class of the view whose window holds it
 * @param field the path of the {@code @Exposed} field that holds it, which is also its table in the view's resources,
 *     or {@code null} for a component that is no field
 * @param command the identity of the command that created it, such as {@code p.MemoView#save}, or {@code null} for a
 *     component that is no command's
 */
public record Inspection(
        Class<? extends Component> type,
        Class<?> view,
        @Nullable String field,
        @Nullable String command) {

    /**
     * Creates the inspection.
     *
     * @param type the component's class
     * @param view the class of the view whose window holds it
     * @param field the path of its field, or {@code null}
     * @param command the identity of its command, or {@code null}
     * @throws NullPointerException if {@code type} or {@code view} is {@code null}
     */
    public Inspection {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(view, "view");
    }
}
