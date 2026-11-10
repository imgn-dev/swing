package be.imgn.swing;

/**
 * The appearance of the user interface. A singleton.
 *
 * <p>Core's own is always {@link Appearance#LIGHT}, which applies with the system look and feel; {@code swing-flatlaf}
 * provides one that follows the theme, and the system's setting while the application runs. A change switches the
 * {@link Resources} to the new appearance, installs the look and feel again, refreshes the open windows, then changes
 * {@link #current()}, on the EDT.
 */
public interface Appearances {

    /**
     * Returns the appearance.
     *
     * @return the observable appearance
     */
    Observable<Appearance> current();
}
