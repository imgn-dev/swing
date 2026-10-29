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
    private String stateKey;
    private final ShortcutBindings shortcuts;
    private final List<ViewHost> views = new ArrayList<>();

    /**
     * Creates the host of a window.
     *
     * @param window the window
     * @param injector the child injector hosting the window scope
     * @param scope the window's scope
     * @param opener the view that opened the window, for a dialog
     * @param stateKey the key of the window's saved state: {@code main}, or the binary name of a dialog's view class
     * @param mode how strictly the window's mistakes are reported
     */
    public WindowHost(
            Window window,
            Injector injector,
            DisposalScope scope,
            @Nullable ViewHost opener,
            String stateKey,
            UiMode mode) {
        this.window = window;
        this.injector = injector;
        this.scope = scope;
        this.opener = opener;
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
     * @return the opener, empty for the main window
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
        return opener == null;
    }

    /**
     * Returns the key of the window's saved state.
     *
     * @return {@code main}, or the binary name of a dialog's view class
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
}
