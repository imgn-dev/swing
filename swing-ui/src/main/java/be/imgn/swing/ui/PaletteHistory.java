package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/** The commands run from the palette, the most recent first, by identity, for the rest of the run. On the EDT. */
@Singleton
final class PaletteHistory {

    private final List<String> recent = new ArrayList<>();

    @Inject
    PaletteHistory() {}

    void ran(BoundCommand command) {
        recent.remove(command.identity());
        recent.addFirst(command.identity());
    }

    // The place of a command in the history, the most recent first; one never run comes after every other.
    int place(BoundCommand command) {
        int index = recent.indexOf(command.identity());
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
