package be.imgn.swing.examples.added;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/** Records what the tool views went through, and tells whether they may close. */
@Singleton
final class ToolLog {

    private final List<String> events = new ArrayList<>();
    private int created;
    private boolean closable = true;

    @Inject
    ToolLog() {}

    int created() {
        return ++created;
    }

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
