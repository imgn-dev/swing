package be.imgn.swing.examples.notes;

import be.imgn.gaia.core.io.AtomicFiles;
import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.Property;
import be.imgn.swing.StartupStep;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.jspecify.annotations.Nullable;

/**
 * The text of the notes, kept in {@code notes.txt} in the data directory. A file that can't be read leaves the text
 * empty, and stays as it is until the user saves.
 */
@Singleton
public final class NoteStore {

    private static final System.Logger LOGGER = System.getLogger(NoteStore.class.getName());

    private final Property<String> text = Property.of("");
    private final Path file;
    private volatile @Nullable CountDownLatch hold;
    private volatile @Nullable Thread loader;

    @Inject
    NoteStore(ApplicationDirectories directories) {
        this.file = directories.data().resolve("notes.txt");
    }

    /**
     * Returns the text of the notes.
     *
     * @return the text, empty until it is loaded
     */
    public Property<String> text() {
        return text;
    }

    /**
     * Writes a text atomically to {@code notes.txt}, creating the data directory when it is missing. Called off the
     * EDT.
     *
     * @param content the text
     * @return the file written
     * @throws IOException if the file can't be written
     * @throws InterruptedException if interrupted while a test holds the save
     */
    Path save(String content) throws IOException, InterruptedException {
        CountDownLatch latch = hold;
        if (latch != null) {
            latch.await();
        }
        Files.createDirectories(file.getParent());
        AtomicFiles.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Makes each save wait for a latch before it writes, so that tests act while it runs.
     *
     * @param latch the latch
     */
    void hold(CountDownLatch latch) {
        this.hold = latch;
    }

    /**
     * Returns the thread that loaded the notes, so that tests check that it isn't the EDT.
     *
     * @return the thread, {@code null} until the notes are loaded
     */
    @Nullable
    Thread loader() {
        return loader;
    }

    private void load() {
        loader = Thread.currentThread();
        if (Files.notExists(file)) {
            return;
        }
        try {
            text.set(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // A file that can't be read is no reason not to start: the user can still save.
            LOGGER.log(System.Logger.Level.WARNING, "Cannot read " + file + ": the notes start empty", e);
        }
    }

    /** Loads the notes at startup, off the EDT, once the settings and the window state are loaded. */
    @Singleton
    @IntoSet(StartupStep.class)
    static final class Loading implements StartupStep {

        private static final int ORDER = 300;

        private final NoteStore store;

        @Inject
        Loading(NoteStore store) {
            this.store = store;
        }

        @Override
        public int order() {
            return ORDER;
        }

        @Override
        public void run() {
            store.load();
        }
    }
}
