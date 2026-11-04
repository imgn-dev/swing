package be.imgn.swing.examples.counter;

import be.imgn.gaia.core.event.OnVirtualThread;
import be.imgn.inject.Inject;
import be.imgn.inject.PreDestroy;
import be.imgn.swing.ui.ViewScoped;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Holds each count announced on the application's bus, on a virtual thread, until its view closes, then keeps it:
 * closing a view doesn't cancel a delivery to its instances that is running.
 */
@ViewScoped
final class CountHolder {

    private static final long TIMEOUT_SECONDS = 10;

    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch closed = new CountDownLatch(1);
    private final List<String> kept = new CopyOnWriteArrayList<>();

    @Inject
    CountHolder() {}

    @OnVirtualThread
    void onCounted(Counted event) {
        started.countDown();
        try {
            kept.add(event.count() + (closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) ? " after" : " before")
                    + " its view closed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kept.add(event.count() + " interrupted");
        }
    }

    @PreDestroy
    void viewClosed() {
        closed.countDown();
    }

    boolean awaitStarted() throws InterruptedException {
        return started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    List<String> kept() {
        return List.copyOf(kept);
    }
}
