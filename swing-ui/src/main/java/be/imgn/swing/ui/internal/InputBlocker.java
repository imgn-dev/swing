package be.imgn.swing.ui.internal;

import be.imgn.gaia.core.time.Ticker;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.internal.RunningTask;
import java.awt.Component;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.JRootPane;
import org.jspecify.annotations.Nullable;

/** Blocks components and windows while tasks run. Used on the EDT. */
@Singleton
public final class InputBlocker {

    private final Ticker ticker;
    private final UiTexts texts;
    private final IdentityHashMap<Component, Integer> disabled = new IdentityHashMap<>();
    private final IdentityHashMap<JRootPane, Installed> glassPanes = new IdentityHashMap<>();

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
        for (JRootPane rootPane : windows) {
            Installed installed = glassPanes.computeIfAbsent(rootPane, this::install);
            installed.count++;
            installed.pane.block(task);
        }
        return () -> {
            for (JRootPane rootPane : windows) {
                Installed installed = glassPanes.get(rootPane);
                if (installed != null) {
                    installed.pane.unblock(task);
                    if (--installed.count == 0) {
                        glassPanes.remove(rootPane);
                        rootPane.setGlassPane(installed.previous);
                        installed.previous.setVisible(false);
                    }
                }
            }
        };
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
}
