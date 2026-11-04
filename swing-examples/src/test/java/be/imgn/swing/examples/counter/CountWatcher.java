package be.imgn.swing.examples.counter;

import be.imgn.gaia.core.event.Subscribe;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.ViewScoped;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Hears the events of the counter while its view is open: the counts announced, and the openings of count dialogs. */
@ViewScoped
final class CountWatcher {

    private final List<Object> heard = new CopyOnWriteArrayList<>();

    @Inject
    CountWatcher() {}

    @Subscribe
    void onCounted(Counted event) {
        heard.add(event);
    }

    @Subscribe
    void onOpened(CountDialog.Opened event) {
        heard.add(event);
    }

    List<Object> heard() {
        return List.copyOf(heard);
    }
}
