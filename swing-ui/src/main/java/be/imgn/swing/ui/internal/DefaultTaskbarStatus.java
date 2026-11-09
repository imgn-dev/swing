package be.imgn.swing.ui.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.swing.UiExecutor;
import be.imgn.swing.ui.TaskbarStatus;
import java.awt.Taskbar.Feature;
import java.awt.Window;
import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** The real {@link TaskbarStatus}, on {@code java.awt.Taskbar}, through an adaptor that tests replace. */
public final class DefaultTaskbarStatus implements TaskbarStatus {

    private static final System.Logger LOGGER = System.getLogger(DefaultTaskbarStatus.class.getName());
    private static final int PERCENT = 100;

    private final TaskbarAdaptor taskbar;
    private final UiExecutor executor;
    private final Supplier<Optional<Window>> activeFrame;
    private final Set<Feature> lacking = EnumSet.noneOf(Feature.class);

    /**
     * Creates the taskbar status.
     *
     * @param taskbar the taskbar
     * @param executor the executor that runs the calls on the EDT
     * @param activeFrame the window of the active frame, for the features of windows
     */
    public DefaultTaskbarStatus(TaskbarAdaptor taskbar, UiExecutor executor, Supplier<Optional<Window>> activeFrame) {
        this.taskbar = taskbar;
        this.executor = executor;
        this.activeFrame = activeFrame;
    }

    @Override
    public void progress(int percent) {
        if (percent < 0 || percent > PERCENT) {
            throw new IllegalArgumentException("The progress must be between 0 and 100: " + percent);
        }
        executor.execute(() -> setProgress(percent));
    }

    @Override
    public void clearProgress() {
        executor.execute(() -> setProgress(-1));
    }

    private void setProgress(int value) {
        if (taskbar.isSupported(Feature.PROGRESS_VALUE)) {
            taskbar.setProgressValue(value);
        } else if (taskbar.isSupported(Feature.PROGRESS_VALUE_WINDOW)) {
            activeFrame.get().ifPresent(window -> taskbar.setWindowProgressValue(window, value));
        } else {
            lacks(Feature.PROGRESS_VALUE);
        }
    }

    @Override
    public void badge(String text) {
        requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("The badge must not be blank");
        }
        executor.execute(() -> setBadge(text));
    }

    @Override
    public void clearBadge() {
        executor.execute(() -> setBadge(null));
    }

    private void setBadge(@Nullable String text) {
        if (taskbar.isSupported(Feature.ICON_BADGE_TEXT)) {
            taskbar.setIconBadge(text);
        } else {
            lacks(Feature.ICON_BADGE_TEXT);
        }
    }

    @Override
    public void requestAttention() {
        executor.execute(() -> {
            if (taskbar.isSupported(Feature.USER_ATTENTION)) {
                taskbar.requestUserAttention();
            } else if (taskbar.isSupported(Feature.USER_ATTENTION_WINDOW)) {
                activeFrame.get().ifPresent(taskbar::requestWindowUserAttention);
            } else {
                lacks(Feature.USER_ATTENTION);
            }
        });
    }

    private synchronized void lacks(Feature feature) {
        if (lacking.add(feature)) {
            LOGGER.log(Level.DEBUG, "The system's taskbar doesn't support " + feature + ": the call does nothing");
        }
    }
}
