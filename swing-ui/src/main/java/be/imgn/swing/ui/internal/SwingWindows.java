package be.imgn.swing.ui.internal;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.Window;
import be.imgn.swing.ui.Windows;
import java.awt.Dialog;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/** The real {@link Windows}: frames and modal dialogs. */
@Singleton
@ExposeAs(Windows.class)
public final class SwingWindows implements Windows {

    @Inject
    SwingWindows() {}

    @Override
    public Window createFrame() {
        return new FrameWindow(new JFrame());
    }

    @Override
    public Window createDialog(Window owner) {
        java.awt.Window ownerWindow =
                owner instanceof AwtWindow awt ? awt.awt() : SwingUtilities.getWindowAncestor(owner.rootPane());
        return new DialogWindow(new JDialog(ownerWindow, Dialog.ModalityType.DOCUMENT_MODAL));
    }

    @Override
    public void show(Window window) {
        ((AwtWindow) window).awt().setVisible(true);
    }

    @Override
    public void dispose(Window window) {
        ((AwtWindow) window).awt().dispose();
    }

    /** A frame. */
    public static final class FrameWindow implements AwtWindow {
        private final JFrame frame;

        FrameWindow(JFrame frame) {
            this.frame = frame;
        }

        /**
         * Returns the frame.
         *
         * @return the frame
         */
        public JFrame frame() {
            return frame;
        }

        @Override
        public java.awt.Window awt() {
            return frame;
        }

        @Override
        public JRootPane rootPane() {
            return frame.getRootPane();
        }

        @Override
        public void setTitle(String title) {
            frame.setTitle(title);
        }
    }

    /** A modal dialog. */
    public static final class DialogWindow implements AwtWindow {
        private final JDialog dialog;

        DialogWindow(JDialog dialog) {
            this.dialog = dialog;
        }

        @Override
        public java.awt.Window awt() {
            return dialog;
        }

        @Override
        public JRootPane rootPane() {
            return dialog.getRootPane();
        }

        @Override
        public void setTitle(String title) {
            dialog.setTitle(title);
        }
    }
}
