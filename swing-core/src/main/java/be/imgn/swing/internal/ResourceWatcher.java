package be.imgn.swing.internal;

import be.imgn.swing.ResourceOpener;
import be.imgn.swing.UiExecutor;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlParseException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reloads the TOML files of the class output when they change, in development mode: it reads the names, last-modified
 * times and sizes of the {@code .toml} files of each package's directory at each poll, and a file that is added,
 * removed or changed counts once two polls in a row agree on it, so that a copy in progress is not read. Polling
 * behaves the same on every system, where the JDK's watch service polls on macOS.
 */
public final class ResourceWatcher {

    private static final System.Logger LOGGER = System.getLogger(ResourceWatcher.class.getName());

    private final List<Path> directories;
    private final ResourceState state;
    private final Refresher refresher;
    private final UiExecutor executor;
    // The files as last reloaded, as the last poll read them, and the stamps of the files that didn't parse, which are
    // parsed again only once they change.
    private Map<Path, Stamp> applied;
    private Map<Path, Stamp> polled;
    private final Map<Path, Stamp> rejected = new HashMap<>();

    /**
     * Creates the watcher of the directories of openers, as they are now.
     *
     * @param openers the openers, whose directories in the class output are watched
     * @param state the state of the resources, whose caches a reload drops
     * @param refresher refreshes the open windows
     * @param executor runs the refresh on the UI thread
     */
    public ResourceWatcher(
            Collection<ResourceOpener> openers, ResourceState state, Refresher refresher, UiExecutor executor) {
        this.directories = openers.stream()
                .map(ResourceOpener::directory)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        this.state = state;
        this.refresher = refresher;
        this.executor = executor;
        this.applied = read(Map.of());
        this.polled = applied;
    }

    /**
     * Returns the directories watched.
     *
     * @return the directories of the packages loaded from a directory
     */
    public List<Path> directories() {
        return directories;
    }

    /** Reads the files once, and reloads the files on whose change two polls in a row agree. */
    public void poll() {
        Map<Path, Stamp> now = read(polled);
        Map<Path, Stamp> previous = polled;
        polled = now;
        Set<Path> files = new LinkedHashSet<>(now.keySet());
        files.addAll(applied.keySet());
        Map<Path, Stamp> next = new LinkedHashMap<>(applied);
        List<Path> changed = new ArrayList<>();
        for (Path file : files) {
            Stamp stamp = now.get(file);
            if (Objects.equals(stamp, applied.get(file)) || !Objects.equals(stamp, previous.get(file))) {
                continue;
            }
            if (stamp != null && !parses(file, stamp)) {
                return;
            }
            changed.add(file);
            if (stamp == null) {
                next.remove(file);
            } else {
                next.put(file, stamp);
            }
        }
        if (changed.isEmpty()) {
            return;
        }
        applied = next;
        LOGGER.log(Level.INFO, "Reloaded the resources " + changed);
        state.reload();
        executor.execute(refresher::refresh);
    }

    // A file that doesn't parse holds the reload back until it does; it is reported once per version of it.
    private boolean parses(Path file, Stamp stamp) {
        if (stamp.equals(rejected.get(file))) {
            return false;
        }
        try {
            Toml.parse(file);
            rejected.remove(file);
            return true;
        } catch (TomlParseException e) {
            rejected.put(file, stamp);
            LOGGER.log(Level.WARNING, "Did not reload " + file + ": " + e.getMessage());
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    // A directory or a file that can't be read keeps its previous stamps, so that a build that removes the class
    // output for a while reloads nothing until its files are back.
    private Map<Path, Stamp> read(Map<Path, Stamp> previous) {
        Map<Path, Stamp> files = new LinkedHashMap<>();
        for (Path directory : directories) {
            List<Path> listed;
            try (Stream<Path> entries = Files.list(directory)) {
                listed = entries.filter(path -> path.getFileName().toString().endsWith(".toml"))
                        .sorted()
                        .toList();
            } catch (IOException | UncheckedIOException e) {
                previous.forEach((file, stamp) -> {
                    if (directory.equals(file.getParent())) {
                        files.put(file, stamp);
                    }
                });
                continue;
            }
            for (Path file : listed) {
                try {
                    files.put(file, new Stamp(Files.getLastModifiedTime(file), Files.size(file)));
                } catch (IOException e) {
                    Stamp stamp = previous.get(file);
                    if (stamp != null) {
                        files.put(file, stamp);
                    }
                }
            }
        }
        return files;
    }

    private record Stamp(FileTime modified, long size) {}
}
