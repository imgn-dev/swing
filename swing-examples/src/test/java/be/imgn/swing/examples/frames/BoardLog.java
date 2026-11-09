package be.imgn.swing.examples.frames;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/** Records what the boards and the application went through, in order. */
@Singleton
final class BoardLog {

    private final List<String> events = new ArrayList<>();

    @Inject
    BoardLog() {}

    void add(String event) {
        events.add(event);
    }

    List<String> events() {
        return List.copyOf(events);
    }
}
