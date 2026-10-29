package be.imgn.swing.ui.internal;

import be.imgn.gaia.core.time.Ticker;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.internal.RunningTask;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;

/**
 * Blocks components and windows while tasks run. While a window is blocked, a key event dispatcher consumes the key
 * events of the blocked windows before any input map sees them. Used on the EDT.
 */
@Singleton
public final class InputBlocker {

    private final Ticker ticker;
    private final UiTexts texts;
    private final IdentityHashMap<Component, Integer> disabled = new IdentityHashMap<>();
    private final IdentityHashMap<JRootPane, Installed> glassPanes = new IdentityHashMap<>();
    private final List<ApplicationBlock> applicationBlocks = new ArrayList<>();
    private final KeyEventDispatcher keys = this::consumeIfBlocked;

    @Inject
    InputBlocker(Ticker ticker, UiTexts texts) {
        this.ticker = ticker;
        this.texts = texts;
    }

    /**
     * Disables a component until the returned code runs; nested blocks of one component restore it once.
     *
     * @param component the component
     * @return re-enables the component
     */
    public Runnable disable(Component component) {
        Integer count = disabled.get(component);
        if (count == null) {
            disabled.put(component, component.isEnabled() ? 1 : -1);
            component.setEnabled(false);
        } else {
            disabled.put(component, count + Integer.signum(count));
        }
        return () -> enable(component);
    }

    private void enable(Component component) {
        Integer count = disabled.get(component);
        if (count == null) {
            return;
        }
        if (Math.abs(count) == 1) {
            disabled.remove(component);
            if (count > 0) {
                component.setEnabled(true);
            }
        } else {
            disabled.put(component, count - Integer.signum(count));
        }
    }

    /**
     * Covers windows with a blocking glass pane while a task runs.
     *
     * @param windows the windows' root panes
     * @param task the task
     * @return removes the glass panes
     */
    public Runnable cover(List<JRootPane> windows, RunningTask task) {
        windows.forEach(rootPane -> coverWindow(rootPane, task));
        return () -> windows.forEach(rootPane -> uncoverWindow(rootPane, task));
    }

    /**
     * Covers every window with a blocking glass pane while a task runs, the windows opened while it runs included.
     *
     * @param windows the root panes of the open windows
     * @param task the task
     * @return removes the glass panes
     */
    public Runnable coverApplication(List<JRootPane> windows, RunningTask task) {
        ApplicationBlock block = new ApplicationBlock(task, new ArrayList<>(windows));
        applicationBlocks.add(block);
        Runnable uncover = cover(block.windows, task);
        return () -> {
            applicationBlocks.remove(block);
            uncover.run();
        };
    }

    /**
     * Covers a window that was just opened, for each application-wide block active.
     *
     * @param rootPane the window's root pane
     */
    public void windowOpened(JRootPane rootPane) {
        for (ApplicationBlock block : applicationBlocks) {
            coverWindow(rootPane, block.task);
            block.windows.add(rootPane);
        }
    }

    private void coverWindow(JRootPane rootPane, RunningTask task) {
        if (glassPanes.isEmpty()) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
        }
        Installed installed = glassPanes.computeIfAbsent(rootPane, this::install);
        installed.count++;
        installed.pane.block(task);
    }

    private void uncoverWindow(JRootPane rootPane, RunningTask task) {
        Installed installed = glassPanes.get(rootPane);
        if (installed == null) {
            return;
        }
        installed.pane.unblock(task);
        if (--installed.count == 0) {
            glassPanes.remove(rootPane);
            rootPane.setGlassPane(installed.previous);
            installed.previous.setVisible(false);
            if (glassPanes.isEmpty()) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
            }
        }
    }

    // Consumes a key event whose window is blocked, unless it is for the glass pane's own Cancel button.
    private boolean consumeIfBlocked(KeyEvent event) {
        @Nullable Component source = event.getComponent();
        if (source == null) {
            return false;
        }
        Window window = source instanceof Window own ? own : SwingUtilities.getWindowAncestor(source);
        @Nullable
        JRootPane rootPane = window instanceof RootPaneContainer container
                ? container.getRootPane()
                : SwingUtilities.getRootPane(source);
        if (rootPane == null
                || !glassPanes.containsKey(rootPane)
                || SwingUtilities.getAncestorOfClass(BlockingGlassPane.class, source) != null) {
            return false;
        }
        event.consume();
        return true;
    }

    private Installed install(JRootPane rootPane) {
        Component previous = rootPane.getGlassPane();
        BlockingGlassPane pane = new BlockingGlassPane(ticker, texts.cancel());
        rootPane.setGlassPane(pane);
        return new Installed(pane, previous);
    }

    /**
     * Returns the blocking glass pane of a window, while it is blocked.
     *
     * @param rootPane the window's root pane
     * @return the glass pane, or {@code null} if the window is not blocked
     */
    public @Nullable BlockingGlassPane glassPaneOf(JRootPane rootPane) {
        Installed installed = glassPanes.get(rootPane);
        return installed == null ? null : installed.pane;
    }

    private static final class Installed {
        private final BlockingGlassPane pane;
        private final Component previous;
        private int count;

        Installed(BlockingGlassPane pane, Component previous) {
            this.pane = pane;
            this.previous = previous;
        }
    }

    // An application-wide block: its task, and the windows it covers so far.
    private static final class ApplicationBlock {
        private final RunningTask task;
        private final List<JRootPane> windows;

        ApplicationBlock(RunningTask task, List<JRootPane> windows) {
            this.task = task;
            this.windows = windows;
        }
    }
}
