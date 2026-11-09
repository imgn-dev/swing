package be.imgn.swing.testing;

import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.FileType;
import be.imgn.swing.ui.SaveChoice;
import be.imgn.swing.ui.internal.UiTexts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

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
    private final Deque<FileAnswer> fileAnswers = new ArrayDeque<>();
    private final Deque<SaveChoice> saveAnswers = new ArrayDeque<>();
    private final List<FileChoice> fileChoices = new ArrayList<>();
    private final List<String> savePrompts = new ArrayList<>();
    private Supplier<UiTexts> texts = () -> {
        throw new IllegalStateException("The application is not started");
    };

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

    /**
     * Queues the answer of the next {@code openFile} or {@code saveFile}: the file chosen.
     *
     * @param answer the file
     * @throws NullPointerException if {@code answer} is {@code null}
     */
    public synchronized void answerOpenFile(Path answer) {
        fileAnswers.add(new FileAnswer(Objects.requireNonNull(answer, "answer")));
    }

    /**
     * Queues the answer of the next {@code saveFile} or {@code openFile}: the file chosen. As the real chooser does, a
     * name without one of the selected type's extensions gets its first one, and a file that exists asks whether to
     * replace it, which the next confirmation answers; refusing leaves the chooser open, for the next queued answer.
     *
     * @param answer the file
     * @throws NullPointerException if {@code answer} is {@code null}
     */
    public synchronized void answerSaveFile(Path answer) {
        fileAnswers.add(new FileAnswer(Objects.requireNonNull(answer, "answer")));
    }

    /** Queues a cancel as the answer of the next file chooser, which returns an empty result. */
    public synchronized void cancelFileChooser() {
        fileAnswers.add(new FileAnswer(null));
    }

    /**
     * Queues the answer of the next {@code askSave}. A save prompt with no answer queued is answered
     * {@link SaveChoice#DISCARD}.
     *
     * @param answer the answer
     * @throws NullPointerException if {@code answer} is {@code null}
     */
    public synchronized void answerSave(SaveChoice answer) {
        saveAnswers.add(Objects.requireNonNull(answer, "answer"));
    }

    /**
     * Returns the file choosers shown, in order.
     *
     * @return the choosers
     */
    public synchronized List<FileChoice> fileChoices() {
        return List.copyOf(fileChoices);
    }

    /**
     * Returns the document names of the save prompts asked, in order.
     *
     * @return the names
     */
    public synchronized List<String> savePrompts() {
        return List.copyOf(savePrompts);
    }

    @Override
    public synchronized Optional<Path> openFile(List<FileType> types) {
        fileChoices.add(new FileChoice(FileChoice.Kind.OPEN, types, null));
        @Nullable FileAnswer answer = fileAnswers.poll();
        return answer == null ? Optional.empty() : Optional.ofNullable(answer.file());
    }

    @Override
    public synchronized Optional<Path> saveFile(List<FileType> types, String suggestedName) {
        fileChoices.add(new FileChoice(FileChoice.Kind.SAVE, types, Objects.requireNonNull(suggestedName)));
        while (true) {
            @Nullable FileAnswer answer = fileAnswers.poll();
            @Nullable Path chosen = answer == null ? null : answer.file();
            if (chosen == null) {
                return Optional.empty();
            }
            Path file = withExtension(chosen, types);
            if (!Files.exists(file)
                    || confirm(texts.get().replace(file.getFileName().toString()))) {
                return Optional.of(file);
            }
        }
    }

    // The first type is the selected one, as the real chooser selects it.
    private static Path withExtension(Path file, List<FileType> types) {
        if (types.isEmpty()) {
            return file;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String> extensions = types.getFirst().extensions();
        for (String extension : extensions) {
            if (name.endsWith("." + extension.toLowerCase(Locale.ROOT))) {
                return file;
            }
        }
        return file.resolveSibling(file.getFileName() + "." + extensions.getFirst());
    }

    @Override
    public synchronized SaveChoice askSave(String documentName) {
        savePrompts.add(Objects.requireNonNull(documentName, "documentName"));
        SaveChoice answer = saveAnswers.poll();
        return answer == null ? SaveChoice.DISCARD : answer;
    }

    void texts(Supplier<UiTexts> source) {
        this.texts = source;
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

    // A queued answer of a file chooser: the file chosen, or null for a cancel.
    private record FileAnswer(@Nullable Path file) {}
}
