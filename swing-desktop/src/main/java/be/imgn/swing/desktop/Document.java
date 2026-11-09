package be.imgn.swing.desktop;

import be.imgn.gaia.core.io.AtomicFiles;
import be.imgn.inject.Inject;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.desktop.internal.DesktopTexts;
import be.imgn.swing.internal.DefaultLifecycle;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.ExitVetoes;
import be.imgn.swing.internal.NoTask;
import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FrameView;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.SaveChoice;
import be.imgn.swing.ui.UndoStack;
import be.imgn.swing.ui.ViewScoped;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JRootPane;
import org.jspecify.annotations.Nullable;

/**
 * The document of a document view, a bean of its scope: its file, its name and whether it is modified. The view, or any
 * bean of its scopes, injects it, such as a status bar showing {@link #name()}.
 *
 * <p>A document starts untitled, with the contents its view's {@code content()} built, named "Untitled", then "Untitled
 * 2" and so on for the next untitled documents open at the same time. While it is modified, closing its frame or
 * exiting asks whether to save it.
 */
@ViewScoped
public final class Document {

    private final Property<Optional<Path>> file = Property.of(Optional.empty());
    private final Property<String> name = Property.of("");
    private final Form form;
    private final UndoStack undo;
    private final Documents documents;
    private final DesktopTexts texts;
    private final UiServices services;
    private final ExitVetoes vetoes;
    private final DefaultLifecycle lifecycle;
    private final ApplicationInfo application;
    private @Nullable ViewHost host;
    private @Nullable Contents<?> contents;
    // The file a read in progress reads, which counts as shown.
    private @Nullable Path reading;
    private int untitled;

    @Inject
    Document(
            Form form,
            UndoStack undo,
            Documents documents,
            DesktopTexts texts,
            UiServices services,
            ExitVetoes vetoes) {
        this.form = form;
        this.undo = undo;
        this.documents = documents;
        this.texts = texts;
        this.services = services;
        this.vetoes = vetoes;
        this.lifecycle = services.lifecycle();
        this.application = services.application();
    }

    /**
     * Returns the document's file.
     *
     * @return the observable, empty while the document is untitled
     */
    public Observable<Optional<Path>> file() {
        return file;
    }

    /**
     * Returns the document's name: its file's name, else "Untitled", numbered from the second untitled document open at
     * the same time.
     *
     * @return the observable
     */
    public Observable<String> name() {
        return name;
    }

    /**
     * Returns whether the document is modified: its view's {@code Form.dirty()}.
     *
     * @return the observable
     */
    public Observable<Boolean> modified() {
        return form.dirty();
    }

    // Starts following the view, once its build ends: its format, its name, its title and its save prompt. A frame
    // that Documents opens for a file is named after it while it is read, and takes no untitled number.
    void start(DocumentView<?> started, ViewHost built, @Nullable Path opening) {
        this.contents = Contents.of(started);
        this.host = built;
        if (opening == null) {
            untitled();
        } else {
            reading = opening;
            name.set(fileName(opening));
        }
        DisposalScope scope = built.scope();
        scope.onClose(this::release);
        vetoes.register(scope, this::canClose);
        name.subscribe(scope, ignored -> showTitle());
        form.dirty().subscribe(scope, ignored -> showTitle());
        file.subscribe(scope, ignored -> showTitle());
        showTitle();
    }

    /**
     * Returns whether the document shows a file, or is reading it.
     *
     * @param shown the file, absolute and normalized
     * @return {@code true} if it does
     */
    boolean shows(Path shown) {
        return shown.equals(reading) || file.get().equals(Optional.of(shown));
    }

    DocumentView<?> view() {
        return contents().view();
    }

    ViewHost host() {
        return Objects.requireNonNull(host, "The document's view is not built yet");
    }

    DocumentFormat<?> format() {
        return contents().format();
    }

    private Contents<?> contents() {
        return Objects.requireNonNull(contents, "The document's view is not built yet");
    }

    Dialogs dialogs() {
        return host().injector().getInstance(Dialogs.class);
    }

    private void untitled() {
        untitled = documents.claimUntitled();
        name.set(untitled == 1 ? texts.untitled() : texts.untitled() + " " + untitled);
        file.set(Optional.empty());
    }

    private void release() {
        if (untitled != 0) {
            documents.releaseUntitled(untitled);
            untitled = 0;
        }
    }

    // A document's file is always absolute and normalized, whether read or saved.
    private void titled(Path titled) {
        release();
        Path normalized = titled.toAbsolutePath().normalize();
        name.set(fileName(normalized));
        file.set(Optional.of(normalized));
    }

    // The frame's title: "{document} – {application}"; on macOS, the root pane's properties show the modified dot and
    // the proxy icon; elsewhere, a "*" starts the title while the document is modified.
    private void showTitle() {
        boolean modified = form.dirty().get();
        String title = texts.documentTitle(name.get(), application.displayedName());
        JRootPane rootPane = host().window().window().rootPane();
        if (services.isMacOS()) {
            rootPane.putClientProperty("Window.documentModified", modified);
            rootPane.putClientProperty(
                    "Window.documentFile", file.get().map(Document::toFile).orElse(null));
            host().window().window().setTitle(title);
        } else {
            host().window().window().setTitle(modified ? "*" + title : title);
        }
    }

    private static @Nullable File toFile(Path path) {
        try {
            return path.toFile();
        } catch (UnsupportedOperationException notOnTheDefaultFileSystem) {
            return null;
        }
    }

    // The save prompt: a modified document asks whether to save it. SAVE stops the close or the exit, writes, then
    // requests it again: the frame's close, or the exit with the status first requested.
    private boolean canClose() {
        if (!form.dirty().get()) {
            return true;
        }
        return switch (dialogs().askSave(name.get())) {
            case DISCARD -> true;
            case CANCEL -> false;
            case SAVE -> {
                boolean exiting = lifecycle.isShuttingDown();
                int status = lifecycle.requestedStatus();
                Runnable again = exiting
                        ? () -> lifecycle.requestExit(status)
                        : () -> host().injector().getInstance(Frames.class).close((FrameView) view());
                run(saveTask(again), "save");
                yield false;
            }
        };
    }

    /**
     * Asks the save prompt before replacing the document in a single-frame application, then replaces it.
     *
     * @param replace replaces the document
     */
    void replace(Runnable replace) {
        if (!form.dirty().get()) {
            replace.run();
            return;
        }
        SaveChoice choice = dialogs().askSave(name.get());
        if (choice == SaveChoice.DISCARD) {
            replace.run();
        } else if (choice == SaveChoice.SAVE) {
            run(saveTask(replace), "save");
        }
    }

    /** Makes the document untitled again, with the empty contents of its format. */
    void reset() {
        loadEmpty(contents());
    }

    private <T> void loadEmpty(Contents<T> typed) {
        typed.view().loaded(typed.format().empty());
        release();
        untitled();
        form.markClean();
        undo.clear();
    }

    /**
     * Reads a file into the document, in a task blocking its window.
     *
     * @param read the file, absolute
     * @param failed runs once a failed read is reported
     */
    void read(Path read, Runnable failed) {
        reading = read;
        run(readTask(read, contents(), failed), "open");
    }

    private <T> Task<T> readTask(Path read, Contents<T> typed, Runnable failed) {
        return Task.of(() -> {
                    try (InputStream in = Files.newInputStream(read)) {
                        return typed.format().read(in);
                    }
                })
                .onSuccess(loaded -> {
                    reading = null;
                    typed.view().loaded(loaded);
                    titled(read);
                    form.markClean();
                    undo.clear();
                    documents.recentFiles().add(read);
                })
                .onFailure(failure -> {
                    reading = null;
                    dialogs().error(texts.openFailed(fileName(read), message(failure)), failure);
                    if (!Files.exists(read)) {
                        documents.recentFiles().remove(read);
                    }
                    failed.run();
                    // A frame opened for the file that stays, as the only frame of a multi-frame application, is
                    // untitled from now on.
                    if (!host().scope().isClosed() && file.get().isEmpty() && untitled == 0) {
                        untitled();
                    }
                });
    }

    /**
     * Returns the task that writes the document to its file, or, while untitled, to the file the save chooser gives.
     *
     * @param then runs once the write succeeds
     * @return the task, or no task when the user cancels the chooser
     */
    Task<?> saveTask(Runnable then) {
        Optional<Path> target = file.get().isPresent() ? file.get() : chooseFile();
        return target.<Task<?>>map(chosen -> writeTask(chosen, contents(), then))
                .orElse(Task.none());
    }

    /**
     * Returns the task that writes the document to the file the save chooser gives.
     *
     * @return the task, or no task when the user cancels the chooser
     */
    Task<?> saveAsTask() {
        return chooseFile()
                .<Task<?>>map(chosen -> writeTask(chosen, contents(), () -> {}))
                .orElse(Task.none());
    }

    private Optional<Path> chooseFile() {
        return dialogs().saveFile(List.of(format().fileType()), name.get());
    }

    // toSave() runs on the EDT, now; the write, in the task.
    private <T> Task<Path> writeTask(Path target, Contents<T> typed, Runnable then) {
        T toSave = typed.view().toSave();
        return Task.of(() -> {
                    AtomicFiles.write(target, out -> typed.format().write(toSave, out));
                    return target;
                })
                .onSuccess(written -> {
                    titled(written);
                    form.markClean();
                    Path normalized = file.get().orElse(written);
                    documents.recentFiles().add(normalized);
                    view().saved(normalized);
                    then.run();
                })
                .onFailure(failure -> dialogs().error(texts.saveFailed(fileName(target), message(failure)), failure));
    }

    private static String fileName(Path path) {
        return Objects.toString(path.getFileName(), path.toString());
    }

    private static String message(Throwable failure) {
        return Objects.requireNonNullElse(
                failure.getMessage(), failure.getClass().getName());
    }

    // A task blocking the document's window, which the test harness waits for as for a command's.
    private void run(Task<?> task, String taskName) {
        if (task instanceof NoTask) {
            return;
        }
        RunningTask running = services.tasks().start(task, host().scope(), taskName, true);
        Runnable unblock =
                services.blocker().cover(List.of(host().window().window().rootPane()), running);
        running.whenEnded(unblock);
    }

    /** A document view with its format, whose contents have the same type. */
    private record Contents<T>(DocumentView<T> view, DocumentFormat<T> format) {
        static <T> Contents<T> of(DocumentView<T> view) {
            return new Contents<>(view, view.format());
        }
    }
}
