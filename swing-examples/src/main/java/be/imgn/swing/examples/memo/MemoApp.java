package be.imgn.swing.examples.memo;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Arguments;
import be.imgn.swing.desktop.Documents;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.MultiFrame;
import be.imgn.swing.ui.Swing;
import java.nio.file.FileSystem;

/** Memo: a note per frame, each argument opened as a note, or an untitled note without arguments. */
@Application(name = "Memo", vendor = "imgn", qualifier = "be")
public final class MemoApp implements MultiFrame {

    private final Documents documents;
    private final FileSystem fileSystem;

    // The arguments are paths of the application's file system, the one its directories are on.
    @Inject
    MemoApp(Documents documents, ApplicationDirectories directories) {
        this.documents = documents;
        this.fileSystem = directories.data().getFileSystem();
    }

    /**
     * Launches Memo.
     *
     * @param args the files to open
     */
    public static void main(String[] args) {
        Swing.launch(MemoApp.class, args);
    }

    @Override
    public void start(Frames frames, Arguments arguments) {
        if (arguments.values().isEmpty()) {
            documents.create(MemoView.class);
        } else {
            arguments.values().forEach(file -> documents.open(MemoView.class, fileSystem.getPath(file)));
        }
    }
}
