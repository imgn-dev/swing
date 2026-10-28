package be.imgn.swing.ui.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.internal.DisposalScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A view the framework built: its class, child injector and scope, in its window. */
public final class ViewHost {

    private Class<?> type;
    private final Injector injector;
    private final DisposalScope scope;
    private final WindowHost window;
    private final @Nullable DialogControl dialog;
    private final List<Runnable> pendingAttachments = new ArrayList<>();
    private boolean building = true;
    private @Nullable Object view;

    /**
     * Creates the host of a view.
     *
     * @param type the view class
     * @param injector the child injector hosting the view scope
     * @param scope the view's scope
     * @param window the view's window
     * @param dialog the control of the dialog, for a dialog view
     */
    public ViewHost(
            Class<?> type, Injector injector, DisposalScope scope, WindowHost window, @Nullable DialogControl dialog) {
        this.type = type;
        this.injector = injector;
        this.scope = scope;
        this.window = window;
        this.dialog = dialog;
        window.add(this);
    }

    /**
     * Returns the view class.
     *
     * @return the class
     */
    public Class<?> type() {
        return type;
    }

    /**
     * Sets the view class, once known: a view created by an assisted factory is known after its creation.
     *
     * @param resolved the view class
     */
    public void type(Class<?> resolved) {
        this.type = resolved;
    }

    /**
     * Returns the child injector hosting the view scope.
     *
     * @return the injector
     */
    public Injector injector() {
        return injector;
    }

    /**
     * Returns the view's scope.
     *
     * @return the scope
     */
    public DisposalScope scope() {
        return scope;
    }

    /**
     * Returns the view's window.
     *
     * @return the window's host
     */
    public WindowHost window() {
        return window;
    }

    /**
     * Returns the control of the dialog, for a dialog view.
     *
     * @return the control, empty for another view
     */
    public Optional<DialogControl> dialog() {
        return Optional.ofNullable(dialog);
    }

    /**
     * Returns the view instance, once resolved.
     *
     * @return the view, empty while it is not resolved
     */
    public Optional<Object> view() {
        return Optional.ofNullable(view);
    }

    /**
     * Sets the view instance.
     *
     * @param resolved the view
     */
    public void view(Object resolved) {
        this.view = resolved;
    }

    /**
     * Runs an attachment: at once if the view is built, else when its build ends.
     *
     * @param attachment the attachment
     */
    public void attach(Runnable attachment) {
        if (building) {
            pendingAttachments.add(attachment);
        } else {
            attachment.run();
        }
    }

    /** Ends the build of the view: runs the attachments made meanwhile. */
    public void built() {
        building = false;
        List<Runnable> pending = List.copyOf(pendingAttachments);
        pendingAttachments.clear();
        pending.forEach(Runnable::run);
    }
}
