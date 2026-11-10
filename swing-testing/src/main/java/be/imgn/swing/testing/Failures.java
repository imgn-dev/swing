package be.imgn.swing.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** The failures a tester records, thrown at its next call. */
final class Failures {

    private final List<Throwable> failures = new ArrayList<>();
    // The strict-mode failures UiMode reported, thrown or not: each arrives a second time, from the handler of the
    // thread
    // it is thrown on, which may run after a call already threw it.
    private final Set<Throwable> reported = Collections.newSetFromMap(new IdentityHashMap<>());

    synchronized void add(Throwable failure) {
        if (!failures.contains(failure)) {
            failures.add(failure);
        }
    }

    /**
     * Records a strict-mode failure that UiMode reports, before it is thrown.
     *
     * @param failure the failure
     */
    synchronized void addReported(Throwable failure) {
        reported.add(failure);
        add(failure);
    }

    /**
     * Records a failure that reached a thread's handler, unless UiMode reported it already.
     *
     * @param failure the failure
     */
    synchronized void addUncaught(Throwable failure) {
        if (!reported.contains(failure)) {
            add(failure);
        }
    }

    /** Throws the recorded failures, the first with the others suppressed, and forgets them. */
    void throwIfAny() {
        List<Throwable> pending;
        synchronized (this) {
            pending = List.copyOf(failures);
            failures.clear();
        }
        if (pending.isEmpty()) {
            return;
        }
        AssertionError error = new AssertionError("The application failed: " + pending.getFirst(), pending.getFirst());
        pending.stream().skip(1).forEach(error::addSuppressed);
        throw error;
    }
}
