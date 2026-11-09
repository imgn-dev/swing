package be.imgn.swing.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The recent files and their file, {@code recent-files.toml}. */
class RecentFilesTest {

    @TempDir
    Path directory;

    private Path file() {
        return directory.resolve("state/recent-files.toml");
    }

    private RecentFiles now() {
        return new RecentFiles(file(), RecentFilesTest::run);
    }

    private static void run(RecentFiles.Write write) {
        try {
            write.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void putsAFileFirstOnceAndKeepsTheTenMostRecentNormalized() {
        RecentFiles recent = now();
        IntStream.rangeClosed(1, 11).forEach(index -> recent.add(directory.resolve("n" + index + ".txt")));

        recent.add(directory.resolve("sub/../n5.txt"));

        assertThat(recent.files().get())
                .hasSize(10)
                .startsWith(directory.resolve("n5.txt"), directory.resolve("n11.txt"));
        assertThat(recent.files().get())
                .doesNotContain(directory.resolve("n1.txt"))
                .contains(directory.resolve("n2.txt"));
    }

    @Test
    void writesTheListAsTomlAndLoadsItBack() throws IOException {
        RecentFiles recent = now();
        recent.add(directory.resolve("a.txt"));
        recent.add(directory.resolve("b.txt"));
        recent.remove(directory.resolve("a.txt"));
        recent.add(directory.resolve("c.txt"));

        assertThat(Files.readString(file()))
                .contains("files = [")
                .contains("c.txt")
                .contains("b.txt");

        RecentFiles loaded = now();
        loaded.load();

        assertThat(loaded.files().get()).containsExactly(directory.resolve("c.txt"), directory.resolve("b.txt"));

        loaded.clear();
        RecentFiles cleared = now();
        cleared.load();

        assertThat(cleared.files().get()).isEmpty();
    }

    @Test
    void ignoresUnknownKeysAndDiscardsAFileThatDoesntBind() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "files = [\"" + directory.resolve("a.txt") + "\"]\nversion = 2\n");
        RecentFiles known = now();
        known.load();

        Files.writeString(file(), "files = 3\n");
        RecentFiles discarded = now();
        discarded.load();

        assertThat(known.files().get()).containsExactly(directory.resolve("a.txt"));
        assertThat(discarded.files().get()).isEmpty();
    }

    @Test
    void writesNothingOverANewerListWhenTheWritesRunOutOfOrder() {
        List<RecentFiles.Write> pending = new ArrayList<>();
        RecentFiles recent = new RecentFiles(file(), pending::add);
        recent.add(directory.resolve("a.txt"));
        recent.add(directory.resolve("b.txt"));

        pending.reversed().forEach(RecentFilesTest::run);

        RecentFiles loaded = now();
        loaded.load();
        assertThat(loaded.files().get()).containsExactly(directory.resolve("b.txt"), directory.resolve("a.txt"));
    }

    @Test
    void flushWritesTheLastListWhenItsWritesNeverRan() {
        RecentFiles recent = new RecentFiles(file(), write -> {});
        recent.add(directory.resolve("a.txt"));

        recent.flush();

        RecentFiles loaded = now();
        loaded.load();
        assertThat(loaded.files().get()).containsExactly(directory.resolve("a.txt"));
    }
}
