package be.imgn.swing.testing;

import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Dialogs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The tester's {@link Dialogs}: records the dialogs shown and answers them. Confirmations are answered {@code true}
 * until {@link #answerConfirm(boolean)} says otherwise. A dialog view is answered by the action registered for its
 * class with {@link #answerDialog(Class, Consumer)}, and cancelled when none is.
 */
public final class FakeDialogs implements Dialogs {

    private final List<String> confirmed = new ArrayList<>();
    private final List<String> informed = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<Class<?>> shown = new ArrayList<>();
    private final Map<Class<?>, Consumer<OpenDialog<?>>> answers = new LinkedHashMap<>();
    private boolean confirmation = true;

    FakeDialogs() {}

    /**
     * Sets the answer of the next confirmations.
     *
     * @param answer {@code true} to confirm, {@code false} to refuse
     */
    public synchronized void answerConfirm(boolean answer) {
        this.confirmation = answer;
    }

    /**
     * Registers the answer of a dialog view: an action run on the EDT once the dialog is built, which closes it with
     * {@code ok()} or {@code cancel()}, through its commands. A dialog left open is cancelled.
     *
     * @param type the dialog view class
     * @param answer the action
     * @param <V> the dialog view class
     */
    @SuppressWarnings("unchecked")
    public synchronized <V extends DialogView<?>> void answerDialog(Class<V> type, Consumer<OpenDialog<V>> answer) {
        answers.put(type, dialog -> answer.accept((OpenDialog<V>) dialog));
    }

    /**
     * Returns the messages of the confirmations asked, in order.
     *
     * @return the messages
     */
    public synchronized List<String> confirmed() {
        return List.copyOf(confirmed);
    }

    /**
     * Returns the messages shown, in order.
     *
     * @return the messages
     */
    public synchronized List<String> informed() {
        return List.copyOf(informed);
    }

    /**
     * Returns the messages of the errors shown, in order.
     *
     * @return the messages
     */
    public synchronized List<String> errors() {
        return List.copyOf(errors);
    }

    /**
     * Returns the classes of the dialog views shown, in order.
     *
     * @return the classes
     */
    public synchronized List<Class<?>> shown() {
        return List.copyOf(shown);
    }

    @Override
    public synchronized boolean confirm(String message) {
        confirmed.add(message);
        return confirmation;
    }

    @Override
    public synchronized void inform(String message) {
        informed.add(message);
    }

    @Override
    public synchronized void error(String message, Throwable cause) {
        errors.add(message);
    }

    void answer(OpenDialog<?> dialog) {
        Consumer<OpenDialog<?>> answer;
        synchronized (this) {
            shown.add(dialog.view().getClass());
            answer = answers.get(dialog.view().getClass());
        }
        if (answer != null) {
            answer.accept(dialog);
        }
    }
}
