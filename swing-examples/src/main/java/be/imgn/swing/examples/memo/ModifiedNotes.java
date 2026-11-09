package be.imgn.swing.examples.memo;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.desktop.Document;
import be.imgn.swing.ui.TaskbarStatus;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Counts the modified notes on the taskbar's badge, while there is at least one. Used on the EDT. */
@Singleton
final class ModifiedNotes {

    private final TaskbarStatus taskbar;
    private final Set<Document> modified = Collections.newSetFromMap(new IdentityHashMap<>());

    @Inject
    ModifiedNotes(TaskbarStatus taskbar) {
        this.taskbar = taskbar;
    }

    void changed(Document note, boolean isModified) {
        if (isModified) {
            modified.add(note);
        } else {
            modified.remove(note);
        }
        show();
    }

    void closed(Document note) {
        modified.remove(note);
        show();
    }

    private void show() {
        if (modified.isEmpty()) {
            taskbar.clearBadge();
        } else {
            taskbar.badge(Integer.toString(modified.size()));
        }
    }
}
