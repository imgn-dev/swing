package be.imgn.swing.testing;

import static java.util.Objects.requireNonNull;

import be.imgn.swing.ui.SystemShell;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The tester's {@link SystemShell}: it supports both actions, and records the URIs browsed and the files opened. A test
 * of a system that can't browse turns browsing off.
 */
public final class FakeShell implements SystemShell {

    private final List<URI> browsed = new CopyOnWriteArrayList<>();
    private final List<Path> opened = new CopyOnWriteArrayList<>();
    private volatile boolean browsing = true;

    FakeShell() {}

    @Override
    public boolean canBrowse() {
        return browsing;
    }

    /**
     * Sets whether the shell can browse, as a system without a browser can't; it can by default.
     *
     * @param supported whether the shell can browse
     */
    public void supportBrowsing(boolean supported) {
        browsing = supported;
    }

    @Override
    public void browse(URI uri) {
        requireNonNull(uri, "uri");
        if (!browsing) {
            throw new UnsupportedOperationException("The system can't browse " + uri);
        }
        browsed.add(uri);
    }

    @Override
    public boolean canOpen() {
        return true;
    }

    @Override
    public void open(Path file) {
        opened.add(requireNonNull(file, "file"));
    }

    /**
     * Returns the URIs browsed, in order.
     *
     * @return the URIs
     */
    public List<URI> browsed() {
        return List.copyOf(browsed);
    }

    /**
     * Returns the files opened, in order.
     *
     * @return the files
     */
    public List<Path> opened() {
        return List.copyOf(opened);
    }
}
