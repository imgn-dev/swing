package be.imgn.swing.examples.counter;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.OnEdt;
import be.imgn.swing.ui.WindowScoped;
import java.awt.EventQueue;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Echoes the counts announced on the application's bus in its window, on the EDT, where a window's components live. */
@WindowScoped
final class CountEcho {

    private final List<String> echoes = new CopyOnWriteArrayList<>();

    @Inject
    CountEcho() {}

    @OnEdt
    void onCounted(Counted event) {
        echoes.add(event.count() + (EventQueue.isDispatchThread() ? " on the EDT" : " off the EDT"));
    }

    List<String> echoes() {
        return List.copyOf(echoes);
    }
}
