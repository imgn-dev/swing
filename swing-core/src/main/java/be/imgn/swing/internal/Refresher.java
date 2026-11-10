package be.imgn.swing.internal;

/**
 * Refreshes the open windows once what resources give has changed: the locale, the appearance, or the files. Core's
 * default does nothing, since core has no window; the UI module replaces it.
 */
public interface Refresher {

    /** Refreshes the open windows, on the UI thread. */
    void refresh();
}
