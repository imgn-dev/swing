package be.imgn.swing.desktop;

import static java.util.Objects.requireNonNull;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.FrameView;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.internal.FrameControl;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Creates and opens documents: each in a frame of its own, or, for the application class of a single-frame application,
 * in its main frame. Every method is called on the EDT.
 */
@Singleton
public final class Documents {

    private final Frames frames;
    private final FrameControl control;
    private final WindowRegistry registry;
    private final RecentFiles recentFiles;
    // The numbers of the untitled documents open, 1 for the one named without a number.
    private final TreeSet<Integer> untitled = new TreeSet<>();

    @Inject
    Documents(Frames frames, FrameControl control, WindowRegistry registry, RecentFiles recentFiles) {
        this.frames = frames;
        this.control = control;
        this.registry = registry;
        this.recentFiles = recentFiles;
    }

    /**
     * Opens a frame of an untitled document of a type. For the application class of a single-frame application, asks
     * the save prompt when its document is modified, then makes it untitled again, with the empty contents of its
     * format.
     *
     * @param type the document view class
     * @param <V> the document view class
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <V extends FrameView & DocumentView<?>> void create(Class<V> type) {
        createIn(requireNonNull(type, "type").asSubclass(FrameView.class));
    }

    void createIn(Class<? extends FrameView> type) {
        Optional<Document> main = mainDocument(type);
        if (main.isPresent()) {
            Document document = main.get();
            document.replace(document::reset);
            return;
        }
        frames.open(type);
    }

    /**
     * Opens a file in a document of a type. If a frame of that type already shows the file, it is brought to the front.
     * Otherwise a new frame is opened, and the file is read into it in a task blocking that window; for the application
     * class of a single-frame application, the file is read into the main frame, after the save prompt when its
     * document is modified. A read that fails shows the error, removes the file from the recent files when it doesn't
     * exist, and closes the frame opened for it, unless it is the only frame of a multi-frame application.
     *
     * @param type the document view class
     * @param file the file, normalized to an absolute path
     * @param <V> the document view class
     * @throws NullPointerException if an argument is {@code null}
     */
    public <V extends FrameView & DocumentView<?>> void open(Class<V> type, Path file) {
        openIn(requireNonNull(type, "type").asSubclass(FrameView.class), requireNonNull(file, "file"));
    }

    void openIn(Class<? extends FrameView> type, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        Optional<Document> main = mainDocument(type);
        if (main.isPresent()) {
            Document document = main.get();
            document.replace(() -> document.read(normalized, () -> {}));
            return;
        }
        for (ViewHost view : frameViews(type)) {
            Document document = view.injector().getInstance(Document.class);
            if (document.file().get().equals(Optional.of(normalized))) {
                frames.toFront((FrameView) view.view().orElseThrow());
                return;
            }
        }
        FrameView opened = frames.open(type);
        ViewHost view = viewOf(opened);
        WindowHost frame = view.window();
        view.injector().getInstance(Document.class).read(normalized, () -> {
            if (!(control.isMultiFrame() && registry.frames().size() == 1)) {
                control.discard(frame);
            }
        });
    }

    // The main frame's document, when the type is the application class of a single-frame application.
    private Optional<Document> mainDocument(Class<?> type) {
        return registry.main()
                .map(main -> main.views().getFirst())
                .filter(view -> view.type().equals(type))
                .map(view -> view.injector().getInstance(Document.class));
    }

    private List<ViewHost> frameViews(Class<?> type) {
        return registry.frames().stream()
                .map(frame -> frame.views().getFirst())
                .filter(view -> type.isInstance(view.view().orElse(null)))
                .toList();
    }

    private ViewHost viewOf(FrameView view) {
        return registry.frames().stream()
                .map(frame -> frame.views().getFirst())
                .filter(host -> view.equals(host.view().orElse(null)))
                .findFirst()
                .orElseThrow();
    }

    RecentFiles recentFiles() {
        return recentFiles;
    }

    // The smallest number no open untitled document has: 1 names "Untitled", 2 "Untitled 2", and so on.
    int claimUntitled() {
        int number = 1;
        while (untitled.contains(number)) {
            number++;
        }
        untitled.add(number);
        return number;
    }

    void releaseUntitled(int number) {
        untitled.remove(number);
    }
}
