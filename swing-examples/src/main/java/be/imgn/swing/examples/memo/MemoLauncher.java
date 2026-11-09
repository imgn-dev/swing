package be.imgn.swing.examples.memo;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Arguments;
import be.imgn.swing.desktop.Documents;
import be.imgn.swing.desktop.SecondInstanceHandler;
import be.imgn.swing.ui.Frames;
import java.nio.file.FileSystem;

/**
 * Makes Memo single-instance: a second launch opens its files, or an untitled note when it has none, then brings the
 * active frame to the front. The examples share one module, whose beans every example has: in another example, a second
 * launch does nothing.
 */
@Singleton
final class MemoLauncher implements SecondInstanceHandler {

    private final Documents documents;
    private final Frames frames;
    private final FileSystem fileSystem;
    private final boolean memo;

    @Inject
    MemoLauncher(Documents documents, Frames frames, ApplicationDirectories directories, ApplicationInfo application) {
        this.documents = documents;
        this.frames = frames;
        this.fileSystem = directories.data().getFileSystem();
        this.memo = application.type().equals(MemoApp.class);
    }

    @Override
    public void launched(Arguments arguments) {
        if (!memo) {
            return;
        }
        if (arguments.values().isEmpty()) {
            documents.create(MemoView.class);
        } else {
            arguments.values().forEach(file -> documents.open(MemoView.class, fileSystem.getPath(file)));
        }
        frames.active().ifPresent(frames::toFront);
    }
}
