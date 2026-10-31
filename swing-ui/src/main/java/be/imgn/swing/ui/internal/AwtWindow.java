package be.imgn.swing.ui.internal;

import be.imgn.swing.ui.Window;

/** A {@link Window} that wraps a real AWT window: a frame or a dialog. */
public interface AwtWindow extends Window {

    /**
     * Returns the AWT window.
     *
     * @return the frame or the dialog
     */
    java.awt.Window awt();
}
