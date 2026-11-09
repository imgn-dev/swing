package be.imgn.swing.ui.internal;

import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import org.jspecify.annotations.Nullable;

/** The calls the framework makes on {@link Taskbar}, which tests replace with a fake. */
public interface TaskbarAdaptor {

    /**
     * Returns whether the system supports a feature.
     *
     * @param feature the feature
     * @return {@code true} if supported
     */
    boolean isSupported(Taskbar.Feature feature);

    /**
     * Calls {@link Taskbar#setProgressValue(int)}.
     *
     * @param value the progress, or -1 to remove it
     */
    void setProgressValue(int value);

    /**
     * Calls {@link Taskbar#setWindowProgressValue(Window, int)}.
     *
     * @param window the window
     * @param value the progress, or -1 to remove it
     */
    void setWindowProgressValue(Window window, int value);

    /**
     * Calls {@link Taskbar#setIconBadge(String)}.
     *
     * @param badge the badge, or {@code null} to remove it
     */
    void setIconBadge(@Nullable String badge);

    /** Calls {@link Taskbar#requestUserAttention(boolean, boolean)}, once and not critical. */
    void requestUserAttention();

    /**
     * Calls {@link Taskbar#requestWindowUserAttention(Window)}.
     *
     * @param window the window
     */
    void requestWindowUserAttention(Window window);

    /**
     * Calls {@link Taskbar#setIconImage(Image)}.
     *
     * @param image the image
     */
    void setIconImage(Image image);

    /**
     * Returns the adaptor of the system's taskbar, which supports nothing when the system has none.
     *
     * @return the adaptor
     */
    static TaskbarAdaptor system() {
        if (!Taskbar.isTaskbarSupported()) {
            return new TaskbarAdaptor() {
                @Override
                public boolean isSupported(Taskbar.Feature feature) {
                    return false;
                }

                @Override
                public void setProgressValue(int value) {}

                @Override
                public void setWindowProgressValue(Window window, int value) {}

                @Override
                public void setIconBadge(@Nullable String badge) {}

                @Override
                public void requestUserAttention() {}

                @Override
                public void requestWindowUserAttention(Window window) {}

                @Override
                public void setIconImage(Image image) {}
            };
        }
        Taskbar taskbar = Taskbar.getTaskbar();
        return new TaskbarAdaptor() {
            @Override
            public boolean isSupported(Taskbar.Feature feature) {
                return taskbar.isSupported(feature);
            }

            @Override
            public void setProgressValue(int value) {
                taskbar.setProgressValue(value);
            }

            @Override
            public void setWindowProgressValue(Window window, int value) {
                taskbar.setWindowProgressValue(window, value);
            }

            @Override
            public void setIconBadge(@Nullable String badge) {
                taskbar.setIconBadge(badge);
            }

            @Override
            public void requestUserAttention() {
                taskbar.requestUserAttention(true, false);
            }

            @Override
            public void requestWindowUserAttention(Window window) {
                taskbar.requestWindowUserAttention(window);
            }

            @Override
            public void setIconImage(Image image) {
                taskbar.setIconImage(image);
            }
        };
    }
}
