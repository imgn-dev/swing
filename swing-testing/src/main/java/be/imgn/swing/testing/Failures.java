package be.imgn.swing.testing;

import java.util.ArrayList;
import java.util.List;

/** The failures a tester records, thrown at its next call. */
final class Failures {

    private final List<Throwable> failures = new ArrayList<>();

    synchronized void add(Throwable failure) {
        failures.add(failure);
    }

    /** Throws the recorded failures, the first with the others suppressed, and forgets them. */
    void throwIfAny() {
        List<Throwable> recorded;
        synchronized (this) {
            recorded = List.copyOf(failures);
            failures.clear();
        }
        if (recorded.isEmpty()) {
            return;
        }
        AssertionError error =
                new AssertionError("The application failed: " + recorded.getFirst(), recorded.getFirst());
        recorded.stream().skip(1).forEach(error::addSuppressed);
        throw error;
    }
}
