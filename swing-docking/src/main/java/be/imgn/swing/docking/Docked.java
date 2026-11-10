package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import be.imgn.swing.Scope;
import be.imgn.swing.ui.ViewScoped;
import be.imgn.swing.ui.internal.WindowRegistry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The place of a dock view in its dock, which the view injects: its title, shown on its tab and in its header. At the
 * end of the view's build, unless the view has set it meanwhile, the title becomes its class's {@code title} resource,
 * else the class's simple name; a refresh sets it again from the new resources while it is still the one the resources,
 * or the simple name, gave it. Used on the EDT.
 */
@ViewScoped
public final class Docked {

    private String title = "";
    private boolean set;
    private @Nullable String resourceTitle;
    private Runnable changed = () -> {};

    /**
     * Creates the place of a view that a dock opens.
     *
     * @throws IllegalStateException if a dock didn't open the view, which a view created through a factory tells only
     *     by how it was opened, since its class is unknown until its constructor returns
     */
    @Inject
    Docked(Scope scope, WindowRegistry windows, Dock dock) {
        if (!dock.isOpening(windows.viewOf(scope))) {
            throw new IllegalStateException("Docked is resolved in a view that a Dock opens, not in another view");
        }
    }

    /**
     * Returns the title shown.
     *
     * @return the title, the empty string until one is set
     */
    public String title() {
        return title;
    }

    /**
     * Sets the title shown.
     *
     * @param title the title
     * @throws NullPointerException if {@code title} is {@code null}
     */
    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
        set = true;
        changed.run();
    }

    // Whether the view set its title.
    boolean isSet() {
        return set;
    }

    // Sets the title the resources, or the simple name, give.
    void resourceTitle(String value) {
        title = value;
        resourceTitle = value;
        changed.run();
    }

    // Sets the title the new resources give, while the title is still the one the old ones gave.
    void refreshTitle(String value) {
        if (title.equals(resourceTitle)) {
            resourceTitle(value);
        }
    }

    void onChange(Runnable action) {
        this.changed = action;
    }
}
