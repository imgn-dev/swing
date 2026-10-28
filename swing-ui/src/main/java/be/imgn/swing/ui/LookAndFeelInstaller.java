package be.imgn.swing.ui;

/**
 * Installs the look and feel, on the EDT, before any component is created. {@code swing-ui} provides a default that
 * installs the system look and feel; {@code swing-flatlaf} provides one that installs FlatLaf.
 */
public interface LookAndFeelInstaller {

    /** Installs the look and feel. */
    void install();
}
