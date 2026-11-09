package be.imgn.swing.examples.memo;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.desktop.Documents;
import be.imgn.swing.desktop.OpenFilesHandler;
import java.nio.file.Path;
import java.util.List;

/**
 * Opens the files the operating system opens with Memo, such as from the macOS Finder, each as a note. The examples
 * share one module, whose beans every example has: in another example, it opens nothing.
 */
@Singleton
final class MemoFiles implements OpenFilesHandler {

    private final Documents documents;
    private final boolean memo;

    @Inject
    MemoFiles(Documents documents, ApplicationInfo application) {
        this.documents = documents;
        this.memo = application.type().equals(MemoApp.class);
    }

    @Override
    public void open(List<Path> files) {
        if (memo) {
            files.forEach(file -> documents.open(MemoView.class, file));
        }
    }
}
