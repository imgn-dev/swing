package be.imgn.swing.ui;

/**
 * The application's entry in the taskbar, or its Dock icon: a progress, a badge, and requests for the user's attention.
 * Its methods may be called from any thread, and are applied on the EDT. Each uses the features of
 * {@code java.awt.Taskbar} that the system supports, and does nothing when it supports none.
 *
 * <p>While a task blocks the application ({@code Block.APPLICATION}), the taskbar shows its progress.
 *
 * <p>The UI module provides it as a singleton; the test harness replaces it with a fake that records the state.
 */
public interface TaskbarStatus {

    /**
     * Shows a progress, through {@code PROGRESS_VALUE}, else {@code PROGRESS_VALUE_WINDOW} on the active frame.
     *
     * @param percent the progress, from 0 to 100
     * @throws IllegalArgumentException if {@code percent} is outside 0 to 100
     */
    void progress(int percent);

    /** Removes the progress. */
    void clearProgress();

    /**
     * Shows a short text, such as a count, through {@code ICON_BADGE_TEXT}.
     *
     * @param text the text
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if {@code text} is blank
     */
    void badge(String text);

    /** Removes the badge. */
    void clearBadge();

    /**
     * Asks the system to draw the user's attention to the application, such as bouncing the Dock icon once, through
     * {@code USER_ATTENTION}, else {@code USER_ATTENTION_WINDOW} on the active frame.
     */
    void requestAttention();
}
