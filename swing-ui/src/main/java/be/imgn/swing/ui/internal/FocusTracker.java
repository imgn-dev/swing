package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.PreDestroy;
import be.imgn.inject.Singleton;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.TransferHandler;
import javax.swing.event.CaretListener;
import javax.swing.text.JTextComponent;
import org.jspecify.annotations.Nullable;

/**
 * Follows the permanent focus owner and its selection, for the standard {@code cut}, {@code copy}, {@code paste} and
 * {@code selectAll} commands, which act on it.
 */
@Singleton
public final class FocusTracker {

    private final Property<Focus> focus = Property.of(new Focus(null, 0));
    private final PropertyChangeListener focusListener = this::focusChanged;
    private final CaretListener caretListener = event -> bump();
    private @Nullable JTextComponent caretSource;
    private boolean listening;

    @Inject
    FocusTracker() {}

    /** Starts following the focus; called on the EDT. Idempotent, and {@link #stop()} allows a new start. */
    public synchronized void start() {
        if (listening) {
            return;
        }
        listening = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("permanentFocusOwner", focusListener);
    }

    /**
     * Stops following the focus, and forgets the focused component: the root injector closing calls it, on the EDT.
     * Idempotent.
     */
    @PreDestroy
    public synchronized void stop() {
        if (!listening) {
            return;
        }
        listening = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("permanentFocusOwner", focusListener);
        follow(null);
    }

    private void focusChanged(PropertyChangeEvent event) {
        follow(event.getNewValue() instanceof Component component ? component : null);
    }

    /**
     * Follows a component as the focus owner; the focus manager's events call it.
     *
     * @param owner the new focus owner, or {@code null}
     */
    public void follow(@Nullable Component owner) {
        if (caretSource != null) {
            caretSource.removeCaretListener(caretListener);
            caretSource = null;
        }
        if (owner instanceof JTextComponent text) {
            text.addCaretListener(caretListener);
            caretSource = text;
        }
        focus.set(new Focus(owner, focus.get().version() + 1));
    }

    private void bump() {
        Focus current = focus.get();
        focus.set(new Focus(current.owner(), current.version() + 1));
    }

    /**
     * Returns whether the focused component can cut.
     *
     * @return the observable state
     */
    public Observable<Boolean> canCut() {
        return focus.map(current -> current.owner() instanceof JTextComponent text
                ? text.isEditable() && hasSelection(text) && !(text instanceof JPasswordField)
                : supports(current.owner(), TransferHandler.getCutAction(), TransferHandler.MOVE));
    }

    /**
     * Returns whether the focused component can copy.
     *
     * @return the observable state
     */
    public Observable<Boolean> canCopy() {
        return focus.map(current -> current.owner() instanceof JTextComponent text
                ? hasSelection(text) && !(text instanceof JPasswordField)
                : supports(current.owner(), TransferHandler.getCopyAction(), TransferHandler.COPY));
    }

    /**
     * Returns whether the focused component can paste.
     *
     * @return the observable state
     */
    public Observable<Boolean> canPaste() {
        return focus.map(current -> current.owner() instanceof JTextComponent text
                ? text.isEditable() && text.isEnabled()
                : supports(current.owner(), TransferHandler.getPasteAction(), TransferHandler.NONE));
    }

    /**
     * Returns whether the focused component can select all.
     *
     * @return the observable state
     */
    public Observable<Boolean> canSelectAll() {
        return focus.map(current -> selectAllAction(current.owner()) != null);
    }

    /**
     * Cuts, copies or pastes in the focused component, through its transfer handler's action.
     *
     * @param action {@link TransferHandler#getCutAction()}, {@link TransferHandler#getCopyAction()} or
     *     {@link TransferHandler#getPasteAction()}
     */
    public void transfer(Action action) {
        Component owner = focus.get().owner();
        if (owner != null) {
            action.actionPerformed(new ActionEvent(owner, ActionEvent.ACTION_PERFORMED, name(action)));
        }
    }

    /** Selects all in the focused component, through its action map. */
    public void selectAll() {
        Component owner = focus.get().owner();
        Action action = selectAllAction(owner);
        if (owner != null && action != null) {
            action.actionPerformed(new ActionEvent(owner, ActionEvent.ACTION_PERFORMED, "selectAll"));
        }
    }

    private static String name(Action action) {
        return Objects.toString(action.getValue(Action.NAME), "");
    }

    private static boolean hasSelection(JTextComponent text) {
        return text.getSelectionStart() != text.getSelectionEnd();
    }

    private static boolean supports(@Nullable Component owner, Action action, int sourceAction) {
        if (!(owner instanceof JComponent component) || !component.isEnabled()) {
            return false;
        }
        TransferHandler handler = component.getTransferHandler();
        if (handler == null || component.getActionMap().get(name(action)) == null) {
            return false;
        }
        return sourceAction == TransferHandler.NONE || (handler.getSourceActions(component) & sourceAction) != 0;
    }

    private static @Nullable Action selectAllAction(@Nullable Component owner) {
        if (!(owner instanceof JComponent component) || !component.isEnabled()) {
            return null;
        }
        Action action = component.getActionMap().get(owner instanceof JTextComponent ? "select-all" : "selectAll");
        return action != null && action.isEnabled() ? action : null;
    }

    /**
     * The focus owner, with a version that changes with its selection.
     *
     * @param owner the focus owner, or {@code null}
     * @param version the version
     */
    private record Focus(@Nullable Component owner, long version) {}
}
