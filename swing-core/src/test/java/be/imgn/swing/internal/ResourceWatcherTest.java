package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.DisplayLocale;
import be.imgn.swing.ResourceOpener;
import be.imgn.swing.UiExecutor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceWatcherTest {

    @TempDir
    Path directory;

    private final ResourceState state = new ResourceState(new DisplayLocale(Locale.ROOT));
    private final AtomicInteger refreshes = new AtomicInteger();
    private final UiExecutor executor = new UiExecutor() {
        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public boolean isUiThread() {
            return true;
        }
    };

    private ResourceOpener opener(@Nullable Path watched) {
        return new ResourceOpener() {
            @Override
            public String packageName() {
                return "p";
            }

            @Override
            public Optional<InputStream> open(String fileName) {
                return Optional.empty();
            }

            @Override
            public Set<String> icons() {
                return Set.of();
            }

            @Override
            public Optional<Path> directory() {
                return Optional.ofNullable(watched);
            }
        };
    }

    private ResourceWatcher watcher() {
        return new ResourceWatcher(
                List.of(opener(directory), opener(null)), state, refreshes::incrementAndGet, executor);
    }

    @Test
    void reloadsOnceTwoPollsInARowAgreeOnAChange() throws IOException {
        Files.writeString(directory.resolve("NotesView.toml"), "title = \"Notes\"\n");
        ResourceWatcher watcher = watcher();
        ResourceState.Snapshot before = state.snapshot();
        Files.writeString(directory.resolve("NotesView.toml"), "title = \"All the notes\"\n");

        watcher.poll();
        assertThat(refreshes).hasValue(0);
        watcher.poll();

        assertThat(refreshes).hasValue(1);
        assertThat(state.snapshot()).isNotSameAs(before);
        watcher.poll();
        assertThat(refreshes).hasValue(1);
    }

    @Test
    void reloadsAFileAddedOrRemoved() throws IOException {
        ResourceWatcher watcher = watcher();
        Files.writeString(directory.resolve("NotesView_fr.toml"), "title = \"Notes\"\n");
        watcher.poll();
        watcher.poll();
        assertThat(refreshes).hasValue(1);

        Files.delete(directory.resolve("NotesView_fr.toml"));
        watcher.poll();
        watcher.poll();

        assertThat(refreshes).hasValue(2);
    }

    @Test
    void holdsTheReloadBackUntilTheFileParses() throws IOException {
        ResourceWatcher watcher = watcher();
        Files.writeString(directory.resolve("NotesView.toml"), "title = \n");
        watcher.poll();
        watcher.poll();
        assertThat(refreshes).hasValue(0);

        Files.writeString(directory.resolve("NotesView.toml"), "title = \"Notes\"\n");
        watcher.poll();
        watcher.poll();

        assertThat(refreshes).hasValue(1);
    }

    @Test
    void watchesTheDirectoriesOfThePackagesLoadedFromOne() {
        assertThat(watcher().directories()).containsExactly(directory);
    }

    @Test
    void ignoresFilesOtherThanToml() throws IOException {
        ResourceWatcher watcher = watcher();
        Files.writeString(directory.resolve("icon.png"), "png");
        watcher.poll();
        watcher.poll();

        assertThat(refreshes).hasValue(0);
    }

    @Test
    void reloadsAFileWhoseChangeTwoPollsAgreeOnWhileAnotherStillChanges() throws IOException {
        ResourceWatcher watcher = watcher();
        Files.writeString(directory.resolve("NotesView.toml"), "title = \"Notes\"\n");
        watcher.poll();
        Files.writeString(directory.resolve("Other.toml"), "title = \"O\"\n");

        watcher.poll();

        assertThat(refreshes).hasValue(1);
    }

    @Test
    void reloadsNothingWhileTheDirectoryIsGone() throws IOException {
        Files.writeString(directory.resolve("NotesView.toml"), "title = \"Notes\"\n");
        ResourceWatcher watcher = watcher();
        Files.delete(directory.resolve("NotesView.toml"));
        Files.delete(directory);

        watcher.poll();
        watcher.poll();

        assertThat(refreshes).hasValue(0);
    }
}
