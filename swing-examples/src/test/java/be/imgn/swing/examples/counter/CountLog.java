package be.imgn.swing.examples.counter;

import be.imgn.gaia.core.event.OnVirtualThread;
import be.imgn.inject.Eager;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the counts announced on the application's bus. It receives them on virtual threads, in no particular order and
 * possibly several at once, so it keeps them in a thread-safe list.
 */
@Singleton
@Eager
final class CountLog {

    private final List<Integer> counts = new CopyOnWriteArrayList<>();

    @Inject
    CountLog() {}

    @OnVirtualThread
    void onCounted(Counted event) {
        counts.add(event.count());
    }

    List<Integer> counts() {
        return List.copyOf(counts);
    }
}
