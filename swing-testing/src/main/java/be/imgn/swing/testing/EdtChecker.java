package be.imgn.swing.testing;

import java.awt.EventQueue;
import javax.swing.JComponent;
import javax.swing.RepaintManager;

/** A {@link RepaintManager} that records a failure when a component is repainted or invalidated off the EDT. */
final class EdtChecker extends RepaintManager {

    private final Failures failures;

    EdtChecker(Failures failures) {
        this.failures = failures;
    }

    @Override
    public synchronized void addInvalidComponent(JComponent invalidComponent) {
        check(invalidComponent, "invalidated");
        super.addInvalidComponent(invalidComponent);
    }

    @Override
    public void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
        check(component, "repainted");
        super.addDirtyRegion(component, x, y, width, height);
    }

    private void check(JComponent component, String what) {
        if (!EventQueue.isDispatchThread()) {
            failures.add(new AssertionError("A " + component.getClass().getName() + " was " + what + " off the EDT,"
                    + " on the thread " + Thread.currentThread().getName()));
        }
    }
}
