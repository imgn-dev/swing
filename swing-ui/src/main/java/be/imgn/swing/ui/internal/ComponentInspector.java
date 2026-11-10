package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import org.jspecify.annotations.Nullable;

/** Tells what a component of an open window is, as the inspector overlay shows it and the test harness reads it. */
@Singleton
public final class ComponentInspector {

    /** The client property of the components a command created, which holds the command's identity. */
    public static final String COMMAND_PROPERTY = "be.imgn.swing.command";

    private final WindowRegistry windows;
    private final ViewSupports supports;

    @Inject
    ComponentInspector(WindowRegistry windows, ViewSupports supports) {
        this.windows = windows;
        this.supports = supports;
    }

    /**
     * Inspects a component, on the EDT.
     *
     * @param component the component
     * @return what it is, or empty if it is in no open window
     */
    public Optional<Inspected> inspect(Component component) {
        JRootPane rootPane = rootPaneOf(component);
        if (rootPane == null) {
            return Optional.empty();
        }
        return windows.windowOf(rootPane)
                .filter(window -> !window.views().isEmpty())
                .map(window -> {
                    ViewHost view = window.views().getFirst();
                    String command = component instanceof JComponent held
                                    && held.getClientProperty(COMMAND_PROPERTY) instanceof String identity
                            ? identity
                            : null;
                    return new Inspected(
                            component.getClass(),
                            view.type(),
                            supports.fields(view).get(component),
                            command,
                            window.kind());
                });
    }

    // A menu item is in a popup menu, whose root pane is its own when heavyweight, or none: its invoker's is the
    // window's.
    private static @Nullable JRootPane rootPaneOf(Component component) {
        Component current = component;
        while (current != null) {
            if (current instanceof JPopupMenu popup) {
                current = popup.getInvoker();
            } else if (current instanceof JRootPane rootPane) {
                return rootPane;
            } else {
                current = current.getParent();
            }
        }
        return null;
    }

    /**
     * What a component is.
     *
     * @param type the component's class
     * @param view the class of the view whose window holds it
     * @param field the path of the {@code @Exposed} field that holds it, which is also its table in the view's
     *     resources, or {@code null} for none
     * @param command the identity of the command that created it, or {@code null} for none
     * @param window the kind of its window
     */
    public record Inspected(
            Class<? extends Component> type,
            Class<?> view,
            @Nullable String field,
            @Nullable String command,
            WindowHost.Kind window) {

        /**
         * Returns the lines the overlay shows.
         *
         * @return the lines
         */
        public List<String> lines() {
            List<String> lines = new ArrayList<>();
            lines.add(type.getName());
            lines.add("field " + (field == null ? "none" : field + "  [" + field + "]"));
            if (command != null) {
                lines.add("command " + command + "  [commands." + command.substring(command.indexOf('#') + 1) + "]");
            }
            lines.add("view " + view.getName());
            lines.add("window "
                    + switch (window) {
                        case MAIN -> "main frame";
                        case FRAME -> "frame";
                        case DIALOG -> "dialog";
                    });
            return List.copyOf(lines);
        }
    }
}
