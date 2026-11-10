package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/** Records what the tests' views went through, and tells whether the vetoing view may close. */
@Singleton
final class DockLog {

    private final List<String> events = new ArrayList<>();
    private boolean closable = true;

    @Inject
    DockLog() {}

    void add(String event) {
        events.add(event);
    }

    List<String> events() {
        return List.copyOf(events);
    }

    boolean closable() {
        return closable;
    }

    void closable(boolean value) {
        this.closable = value;
    }
}
