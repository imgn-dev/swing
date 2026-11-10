package be.imgn.swing.ui.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.ui.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A window the framework opened: its child injector, scope, views and shortcuts. */
public final class WindowHost {

    private final Window window;
    private final Injector injector;
    private final DisposalScope scope;
    private final @Nullable ViewHost opener;
    private final Kind kind;
    private String stateKey;
    private final ShortcutBindings shortcuts;
    private final List<ViewHost> views = new ArrayList<>();
    // The title the window took from its view's resources, or the displayed name; null until it took one.
    private @Nullable String resourceTitle;

    /**
     * Creates the host of a window.
     *
     * @param window the window
     * @param injector the child injector hosting the window scope
     * @param scope the window's scope
     * @param opener the view that opened the window, for a dialog
     * @param kind what the window is
     * @param stateKey the key of the window's saved state: {@code main}, or the binary name of a view class
     * @param mode how strictly the window's mistakes are reported
     */
    public WindowHost(
            Window window,
            Injector injector,
            DisposalScope scope,
            @Nullable ViewHost opener,
            Kind kind,
            String stateKey,
            UiMode mode) {
        this.window = window;
        this.injector = injector;
        this.scope = scope;
        this.opener = opener;
        this.kind = kind;
        this.stateKey = stateKey;
        this.shortcuts = new ShortcutBindings(window.rootPane(), mode);
    }

    /**
     * Returns the window.
     *
     * @return the window
     */
    public Window window() {
        return window;
    }

    /**
     * Returns the child injector hosting the window scope.
     *
     * @return the injector
     */
    public Injector injector() {
        return injector;
    }

    /**
     * Returns the window's scope.
     *
     * @return the scope
     */
    public DisposalScope scope() {
        return scope;
    }

    /**
     * Returns the view that opened this window, for a dialog.
     *
     * @return the opener, empty for a frame
     */
    public Optional<ViewHost> opener() {
        return Optional.ofNullable(opener);
    }

    /**
     * Returns whether this window is the main window.
     *
     * @return {@code true} for the main window
     */
    public boolean isMain() {
        return kind == Kind.MAIN;
    }

    /**
     * Returns whether this window is a frame: the main window, or a frame that {@code Frames} opened.
     *
     * @return {@code true} for a frame
     */
    public boolean isFrame() {
        return kind != Kind.DIALOG;
    }

    /**
     * Returns what the window is.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the key of the window's saved state.
     *
     * @return {@code main}, or the binary name of a view class
     */
    public String stateKey() {
        return stateKey;
    }

    /**
     * Sets the key of the window's saved state, once known.
     *
     * @param key the key
     */
    public void stateKey(String key) {
        this.stateKey = key;
    }

    /**
     * Returns the title the window took from its view's {@code title} resource, else the displayed name.
     *
     * @return the title, or empty if the framework set none
     */
    public Optional<String> resourceTitle() {
        return Optional.ofNullable(resourceTitle);
    }

    /**
     * Sets the title of the window from its view's resources, and records it.
     *
     * @param title the title
     */
    public void resourceTitle(String title) {
        this.resourceTitle = title;
        window.setTitle(title);
    }

    /**
     * Returns the shortcuts registered in the window.
     *
     * @return the shortcuts
     */
    public ShortcutBindings shortcuts() {
        return shortcuts;
    }

    /**
     * Returns the views of the window.
     *
     * @return the views, in creation order
     */
    public List<ViewHost> views() {
        return List.copyOf(views);
    }

    void add(ViewHost view) {
        views.add(view);
    }

    /**
     * Closes the window, innermost first: each view, the last built first, then the window's child injector, which runs
     * {@code @PreDestroy} on its beans, then the window's scope. A view's scope is thus closed right after its
     * injector, before the window's beans are destroyed. Idempotent.
     */
    public void close() {
        try {
            for (ViewHost view : views().reversed()) {
                view.close();
            }
        } finally {
            try {
                injector.close();
            } finally {
                scope.close();
            }
        }
    }

    /** What a window is. */
    public enum Kind {
        /** The main window of a single-frame application. */
        MAIN,
        /** A frame that {@code Frames} opened. */
        FRAME,
        /** A modal dialog. */
        DIALOG
    }
}
