package be.imgn.swing.internal;

/**
 * A {@link be.imgn.swing.UiExecutor} that can wait until the work it was given has settled, as the test harness's does:
 * the running tasks have ended, and the updates posted to the EDT have run.
 */
public interface Settling {

    /** Waits until the running tasks have ended and the posted updates have run. Called off the EDT. */
    void settle();
}
