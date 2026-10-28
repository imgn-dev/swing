package be.imgn.swing.ui;

import javax.swing.JRootPane;

/** A window, as the framework sees it: a frame or a dialog, or a bare root pane in the test harness. */
public interface Window {

    /**
     * Returns the root pane of the window.
     *
     * @return the root pane
     */
    JRootPane rootPane();

    /**
     * Sets the title of the window.
     *
     * @param title the title
     */
    void setTitle(String title);
}
