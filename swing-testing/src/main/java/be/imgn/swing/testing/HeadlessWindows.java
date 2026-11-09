package be.imgn.swing.testing;

import static java.util.Objects.requireNonNull;

import be.imgn.swing.ui.Window;
import be.imgn.swing.ui.Windows;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JRootPane;
import org.jspecify.annotations.Nullable;

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
        private @Nullable Image iconImage;
        private @Nullable Component focused;
        private int toFrontCount;

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

        @Override
        public void setIconImage(Image image) {
            this.iconImage = requireNonNull(image, "image");
        }

        // A window that is never realized has no focus to move: the last component asked for is recorded instead.
        @Override
        public void focus(Component component) {
            this.focused = requireNonNull(component, "component");
        }

        // A window that is never realized has no front: the requests are counted instead.
        @Override
        public void toFront() {
            toFrontCount++;
        }

        int toFrontCount() {
            return toFrontCount;
        }

        String title() {
            return title;
        }

        Optional<Image> iconImage() {
            return Optional.ofNullable(iconImage);
        }

        Optional<Component> focused() {
            return Optional.ofNullable(focused);
        }

        boolean isShown() {
            return shown;
        }
    }
}
