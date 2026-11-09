package be.imgn.swing.desktop;

import be.imgn.inject.Inject;
import be.imgn.inject.Provider;
import be.imgn.swing.Observable;
import be.imgn.swing.Scope;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.CommandRegistry;
import be.imgn.swing.ui.DynamicEntry;
import be.imgn.swing.ui.FrameView;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.ViewScoped;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The commands of a document view, beside the standard ones: new, open, save and save as, and the recent files. Their
 * texts and shortcuts come from the desktop's own resources; a document view overrides them in its own resources, as it
 * overrides the standard commands', such as {@code [commands.save]}.
 */
@ViewScoped
public final class DocumentCommands extends StandardCommands {

    private final BoundCommand newDocument;
    private final BoundCommand open;
    private final BoundCommand save;
    private final BoundCommand saveAs;
    private final MenuEntry recentFiles;

    @Inject
    DocumentCommands(
            CommandRegistry registry,
            Provider<Document> document,
            Documents documents,
            RecentFiles recent,
            Scope scope,
            WindowRegistry windows) {
        super(registry);
        ViewHost view = windows.viewOf(scope);
        this.newDocument = registry.overridableCommand(DocumentCommands.class, "newDocument")
                .handler(context -> documents.createIn(type(view)))
                .bind();
        this.open = registry.overridableCommand(DocumentCommands.class, "open")
                .handler(context -> document.get()
                        .dialogs()
                        .openFile(List.of(document.get().format().fileType()))
                        .ifPresent(file -> documents.openIn(type(view), file)))
                .bind();
        this.save = registry.overridableCommand(DocumentCommands.class, "save")
                .taskHandler(context -> document.get().saveTask(() -> {}))
                .enabled(() -> Observable.combine(
                        document.get().modified(),
                        document.get().file(),
                        (modified, file) -> modified || file.isEmpty()))
                .block(Block.WINDOW)
                .bind();
        this.saveAs = registry.overridableCommand(DocumentCommands.class, "saveAs")
                .taskHandler(context -> document.get().saveAsTask())
                .block(Block.WINDOW)
                .bind();
        this.recentFiles = MenuEntry.dynamic(recent.files()
                .map(files -> DynamicEntry.numbered(files.stream()
                        .map(file -> DynamicEntry.item(name(file), () -> documents.openIn(type(view), file)))
                        .toList())));
    }

    private static Class<? extends FrameView> type(ViewHost view) {
        return view.type().asSubclass(FrameView.class);
    }

    // A dynamic item's text marks its mnemonic with "&": a file's own "&" is doubled.
    private static String name(Path file) {
        return Objects.toString(file.getFileName(), file.toString()).replace("&", "&&");
    }

    /**
     * Returns the command that opens a frame of a new, untitled document of the view's class.
     *
     * @return the command
     */
    public BoundCommand newDocument() {
        return newDocument;
    }

    /**
     * Returns the command that asks for a file of the view's type with the open chooser, then opens it.
     *
     * @return the command
     */
    public BoundCommand open() {
        return open;
    }

    /**
     * Returns the command that writes the document to its file, or, while untitled, does what {@link #saveAs()} does;
     * enabled while the document is modified or untitled.
     *
     * @return the command
     */
    public BoundCommand save() {
        return save;
    }

    /**
     * Returns the command that asks for a file with the save chooser, then writes the document to it.
     *
     * @return the command
     */
    public BoundCommand saveAs() {
        return saveAs;
    }

    /**
     * Returns the dynamic entry of the recent files, numbered, each named by the file's name and opening it in a
     * document of the view's class.
     *
     * @return the entry
     */
    public MenuEntry recentFiles() {
        return recentFiles;
    }
}
