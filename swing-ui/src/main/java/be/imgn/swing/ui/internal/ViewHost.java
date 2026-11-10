package be.imgn.swing.ui.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.Scope;
import be.imgn.swing.internal.DisposalScope;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A view the framework built: its class, child injector and scope, in its window. */
public final class ViewHost {

    private Class<?> type;
    private final Injector injector;
    private final DisposalScope scope;
    private final WindowHost window;
    private final @Nullable DialogControl dialog;
    private final Thread builder = Thread.currentThread();
    // Guarded by this: another thread may attach while the view is built.
    private final List<Runnable> pendingAttachments = new ArrayList<>();
    private boolean building = true;
    private @Nullable Object view;
    private @Nullable Enum<?> variant;
    // What a refresh runs for the view, by phase, in registration order; each action leaves with its scope.
    private final Map<RefreshPhase, List<Runnable>> refreshes = new EnumMap<>(RefreshPhase.class);

    /**
     * Creates the host of a view, on the thread that builds it.
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
     * Returns the variant the view was opened with.
     *
     * @return the variant, or empty for none
     */
    public Optional<Enum<?>> variant() {
        return Optional.ofNullable(variant);
    }

    /**
     * Records the variant the view is opened with.
     *
     * @param opened the variant
     */
    public void variant(Enum<?> opened) {
        this.variant = opened;
    }

    /**
     * Adds what a refresh runs for the view in a phase, until a scope closes.
     *
     * @param phase the phase
     * @param scope the scope the action belongs to
     * @param action the action, run on the EDT
     */
    public void onRefresh(RefreshPhase phase, Scope scope, Runnable action) {
        List<Runnable> actions = refreshes.computeIfAbsent(phase, ignored -> new ArrayList<>());
        actions.add(action);
        scope.onClose(() -> actions.remove(action));
    }

    /**
     * Runs what a refresh runs for the view in a phase, in registration order.
     *
     * @param phase the phase
     */
    public void refresh(RefreshPhase phase) {
        List.copyOf(refreshes.getOrDefault(phase, List.of())).forEach(Runnable::run);
    }

    /**
     * Runs an attachment: when the view's build ends if it is made on the thread that builds the view, during that
     * build, else at once, since what another thread builds meanwhile doesn't belong to the build.
     *
     * @param attachment the attachment
     */
    public void attach(Runnable attachment) {
        synchronized (this) {
            if (building && Thread.currentThread() == builder) {
                pendingAttachments.add(attachment);
                return;
            }
        }
        attachment.run();
    }

    /** Ends the build of the view: runs the attachments made meanwhile. */
    public void built() {
        List<Runnable> pending;
        synchronized (this) {
            building = false;
            pending = List.copyOf(pendingAttachments);
            pendingAttachments.clear();
        }
        pending.forEach(Runnable::run);
    }

    /** Closes the view: its child injector, which runs {@code @PreDestroy} on its beans, then its scope. Idempotent. */
    public void close() {
        try {
            injector.close();
        } finally {
            scope.close();
        }
    }
}
