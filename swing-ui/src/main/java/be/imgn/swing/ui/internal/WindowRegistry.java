package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.Scope;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import javax.swing.JRootPane;
import org.jspecify.annotations.Nullable;

/** The windows and views the framework opened, from the main window to the innermost dialog. Used on the EDT. */
@Singleton
public final class WindowRegistry {

    private final InputBlocker blocker;
    private final List<WindowHost> windows = new ArrayList<>();
    // The frames that had the focus, the most recent first.
    private final List<WindowHost> focusOrder = new ArrayList<>();
    private final IdentityHashMap<Injector, ViewHost> viewsByInjector = new IdentityHashMap<>();
    private final IdentityHashMap<Scope, ViewHost> viewsByScope = new IdentityHashMap<>();

    @Inject
    WindowRegistry(InputBlocker blocker) {
        this.blocker = blocker;
    }

    /**
     * Registers a window, until its scope closes, and covers it if an application-wide block is active.
     *
     * @param window the window's host
     */
    public synchronized void addWindow(WindowHost window) {
        windows.add(window);
        window.scope().onClose(() -> removeWindow(window));
        blocker.windowOpened(window.window().rootPane());
    }

    private synchronized void removeWindow(WindowHost window) {
        windows.remove(window);
        focusOrder.remove(window);
    }

    /**
     * Registers a view, until its scope closes.
     *
     * @param view the view's host
     */
    public synchronized void addView(ViewHost view) {
        viewsByInjector.put(view.injector(), view);
        viewsByScope.put(view.scope(), view);
        view.scope().onClose(() -> removeView(view));
    }

    private synchronized void removeView(ViewHost view) {
        viewsByInjector.remove(view.injector());
        viewsByScope.remove(view.scope());
    }

    /**
     * Returns the open windows, from the main window to the innermost dialog.
     *
     * @return the windows
     */
    public synchronized List<WindowHost> windows() {
        return List.copyOf(windows);
    }

    /**
     * Returns the main window.
     *
     * @return the main window, if open
     */
    public synchronized Optional<WindowHost> main() {
        return windows.stream().filter(WindowHost::isMain).findFirst();
    }

    /**
     * Records that a frame has the focus: it becomes the active frame, until another one does.
     *
     * @param frame the frame's host
     */
    public synchronized void focused(WindowHost frame) {
        focusOrder.remove(frame);
        focusOrder.addFirst(frame);
    }

    /**
     * Returns the open frames, in the order they were opened, the main window first.
     *
     * @return the frames
     */
    public synchronized List<WindowHost> frames() {
        return windows.stream()
                .filter(WindowHost::isFrame)
                .filter(window -> !window.views().isEmpty())
                .toList();
    }

    /**
     * Returns the active frame: the frame that has the focus, else the frame that had it last, else the frame opened
     * last.
     *
     * @return the frame, empty when no frame is open
     */
    public synchronized Optional<WindowHost> activeFrame() {
        List<WindowHost> frames = frames();
        for (WindowHost frame : focusOrder) {
            if (frames.contains(frame)) {
                return Optional.of(frame);
            }
        }
        return frames.isEmpty() ? Optional.empty() : Optional.of(frames.getLast());
    }

    /**
     * Returns the view hosted by an injector.
     *
     * @param injector the injector
     * @return the view, if the injector hosts one
     */
    public synchronized Optional<ViewHost> viewOf(Injector injector) {
        return Optional.ofNullable(viewsByInjector.get(injector));
    }

    /**
     * Returns the view of a scope.
     *
     * @param scope the view's scope
     * @return the view
     * @throws IllegalStateException if the scope is not a view's
     */
    public synchronized ViewHost viewOf(Scope scope) {
        @Nullable ViewHost view = viewsByScope.get(scope);
        if (view == null) {
            throw new IllegalStateException(scope + " is not the scope of a view the framework built");
        }
        return view;
    }

    /**
     * Returns the window of an AWT window or a root pane.
     *
     * @param rootPane the root pane
     * @return the window, if the framework opened it
     */
    public synchronized Optional<WindowHost> windowOf(JRootPane rootPane) {
        return windows.stream()
                .filter(window -> window.window().rootPane().equals(rootPane))
                .findFirst();
    }
}
