package be.imgn.swing.ui;

import static java.util.Objects.requireNonNull;

import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/**
 * The undo stack of a view, a bean of its scope, which the standard {@code undo()} and {@code redo()} commands act on.
 * The text components bound with {@link Form#bind} add their edits to it; other components add their own, such as a
 * drawing that adds an edit per stroke. It is built on Swing's {@link UndoManager}, with its default limit of 100
 * edits. Every method is called on the EDT.
 */
@ViewScoped
public final class UndoStack {

    private final UndoManager manager = new UndoManager();
    private final Property<Boolean> canUndo = Property.of(false);
    private final Property<Boolean> canRedo = Property.of(false);

    @Inject
    UndoStack() {}

    /**
     * Adds an edit, which discards the edits that were undone.
     *
     * @param edit the edit
     * @throws NullPointerException if {@code edit} is {@code null}
     */
    public void add(UndoableEdit edit) {
        manager.addEdit(requireNonNull(edit, "edit"));
        update();
    }

    /**
     * Returns whether an edit can be undone.
     *
     * @return the observable, {@code true} while an edit can be undone
     */
    public Observable<Boolean> canUndo() {
        return canUndo;
    }

    /**
     * Returns whether an edit can be redone.
     *
     * @return the observable, {@code true} while an edit can be redone
     */
    public Observable<Boolean> canRedo() {
        return canRedo;
    }

    /**
     * Undoes the next edit.
     *
     * @throws IllegalStateException if there is none
     */
    public void undo() {
        if (!manager.canUndo()) {
            throw new IllegalStateException("There is no edit to undo");
        }
        manager.undo();
        update();
    }

    /**
     * Redoes the next edit.
     *
     * @throws IllegalStateException if there is none
     */
    public void redo() {
        if (!manager.canRedo()) {
            throw new IllegalStateException("There is no edit to redo");
        }
        manager.redo();
        update();
    }

    /** Discards every edit. */
    public void clear() {
        manager.discardAllEdits();
        update();
    }

    private void update() {
        canUndo.set(manager.canUndo());
        canRedo.set(manager.canRedo());
    }
}
