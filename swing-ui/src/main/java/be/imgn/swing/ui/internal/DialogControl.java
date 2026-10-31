package be.imgn.swing.ui.internal;

/** How a dialog view's {@code ok()} and {@code cancel()} close its dialog. */
public final class DialogControl {

    private final Runnable close;
    private boolean confirmed;
    private boolean closed;

    /**
     * Creates the control of a dialog.
     *
     * @param close closes the dialog's window
     */
    public DialogControl(Runnable close) {
        this.close = close;
    }

    /** Closes the dialog with a result. */
    public void ok() {
        close(true);
    }

    /** Closes the dialog without a result. */
    public void cancel() {
        close(false);
    }

    private void close(boolean withResult) {
        if (closed) {
            return;
        }
        closed = true;
        confirmed = withResult;
        close.run();
    }

    /**
     * Returns whether the dialog was closed with {@code ok()}.
     *
     * @return {@code true} after {@code ok()}
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Returns whether the dialog was closed.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        return closed;
    }
}
