package be.imgn.swing.ui.internal;

import be.imgn.swing.UiExecutor;
import java.awt.EventQueue;

/** The UI module's {@link UiExecutor}: the EDT. */
public final class EdtExecutor implements UiExecutor {

    /** Creates the executor. */
    public EdtExecutor() {}

    @Override
    public void execute(Runnable task) {
        EventQueue.invokeLater(task);
    }

    @Override
    public boolean isUiThread() {
        return EventQueue.isDispatchThread();
    }
}
