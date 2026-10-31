package be.imgn.swing.testing;

import be.imgn.swing.ui.Window;
import be.imgn.swing.ui.Windows;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.JRootPane;

/**
 * The tester's {@link Windows}: each window wraps a bare root pane, which is lightweight, so frames and dialogs are
 * never realized. Showing a window lays it out; showing a dialog then answers it.
 */
final class HeadlessWindows implements Windows {

    private final Consumer<HeadlessWindow> dialogs;

    HeadlessWindows(Consumer<HeadlessWindow> dialogs) {
        this.dialogs = dialogs;
    }

    @Override
    public Window createFrame() {
        return new HeadlessWindow(false);
    }

    @Override
    public Window createDialog(Window owner) {
        return new HeadlessWindow(true);
    }

    @Override
    public void show(Window window) {
        HeadlessWindow headless = (HeadlessWindow) window;
        JRootPane rootPane = headless.rootPane();
        Dimension size = rootPane.getPreferredSize();
        rootPane.setSize(size);
        rootPane.validate();
        headless.shown = true;
        if (headless.dialog) {
            dialogs.accept(headless);
        }
    }

    @Override
    public void dispose(Window window) {
        ((HeadlessWindow) window).shown = false;
    }

    /** A window that wraps a bare root pane. */
    static final class HeadlessWindow implements Window {
        private final JRootPane rootPane = new JRootPane();
        private final boolean dialog;
        private String title = "";
        private boolean shown;

        HeadlessWindow(boolean dialog) {
            this.dialog = dialog;
        }

        @Override
        public JRootPane rootPane() {
            return rootPane;
        }

        @Override
        public void setTitle(String newTitle) {
            this.title = newTitle;
        }

        String title() {
            return title;
        }

        boolean isShown() {
            return shown;
        }
    }
}
