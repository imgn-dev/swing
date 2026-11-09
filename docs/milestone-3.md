# Milestone 3: requirements

This is the requirement document of the desktop milestone, described in the [vision](vision.md#milestone-3-desktop): single instance, the OS hooks (`Desktop`, `Taskbar`, notifications), documents with save prompts, recent files and undo, and multi-frame applications, in which `@Application` is required and `SingleFrame` is not. It also delivers what [milestone 2](milestone-2.md#scope) left for it: opening the website from the About dialog. And it takes up the [ideas](ideas.md) that the ports suggested for it: file choosers, the application's icon on every window, and a default application name. The release is accepted when the [acceptance](#acceptance) examples and their tests pass.

Every part of every sentence in this document is a requirement, and must be followed exactly: names, packages, signatures, behaviors, orders and error cases. Any deviation, whether a part that can't be implemented as written, a part that contradicts another, or a gap, is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text.

Milestone 3 builds on milestones 1 and 2 as implemented. Everything they specify stays, except where a section below changes it; [Changes to milestones 1 and 2](#changes-to-milestones-1-and-2) lists those changes.

---

## Scope

| In milestone 3 | Later |
|---|---|
| Multi-frame applications: frame views opened from code, each in its own frame and scopes | A Window menu that lists the open frames; tabs or docking (M5) |
| Closing a frame asks the vetoes of its scopes; closing the last frame exits | Applications that keep running with no frame, as macOS document applications may |
| File choosers through `Dialogs`, answered by tests | Native file dialogs (`FileDialog`); choosing several files at once |
| An undo stack per view, with the standard `undo()` and `redo()` commands, fed by the text bindings | Grouping edits into compound edits; undo across views |
| Documents: a view that edits a file, with new, open, save and save-as commands, the save prompt, a title that follows the document, and recent files | Autosave, file watching, reverting, documents in dialogs |
| Single instance: a second launch forwards its arguments to the first instance | Forwarding to an instance of another user or another machine |
| OS hooks: open-file events, browsing a URI, the taskbar's progress, badge and attention, notifications | Jump lists, the macOS dock menu, notification actions |
| The progress of a task that blocks the application, on the taskbar | The progress of other tasks |
| Variants of frame views, as of dialog views | — |
| The application's icon on every window and on the taskbar | Icons per frame view |
| A default application name | Removing the empty `@Inject` constructors, which is inject's to decide |
| `SingleFrameExample6`'s secondary windows as frames | The Address Book's three views (M5, with docking) |
| The test harness for all of the above | — |

Milestone 3 adds one module, `swing-desktop` (`be.imgn.swing.desktop`), which the [vision](vision.md#modules) plans: single instance, open-file events, notifications, documents and recent files. It requires `be.imgn.swing.ui` transitively, and nothing that `swing-ui` doesn't already require. An application that needs none of them doesn't depend on it. `swing-ui` gains the types of its layer: multi-frame applications (`FrameView`, `MultiFrame`, `Frames`), file choosers (`FileType`, `SaveChoice`), `UndoStack`, `SystemShell` and `TaskbarStatus`. `swing-processor` gains the checks below; `swing-testing` gains the harness additions. Core gains nothing, and still doesn't read `java.desktop`.

The libraries are those of milestone 2: the parent 2026-11-01, gaia 0.1.2, inject 0.1.1 and toml 0.1.0, from Maven Central. Milestone 3 needs no upstream change (see [Upstream changes](#upstream-changes)).

---

## Multi-frame applications

A single-frame application has one window, which its application class fills. A multi-frame application opens frames from code, each showing a view of its own: a document per frame, or an inspector beside an editor.

```java
@Application(name = "Memo", vendor = "imgn", qualifier = "be")
public final class MemoApp implements MultiFrame {

    private final Documents documents;

    @Inject
    MemoApp(Documents documents) {
        this.documents = documents;
    }

    public static void main(String[] args) {
        Swing.launch(MemoApp.class, args);
    }

    @Override
    public void start(Frames frames, Arguments arguments) {
        if (arguments.values().isEmpty()) {
            documents.create(MemoView.class);
        } else {
            arguments.values().forEach(file -> documents.open(MemoView.class, Path.of(file)));
        }
    }
}
```

### Frame views

```java
public interface FrameView extends View {                              // UI
    default @Nullable JMenuBar menuBar() { return null; }
    default @Nullable JToolBar toolBar() { return null; }              // placed at BorderLayout.NORTH
    default void configureFrame(JFrame frame) {}
    default boolean restoreBounds() { return true; }
}

public interface SingleFrame extends FrameView {                       // UI
    default void ready() {}
}

@Singleton
public interface MultiFrame {                                          // UI
    void start(Frames frames, Arguments arguments);
    default void ready() {}
}
```

- A `FrameView` is a view shown in a frame of its own, with the frame's menu bar and toolbar. `menuBar()`, `toolBar()`, `configureFrame` and `restoreBounds` move from `SingleFrame` to `FrameView`, with the meaning [milestone 1](milestone-1.md#singleframe) gives them; `SingleFrame` keeps `ready()`. Code written against milestone 2's `SingleFrame` compiles unchanged.
- `MultiFrame` carries `@Singleton`, which inject's scopes inherited from supertypes ([I5](milestone-1.md#imgn-devinject)) apply to the application class: the class is a singleton of the root injector, not a view, so it has no window, no `Ui` and no component resources of its own. Its `@Command` methods, if any, are a singleton's: their generated class takes the view scope of each view that injects it.
- The `@Application` class implements exactly one of `SingleFrame` and `MultiFrame` (see [Build-time checks](#build-time-checks)).

### Startup

Startup step 4 of [milestone 1](milestone-1.md#startup), which creates and shows the main window, does for a `MultiFrame` application class, on the EDT:

1. Resolves the application class through the root injector.
2. Calls `start(frames, arguments)` with the `Frames` bean and the `Arguments` bean. `start` opens the first frames, through `Frames` or through [`Documents`](#documents).
3. Throws `IllegalStateException` if no frame is open once `start` returns: the startup fails as any failure of step 4 does.

Step 5 then calls the application class's `ready()`, before the `ReadyListener`s, as it calls `SingleFrame.ready()`.

A `MultiFrame` application class is a singleton of the root injector, whose exit vetoes are otherwise only contributions: when it implements `ExitVeto`, step 4 registers it in the application scope, so that exiting asks it after the vetoes of the frames.

### Opening frames

```java
public interface Frames {                                              // UI, singleton
    <V extends FrameView> V open(Class<V> type);
    <F, V extends FrameView> V open(Class<F> factory, Function<? super F, ? extends V> create);
    <V extends FrameView, E extends Enum<E>> V open(Class<V> type, E variant);
    List<FrameView> views();                                           // the views of the open frames, in opening order
    Optional<FrameView> active();
    boolean close(FrameView view);
    void toFront(FrameView view);
}
```

`open` opens a frame showing a new view of the type, and returns the view once the frame is shown. The second form passes arguments through the assisted factory that the processor generates for a view whose `@Inject` constructor has `@Assisted` parameters, as [`showDialog`](milestone-1.md#dialogs) does, resolved through the view's child injector. The third opens a [variant](milestone-1.md#dialogs) of the view, as `showDialog` does for a dialog view: its `@Variants` enum is nested in the frame view's class, its resources merge the variant's table over the base table, and it throws as `showDialog` does. For each call, on the EDT:

1. Creates the frame with `Windows.createFrame()`, with `DO_NOTHING_ON_CLOSE` and a listener that runs the view's [`closeWindow()`](#closing-frames).
2. Sets the frame's title from the view class's `title` resource, else the displayed name, and its icon image from the application's `icon` (see [The application's icon](#the-applications-icon)), before the view is built, so that a title the view sets or binds in `content()` wins.
3. Creates the window's host with `scopedChildBuilder(WindowScoped.class)` as a child of the root injector, binding that `Window` and the window's `Scope`, whose parent is the application scope, and the view's host as its child with `scopedChildBuilder(ViewScoped.class)`. A frame never belongs to the view that opened it: it outlives it.
4. Resolves the view class through the view's child injector, or calls `create` with the factory resolved there.
5. Applies the view's component resources, calls `content()`, `menuBar()` and `toolBar()`, puts them in the frame's root pane, and applies the `tab-title` properties. The build ends here, and the view's commands are [attached](milestone-1.md#attaching).
6. Calls `configureFrame(frame)` with the `JFrame` that the real `Windows` wraps, then `pack()`.
7. Places the frame (see [Window state of frames](#window-state-of-frames)), then shows it, without blocking.

- `views()` lists the views of the frames that are open, in the order they were opened, the main view of a single-frame application first.
- `active()` is the view of the [active frame](#the-active-frame).
- `close(view)` closes the view's frame as its `closeWindow()` does, and returns whether it closed. A view that is not in an open frame, such as a dialog view, throws `IllegalArgumentException`.
- `toFront(view)` brings its frame to the front, deiconified, and focuses it, through `Window.toFront()`. A view that is not in an open frame throws `IllegalArgumentException`.
- Every method is called on the EDT, as the other UI services are. `open` may be called by a single-frame application too: its frames are secondary frames, closed as described below.

`Window` (UI) gains `void toFront()`, which the real window does with `setExtendedState` to clear `ICONIFIED`, `toFront()` and `requestFocus()`, and which `AppTester`'s headless window records.

### Closing frames

The standard command `closeWindow()`, the frame's close button and `Frames.close` close a frame. For a frame of a multi-frame application, or a secondary frame of a single-frame one:

- When it is the only open frame of a multi-frame application, closing it is `Lifecycle.requestExit()`: the shutdown asks every veto, the frame's included, and disposes the frame as milestone 1 does. `Frames.close` then returns `false`: the frame closes, if at all, once the shutdown runs.
- Otherwise, the vetoes registered in the frame's scopes are asked, as shutdown asks them ([milestone 1](milestone-1.md#shutdown)): those of its view scope, then those of its window scope, each in the order their instances were constructed. The first that returns `false` cancels the close. Then the frame's window state is saved, the frame is disposed with `Windows.dispose`, its child injector is closed, which runs `@PreDestroy` on the beans of its scopes, and its `Scope` is closed, in the order milestone 1 gives: the view's injector, the view's `Scope`, the window's injector, the window's `Scope`.

The main frame of a single-frame application still requests exit, as in milestone 1, and a dialog's `closeWindow()` is still `cancel()`. A veto in a window or view scope therefore guards its window: closing the window asks it, and so does exiting. No separate close veto exists (see [Changes to the vision](#changes-to-the-vision)).

Closing the last frame always exits, on macOS too, where document applications usually keep running with no window: that needs a default menu bar (`Desktop.setDefaultMenuBar`), and is left for later.

A close requested while a veto of that frame is being asked, or while a shutdown runs, is ignored.

### The active frame

The active frame is the frame that has the focus, else the frame that had it last, else the frame opened last; in a single-frame application with no secondary frame, it is the main frame. The framework follows it with a `WindowFocusListener` on every frame. A frame just shown counts as focused, as the platform focuses it, and so does a frame `toFront` brings forward: the headless frames of the test harness, which never get the focus, follow the same order.

- The OS hooks of macOS's application menu, Quit, About and Preferences, run the standard commands of the active frame's view, where milestone 1 ran the main view's. `PreferencesHandler.showPreferences(Ui)` receives that view's `Ui`.
- The `FailureHandler` shows the error dialog owned by the focused window, else by the active frame.

### Window state of frames

A frame's state is saved when it closes, and on exit if it is still open, as the main frame's is, under the binary name of its view class, as a dialog's is: `[windows."be.imgn.swing.examples.memo.MemoView"]`. The main frame of a single-frame application keeps `[windows.main]`. Every frame saves `maximized`, where milestone 1 saved it for the main frame only.

Placing a frame, at step 7:

- With no saved state, the frame is centered on the [active frame](#the-active-frame), as the vision's window controller centers on the active window; with no frame open, such as the first frame of a multi-frame application, it is centered on the screen. `configureFrame` moving it or asking the platform to place it wins, as for the main frame.
- With saved state, its size is restored, and its location too, unless `restoreBounds()` returns `false`, as for the main frame.
- When another frame of the same view class is open, the frame is placed 24 pixels right of and below the frame of that class opened last, rather than at its saved or centered location, so that frames of one class don't hide each other; its saved size is still restored. A location that would put it outside the usable bounds of that frame's screen is replaced by the screen's top-left usable corner.

Several frames of one class share one entry: the one closed last wins. Each variant of a frame view keeps its own entry, under the class's binary name followed by `.` and the variant's name, as a dialog's variant does.

---

## File choosers

`Dialogs` (UI, `@WindowScoped`) gains two file choosers, owned by its window and modal to it, as its other dialogs are:

```java
Optional<Path> openFile(List<FileType> types);
Optional<Path> saveFile(List<FileType> types, String suggestedName);

public record FileType(String description, List<String> extensions) {}   // UI
```

```java
Optional<Path> file = ui.dialogs().openFile(List.of(new FileType(messages.textFiles(), List.of("txt"))));
```

- **Types.** Each `FileType` is a filter of the chooser, in order, the first one selected; the chooser also offers its "All files" filter. An empty list offers that filter only. A `FileType`'s `description` is shown as it is: it comes from typed messages, so it is translated. Its extensions are given without the dot, matched without regard to case, and copied. `FileType`'s constructor throws `NullPointerException` for a `null` description, list or extension, and `IllegalArgumentException` for no extension, or a blank one or one that holds a `.`.
- **Open.** `openFile` returns the chosen file, or an empty result when the user cancels. It checks nothing about the file: a file that can't be read fails the task that reads it.
- **Save.** `saveFile` starts with `suggestedName` in the name field. A name without one of the selected type's extensions gets its first one, after a `.`; with the "All files" filter, the name stays as typed. A file that already exists asks `confirm(replace(name))`, from the UI's own messages, before the chooser closes: answering no leaves the chooser open. It returns the file, or an empty result when the user cancels.
- **Directory.** Each chooser starts in the directory last chosen in a chooser of the same window, saved in the window's state as `directory`, the absolute path of that directory as a string; else in the user's home directory. A saved directory that no longer exists is ignored.
- **Implementation.** The real `Dialogs` uses `JFileChooser` on every platform, with `showOpenDialog` and `showSaveDialog` on its window.

`Dialogs` also gains the three-way question of the [save prompt](#the-save-prompt):

```java
SaveChoice askSave(String documentName);

public enum SaveChoice { SAVE, DISCARD, CANCEL }                       // UI
```

`askSave` asks `saveChanges(documentName)`, from the UI's own messages, with three buttons: Save, Don't Save and Cancel, the texts of which come from the UI's messages too. Enter is Save, Escape and closing the dialog are Cancel. It returns the button chosen.

The UI's messages, in English and French, gain `replace(name)` ("{name} already exists. Do you want to replace it?"), `saveChanges(name)` ("Do you want to save the changes to {name}?"), `save` ("&Save"), `discard` ("Do&n't Save") and `cancel` ("Cancel").

---

## Undo

```java
@ViewScoped
public final class UndoStack {                                         // UI
    public void add(UndoableEdit edit);
    public Observable<Boolean> canUndo();
    public Observable<Boolean> canRedo();
    public void undo();
    public void redo();
    public void clear();
}
```

- Each view has its own stack, a bean of its scope, built on Swing's `UndoManager` with its default limit of 100 edits. `add` adds an edit, which discards the edits that were undone; `undo` and `redo` undo or redo the next edit, and throw `IllegalStateException` when there is none; `clear` discards every edit. `canUndo()` and `canRedo()` follow the stack. `add` throws `NullPointerException` for `null`. Every method is called on the EDT.
- **Text bindings.** A text component bound with `Form.bind` adds the undoable edits of its `Document` to its view's stack, so undoing a change of the text is a change of the component, which sets the property as the user's changes do and recomputes the form's dirty state: undoing back to the clean text makes the form clean again. The edits made by the binding itself, when the property changes and the binding updates the text, are not added.
- **Commands.** `StandardCommands` gains `undo()` and `redo()`, enabled while the view's `canUndo()` and `canRedo()` are `true`, which undo and redo on the view's stack. Their texts and shortcuts come from the framework's resources: `&Undo` with `shortcut Z`, and `&Redo` with `shortcut shift Z` on macOS and `shortcut Y` elsewhere, in English and French, overridable as any standard command (`[commands.undo]`).

Other components add their own edits with `add`, such as a drawing that adds an edit per stroke.

---

## Documents

A document view edits a file: Memo's editor, `DocumentExample`'s text area. `swing-desktop` gives it new, open, save and save-as commands, the save prompt, a title that follows the file, and recent files.

```java
public final class MemoView implements FrameView, DocumentView<String> {

    private final JTextArea editor = new JTextArea();
    private final Property<String> text = Property.of("");
    private final Form form;
    private final MemoMessages messages;

    @Inject
    MemoView(Form form, MemoMessages messages) {
        this.form = form;
        this.messages = messages;
    }

    @Override
    public JComponent content() {
        form.bind(editor, text);
        return new JScrollPane(editor);
    }

    @Override
    public DocumentFormat<String> format() {
        return DocumentFormat.text(new FileType(messages.textFiles(), List.of("txt")));
    }

    @Override
    public String toSave() {
        return text.get();
    }

    @Override
    public void loaded(String contents) {
        text.set(contents);
    }
}
```

### Document views

```java
public interface DocumentView<T> extends View {                        // desktop
    DocumentFormat<T> format();
    T toSave();                                                        // on the EDT, before a write
    void loaded(T contents);                                           // on the EDT, after a read
    default void saved(Path file) {}                                   // on the EDT, after a write
}

public interface DocumentFormat<T> {                                   // desktop
    FileType fileType();
    T empty();
    T read(InputStream in) throws IOException;                         // on a task's thread
    void write(T contents, OutputStream out) throws IOException;       // on a task's thread

    static DocumentFormat<String> text(FileType fileType);             // UTF-8 text, empty ""
}
```

- A document view is also a `FrameView`, `SingleFrame` included: a dialog view can't be one (see [Build-time checks](#build-time-checks)).
- `format()` is read once, when the view's commands are attached; `read` and `write` run on a task's virtual thread and touch no component; `toSave` and `loaded` run on the EDT. `loaded` puts the contents in the view's components: the framework then marks the view's form clean and clears its undo stack.
- `DocumentFormat.text` reads and writes UTF-8: a read that doesn't decode fails with `MalformedInputException`, an `IOException`.

```java
@ViewScoped
public final class Document {                                          // desktop, one per document view
    public Observable<Optional<Path>> file();                          // empty while untitled
    public Observable<String> name();                                  // the file's name, else untitled()
    public Observable<Boolean> modified();                             // the view's Form.dirty()
}
```

- `Document` is a bean of the view's scope, which the view or any bean of its scopes injects, such as a status bar showing `name()`.
- A document starts untitled, with the contents its view's `content()` built, and is named `untitled()` from the desktop's messages ("Untitled"), followed by ` 2`, ` 3` and so on for the second untitled document open at the same time and the next ones.
- `modified()` is the view's `Form.dirty()`. A view whose contents are not all bound makes its form follow them with `Form.track` ([milestone 2](milestone-2.md#dirty-tracking)).

### Opening and creating documents

```java
public final class Documents {                                         // desktop, singleton
    public <V extends FrameView & DocumentView<?>> void create(Class<V> type);
    public <V extends FrameView & DocumentView<?>> void open(Class<V> type, Path file);
}
```

- `create(type)` opens a frame of an untitled document of the type with `Frames.open`. For the application class of a single-frame application, it asks the [save prompt](#the-save-prompt) when the document is modified, then calls `loaded(format().empty())`, and the document is untitled again.
- `open(type, file)` reads the file, normalized to an absolute path, into a document of the type: if a frame of that type already shows that file, it is brought to the front, and nothing is read. Otherwise a new frame is opened with `Frames.open`, and the file is read into it in a task blocking that window (`Block.WINDOW`); for the application class of a single-frame application, the file is read into the main frame, after the save prompt when its document is modified. Once read, `loaded` is called, the document's file is set, and the file is added to the [recent files](#recent-files). A read that fails shows `openFailed(file, message)` from the desktop's messages with `Dialogs.error`, and removes the file from the recent files when it doesn't exist. The frame opened for it is then closed without asking its vetoes, unless it is the only open frame of a multi-frame application: it then stays, with an untitled document, rather than exiting. The main frame of a single-frame application keeps the document it had.
- Both are called on the EDT.

### Commands

`DocumentCommands` (desktop, `@ViewScoped`) holds the commands of a document view, built through the view's `CommandRegistry` as the standard commands are, and injected like any generated `<Class>Commands`. Like those, it extends `StandardCommands`, so that a document view's menus need no other commands class. `CommandRegistry` (UI) gains `CommandBuilder overridableCommand(Class<?> owner, String id)`, which builds a command whose resources are the owner's, overridden by the view class's `[commands.<id>]` table, as the standard commands' are:

| Command | Does |
|---|---|
| `newDocument()` | `Documents.create` of the view's class |
| `open()` | `Dialogs.openFile(List.of(format().fileType()))`, then `Documents.open` of the view's class with the chosen file |
| `save()` | Writes the document to its file, or, while untitled, does what `saveAs()` does. Enabled while the document is modified or untitled |
| `saveAs()` | `Dialogs.saveFile(List.of(format().fileType()), name)`, then writes the document to the chosen file |
| `MenuEntry recentFiles()` | A [dynamic entry](milestone-1.md#dynamic-entries) of the recent files, numbered with `DynamicEntry.numbered`, each named by the file's name and opening it with `Documents.open` of the view's class |

- A write calls `toSave()` on the EDT, then, in a task blocking the window (`Block.WINDOW`), writes the contents with gaia's `AtomicFiles.write(Path, Output)`, so that a failed write leaves the previous file whole. Once written, the document's file is set, the form is marked clean, the file is added to the recent files, and the view's `saved(file)` is called. A write that fails shows `saveFailed(file, message)` with `Dialogs.error`, and the document stays modified.
- The reads, the writes the save prompt starts, and the writes of the recent files run as tasks of commands do, so that the test harness waits for them as it waits for a command's.
- Their texts and shortcuts come from the desktop's own resources, in English and French: `&New` (`shortcut N`), `&Open…` (`shortcut O`), `&Save` (`shortcut S`) and `Save &As…` (`shortcut shift S`). A document view overrides them in its own resources (`[commands.save]`), as it overrides standard commands, and the build checks them (see [Build-time checks](#build-time-checks)).

### The save prompt

A `Document` registers an `ExitVeto` in its view's scope, so that closing its frame and exiting ask it, as [closing frames](#closing-frames) describes. While its document is modified, it asks `Dialogs.askSave(name)` of its window:

- `DISCARD` lets the close or the exit go on.
- `CANCEL` stops it.
- `SAVE` stops it, and starts the write that `save()` does. Once the write succeeds, the framework requests again what the veto stopped: the frame's close, or the exit with the status first requested, which asks the other vetoes again. A save-as chooser that the user cancels, or a write that fails, requests nothing.

`Documents.create` and `open` into a single-frame application ask the same prompt, with the same three answers, before replacing the document.

### Title

The title of a document view's frame follows its document: `documentTitle(document, application)` from the desktop's messages ("{document} – {application}"), with the document's `name()` and the displayed name. A document view's `title` resource isn't used, and the processor rejects it.

- On macOS, the root pane's client property `Window.documentModified` follows `modified()`, which shows the dot in the close button, and `Window.documentFile` is the document's file, which shows its proxy icon.
- Elsewhere, the title starts with `*` while the document is modified.

### Recent files

```java
public final class RecentFiles {                                       // desktop, singleton
    public ObservableList<Path> files();                               // most recent first
    public void add(Path file);
    public void remove(Path file);
    public void clear();
}
```

- `add` puts the file, normalized to an absolute path, first, removing it from further down, and keeps the ten most recent. `remove` and `clear` remove one file or all of them.
- The list is saved to `recent-files.toml` in `ApplicationDirectories.state()`, as `files = ["/path/one", ...]`, written atomically after each change, in a task, from the record `RecentFileList(List<String> files)` through toml's generated writer. A startup step of order 250 loads it, ignoring unknown keys; a file that doesn't bind is discarded with a warning, and the list starts empty.
- Opening a recent file that no longer exists shows the error, as any failed read does, and removes it from the list.

---

## Single instance

Launching a single-instance application a second time forwards its arguments, such as files to open, to the instance already running, and exits.

```java
public interface SecondInstanceHandler {                               // desktop
    void launched(Arguments arguments);                                // on the EDT, in the first instance
}
```

- **Enabling.** An application is single-instance when it has a `SecondInstanceHandler` bean, a singleton, as `preferences()` is enabled by a `PreferencesHandler` bean. Without one, nothing below runs.
- **Detection.** A startup step of order 10, before the resources (100), runs on the main thread. In the state directory, which it creates when missing, it opens `instance.lock` and takes an exclusive lock on it with `FileChannel.tryLock()`. The lock identifies the instance per user and per application, since the state directory is both.
- **First instance.** When it gets the lock, it keeps it until the root injector closes. It binds a `ServerSocket` to the loopback address on a port the system chooses, then writes `instance.toml` atomically, holding `port` and `token`, 32 random bytes from `SecureRandom` in Base64. On POSIX file systems, the file is readable by its owner only. A virtual thread accepts connections; each one is read as the request below. A request with the right token is answered `ok`, and its arguments are handed to `launched` on the EDT, once the application is ready, in the order they arrived; one with another token, or one that doesn't parse, is closed without an answer, and logged at `WARNING`. The server and the lock are closed by a shutdown step of order 300, after the windows.
- **Second instance.** When the lock is held, the step reads `instance.toml`, connects to the port, writes the request, a TOML document with `token` and `arguments`, the launch's arguments as an array of strings, shuts its output down, and waits for `ok`. It then calls `Exit.exit(0)`: no further step runs, and no window is shown.
- **Races.** A held lock whose `instance.toml` is missing, whose port refuses the connection or which doesn't answer, is that of an instance starting or exiting: the step tries the lock and the request again every 100 ms, for up to 5 seconds. It then fails the startup with an `IllegalStateException` saying that another instance holds the lock and doesn't answer.
- **Transport.** A loopback TCP socket with a token works on every system. A Unix domain socket would need neither, but its path is limited to about 100 bytes on macOS, which a deep state directory can exceed; a watched folder has no acknowledgement, and the JDK polls it on macOS.
- **Where it lives.** The lock, the server and the request are internal to `swing-desktop`. They have nothing of Swing, but they stay out of gaia: only `SecondInstanceHandler` is public.
- The handler decides what a second launch does, such as opening its files, or a new document and bringing the active frame to the front.

`Frames` and `Documents` are what a handler uses:

```java
@Singleton
final class MemoLauncher implements SecondInstanceHandler {
    @Override
    public void launched(Arguments arguments) {
        if (arguments.values().isEmpty()) {
            documents.create(MemoView.class);
        } else {
            arguments.values().forEach(file -> documents.open(MemoView.class, Path.of(file)));
        }
    }
}
```

---

## OS hooks

### Open-file events

```java
public interface OpenFilesHandler {                                    // desktop
    void open(List<Path> files);                                       // on the EDT
}
```

When the application has an `OpenFilesHandler` bean, a singleton, and `Desktop` supports `APP_OPEN_FILE`, a startup step of order 1000, the last, sets `Desktop.setOpenFileHandler`, before the first window is shown. Each event's files, as absolute paths, are handed to `open` on the EDT, once the application is ready, in the order the events came. macOS sends these events when the user opens a file of the application from the Finder or drops it on the Dock icon; on the other systems, files come as arguments.

### Browsing

```java
public interface SystemShell {                                         // UI, singleton
    boolean canBrowse();
    void browse(URI uri);
    boolean canOpen();
    void open(Path file);
}
```

`SystemShell` asks the system to show a URI in the browser, or a file in the application that the system associates with it, through `Desktop.browse` and `Desktop.open`. `canBrowse()` and `canOpen()` tell whether `Desktop` supports `BROWSE` and `OPEN`; `browse` and `open` then run in a task of the application scope, through `TaskService.run`, so that shutdown cancels it, and a failure goes to the `FailureHandler`. Without support, they throw `UnsupportedOperationException`. The UI module provides it as a `@Provides @Default` bean; `AppTester` replaces it with a fake that records the calls.

The About dialog shows the website as a link when `canBrowse()` is `true`: a `JButton` without border, content area or focus painting, whose text is the website, underlined, in the look and feel's link color (`Component.linkColor`, else blue), with a hand cursor, which browses the website when activated. Without support, it stays milestone 2's read-only text field.

### Taskbar

```java
public interface TaskbarStatus {                                       // UI, singleton
    void progress(int percent);
    void clearProgress();
    void badge(String text);
    void clearBadge();
    void requestAttention();
}
```

- `progress` shows a progress of 0 to 100 on the application's taskbar entry or Dock icon, and throws `IllegalArgumentException` outside that range; `clearProgress` removes it. `badge` shows a short text on it, such as a count, and throws `IllegalArgumentException` for a blank text; `clearBadge` removes it. `requestAttention` asks the system to draw the user's attention to the application, such as bouncing the Dock icon once.
- They may be called from any thread, and are applied on the EDT. Each uses the features of `java.awt.Taskbar` the system supports: `PROGRESS_VALUE`, else `PROGRESS_VALUE_WINDOW` on the active frame; `ICON_BADGE_TEXT`, else nothing; `USER_ATTENTION`, else `USER_ATTENTION_WINDOW` on the active frame. A feature the system lacks makes the call do nothing, with one `DEBUG` log per feature.
- `TaskbarStatus` is in `swing-ui`, beside `SystemShell`, since tasks drive it (below). The UI module provides it as a `@Provides @Default` bean; `AppTester` replaces it with a fake.
- **Task progress.** While a task blocks the application (`Block.APPLICATION`), the taskbar shows the progress that the glass pane's indicator shows, as `progress(Math.round(fraction * 100))`, from the task's start, without the indicator's delay. An indeterminate progress (`-1`), or none yet, clears it. When the task ends, however it ends, the progress is cleared. With several such tasks, the taskbar follows the one the glass pane's Cancel cancels, the one that blocked last; a chain is followed step by step, as its indicator is. A progress the application sets itself while such a task runs is replaced at the task's next progress.

### Notifications

```java
public interface Notifications {                                       // desktop, singleton
    boolean isSupported();
    void notify(String caption, String text);
}
```

`notify` shows a system notification with a caption and a text, from any thread. The JDK shows notifications only through the system tray: at the first notification, the framework adds a `TrayIcon` with the application's icon and displayed name to the `SystemTray`, and shows each notification with `displayMessage(caption, text, MessageType.INFO)`. A shutdown step of order 310 removes the icon. `isSupported()` is `SystemTray.isSupported()`; without support, `notify` logs the caption and the text at `INFO`, and shows nothing.

### The application's icon

The application's `icon` ([milestone 2](milestone-2.md#about-dialog-and-application-icon)) is set on every window: the main frame, as in milestone 2, every frame that `Frames` opens, and every dialog. At startup step 4, before the first window is created, it is also set with `Taskbar.setIconImage` when the system supports `ICON_IMAGE`, such as on macOS, where the Dock shows it instead of Java's icon. An icon that gives no image, as milestone 2 describes, sets none, with the warning logged once.

---

## Default application name

`@Application.name` becomes optional, `String name() default ""`: an empty name is the class's simple name, `MemoApp` for `MemoApp`, which the processor writes in the descriptor. The processor checks the name it uses, given or derived, with gaia's rules, as in milestone 1; a simple name always passes. The displayed name is still the `name` resource key, else the name.

---

## Changes to milestones 1 and 2

These change what milestones 1 and 2 specify, from milestone 3 on:

- **The application class.** The `@Application` class implements `SingleFrame` or `MultiFrame`, where milestone 1 required `SingleFrame` (see [Multi-frame applications](#multi-frame-applications)). `@Application.name` is optional (see [Default application name](#default-application-name)).
- **`SingleFrame`** extends `FrameView`, which takes its `menuBar()`, `toolBar()`, `configureFrame` and `restoreBounds()`.
- **`closeWindow()`** closes a frame that `Frames` opened, after asking the vetoes of its scopes, instead of doing nothing outside the main frame and dialogs; closing the last frame of a multi-frame application requests exit (see [Closing frames](#closing-frames)).
- **Vetoes.** A veto in a window or view scope is also asked when its window closes, where milestone 1 asked vetoes only on exit.
- **The active frame** replaces the main view for macOS's application menu and as the error dialog's owner (see [The active frame](#the-active-frame)).
- **Window state.** Frames are saved under their view class's binary name; every frame saves `maximized`; every window saves the `directory` of its file choosers. `SavedWindow` gains `@Nullable String directory`.
- **`Window`** gains `toFront()`.
- **`@Variants`** marks an enum nested in a dialog view or a frame view, where milestone 1 allowed dialog views only; `Frames.open` opens a frame view's variants (see [Opening frames](#opening-frames)).
- **Tasks** that block the application show their progress on the taskbar (see [Taskbar](#taskbar)).
- **`Dialogs`** gains `openFile`, `saveFile` and `askSave` (see [File choosers](#file-choosers)).
- **`StandardCommands`** gains `undo()` and `redo()`, and the text bindings of `Form` feed the view's `UndoStack` (see [Undo](#undo)).
- **The About dialog** shows the website as a link when the system can browse (see [Browsing](#browsing)).
- **The application's icon** is set on every window and on the taskbar (see [The application's icon](#the-applications-icon)).

---

## Build-time checks

Milestone 3 adds these checks to those of milestones [1](milestone-1.md#build-time-checks) and [2](milestone-2.md#build-time-checks), with diagnostics attached to the Java element as there:

| Check | Error when |
|---|---|
| Application class | The `@Application` class implements neither `SingleFrame` nor `MultiFrame`, or both; or implements `MultiFrame` and declares `@WindowScoped` or `@ViewScoped` |
| Document views | A class that implements `DocumentView` doesn't implement `FrameView`; or its resources have a `title` key |
| Variants | Milestone 1's variants check, with "a dialog view" read as "a dialog view or a frame view" |
| Document commands | A `[commands.<id>]` table of a class that implements `DocumentView` names none of its commands, of `StandardCommands`, or of `newDocument`, `open`, `save` and `saveAs` |

The commands' check of `[commands.<id>]` tables also accepts `undo` and `redo`, as standard commands. The class-properties check accepts no `title` on a document view, as the table above says.

---

## Processor

The application step reads `name` as optional, and checks the application class against `SingleFrame` and `MultiFrame`; milestone 1's error, "must implement SingleFrame in milestone 1", is removed. The resources step knows the ids of the document commands, which it accepts in the resources of document views only. `swing-processor` adds no step and generates nothing new: `DocumentCommands` is a class of `swing-desktop`, not generated.

---

## Test harness

```java
@Test
void savesANewNoteUnderTheChosenFile() {
    try (AppTester<MemoApp> app = AppTester.start(MemoApp.class)) {
        OpenFrame<MemoView> note = app.frame(MemoView.class);
        app.run(() -> note.component(JTextArea.class).setText("Buy milk"));
        Path file = app.bean(ApplicationDirectories.class).data().resolve("shopping.txt");
        app.dialogs().answerSaveFile(file);

        note.commands(DocumentCommands.class).save().execute();

        assertThat(file).hasContent("Buy milk");
        assertThat(note.title()).isEqualTo("shopping.txt – Memo");
    }
}
```

`AppTester` gains:

| Method | Does |
|---|---|
| `List<OpenFrame<?>> frames()` | The open frames, in opening order, the main frame of a single-frame application first |
| `<V extends FrameView> OpenFrame<V> frame(Class<V> type)` | The one open frame whose view is of the type; `IllegalStateException` when there is none or more than one |
| `void launchAgain(String... arguments)` | Does in the running application what a second launch with these arguments forwards: calls the `SecondInstanceHandler` on the EDT, then settles. `IllegalStateException` without a handler |
| `void openFiles(Path... files)` | Hands the files to the `OpenFilesHandler` on the EDT, as an open-file event does, then settles. `IllegalStateException` without a handler |
| `FakeShell shell()` | The fake `SystemShell`, which supports both actions and records the URIs browsed and the files opened: `browsed()`, `opened()`. `supportBrowsing(false)` makes it a system that can't browse, for the About dialog's read-only field |
| `FakeTaskbar taskbar()` | The fake `TaskbarStatus`, which records the current state: `OptionalInt progress()`, `Optional<String> badge()`, `int attentionRequests()`. It is in `swing-ui`'s harness, so every application has it |
| `FakeNotifications notifications()` | The fake `Notifications`, supported, which records each notification: `List<Notification> notified()`, `record Notification(String caption, String text)` |

`OpenFrame<V>` (testing) is to a frame what `OpenDialog` is to a dialog: `view()`, `commands(Class)`, `bean(Class)`, `component(...)` and `components(...)`, `title()`, `iconImage()`, `focused()`, `isOpen()`, and `boolean close()`, which runs the view's `closeWindow()` on the EDT, settles, and returns whether the frame closed. In a multi-frame application, the methods of `AppTester` that act on the main frame, `commands`, `component`, `components`, `title`, `iconImage` and `focused`, act on the [active frame](#the-active-frame); `application()` returns the application class's singleton; `bean` resolves through the active frame's view injector.

`FakeDialogs` gains:

| Method | Does |
|---|---|
| `answerOpenFile(Path answer)`, `answerSaveFile(Path answer)`, `cancelFileChooser()` | Queues the answer of the next `openFile` or `saveFile`, as `answerConfirm` queues confirmations: the file chosen, or a cancel, which the chooser returns as an empty result. `saveFile`'s answer gets the type's extension and the replace question as the real chooser does, the question answered with the next `answerConfirm` |
| `answerSave(SaveChoice answer)` | Queues the answer of the next `askSave`; a prompt with no answer queued is answered `DISCARD`, as a confirmation with none is answered yes |
| `List<FileChoice> fileChoices()` | The choosers shown, in order: `record FileChoice(Kind kind, List<FileType> types, @Nullable String suggestedName)` (testing), `null` for an open chooser, with `enum Kind { OPEN, SAVE }` nested in it |
| `List<String> savePrompts()` | The document names of the save prompts asked, in order |

- `swing-testing` depends on `swing-desktop` as an optional dependency, `requires static transitive be.imgn.swing.desktop`, transitive since `FakeNotifications`, public, implements a type of it: the tester binds the fakes of `Notifications` and the single instance's transport only when `swing-desktop` is on the path, and `notifications()`, `launchAgain` and `openFiles` throw `IllegalStateException` without it, with a message that names the `swing-desktop` dependency to add. A separate `swing-desktop-testing` module would avoid these methods in applications without `swing-desktop`, but would need an extension point in `swing-testing` to bind its fakes.
- **Finding `swing-desktop`.** The tester never uses reflection, such as `Class.forName`, to find out whether `swing-desktop` is present: as the vision requires, the only runtime discovery is `ServiceLoader`. `swing-ui` declares an internal service, `be.imgn.swing.ui.internal.TesterExtension`, in the package it already exports to `swing-flatlaf` and `swing-testing` only, and exports it to `swing-desktop` too; `swing-desktop` provides it, and binds its fakes through it; `swing-testing` uses it, and touches no type of `swing-desktop` when it finds none. It is not public API.
- The tester's single instance never opens a socket or a lock: the detection step finds no other instance, and `launchAgain` stands for the forwarding. The real transport is tested in `swing-desktop`'s own tests, with two lock holders in one JVM, where `tryLock` throws `OverlappingFileLockException`, which the step takes as a held lock.
- **Mnemonics of the save prompt.** Tests answer `askSave` through `FakeDialogs`, which builds no dialog. `swing-ui`'s own tests build the real `askSave` dialog's buttons from each locale's texts, and check that their mnemonics differ, and that macOS sets none; the option pane around them needs a display.
- **Tests that need the harness.** The taskbar progress of tasks blocking the application, and multi-frame applications, are tested in `swing-examples`' fixture tests (`frames`), since `swing-ui`'s own tests can't use the harness, which depends on it. The placement of frames, centering and cascading, needs real windows, which headless tests don't have: it is checked by hand.
- The headless frames record `toFront()`, which `OpenFrame` exposes as `int toFrontCount()`, and the client properties of document views, which tests read through `component(JRootPane.class)`.

---

## Upstream changes

Milestone 3 needs none. It relies on this existing behavior:

- gaia's `AtomicFiles.write(Path, Output)` writes through a temporary file in the same directory and moves it into place, so a failed write leaves the previous file whole.
- gaia's `ApplicationDirectories.state()` is per user and per application, which makes the single instance's lock per user and per application.
- toml's generated writers and its binding that ignores unknown keys, for the recent files, as for the settings of milestone 2.

---

## Changes to the vision

These depart from the [vision](vision.md), and are to be applied to it:

- **Starting a multi-frame application.** The vision's sample starts with `@Startup void start(Windows windows, Views views)` and `windows.showMain(views.create(NotesView.class))`. The application class implements `MultiFrame` instead, whose `start(Frames, Arguments)` opens the first frames: an interface, as the vision prefers for hooks. `Windows` stays the factory of milestone 1, and no window is the main one.
- **Close vetoes.** The vision's `closeWindow()` "asks the window's close vetoes". There is no close veto type: the `ExitVeto`s of a window's scopes are asked, so that a view guards both its window and the exit with one veto.
- **Modules.** Multi-frame applications, file choosers, undo, browsing and the taskbar are in `swing-ui`, since views, dialogs, standard commands and tasks are; `swing-desktop` holds single instance, open-file events, notifications, documents and recent files.
- **Notifications** go through the system tray, the only notifications the JDK has.
- **The taskbar** is in `swing-ui`, where the vision's module table puts OS integration in `swing-desktop`: tasks, which are in the UI layer, drive its progress.
- **The test harness's modules.** `swing-testing` depends on `swing-desktop` as an optional dependency, beyond the vision's "core, ui, gaia testing libraries, gaia-memfs" (see [Test harness](#test-harness)).
- **The sample's save.** The vision's `NotesView` saves with its own `@Command save()`, which runs a `Task` through a `NoteDocument`. A document view gets its save from `DocumentCommands` instead, with the save prompt, save-as and recent files, and `DocumentView.saved` replaces the task's `onSuccess`. Its resources still override `[commands.save]`, as the vision's do.
- **Placing frames.** The vision's window controller centers on the active window, as a new frame is; but a frame opened while another of its class is open is cascaded from it instead, so that they don't hide each other (see [Window state of frames](#window-state-of-frames)).
- **`@Application.name`** is optional, and defaults to the class's simple name; the vision's examples give it every time.

---

## Acceptance

`./mvnw -B verify` succeeds on JDK 21 and on JDK 25, with the libraries of milestone 2. Every test of milestones 1 and 2 still passes, except those of the behavior milestone 3 changes, which milestone 3's tests replace: those of `DocumentExample` that set its file through `file(Path)`. `swing-examples` depends on `swing-desktop`, and has the following, each with `AppTester` tests of every behavior listed.

### Memo, as in the vision

Memo is the vision's [sample application](vision.md#a-sample-application), in a new package, `be.imgn.swing.examples.memo`: a multi-frame document application, a note per frame. The vision calls it Notes; milestone 2's Notes keeps that name and stays as it is, a single-frame application with its `notes.txt`. The vision's `@Subscribe` to font-size changes is left out: Notes already shows the editor following its settings.

- **`MemoApp`**, `@Application(name = "Memo", vendor = "imgn", qualifier = "be")`, implements `MultiFrame`: `start` opens each argument as a note, with `Documents.open(MemoView.class, ...)`, or an untitled note when there is none.
- **`MemoView`**, a `FrameView` and `DocumentView<String>`, holds the editor, bound with `Form`, and a status label below it. Its menu bar has File (`newDocument`, `open`, `recentFiles`, a separator, `save`, `saveAs`, a separator, `closeWindow`, `exit`), Edit (`undo`, `redo`, a separator, `cut`, `copy`, `paste`, `selectAll`) and Help (`about`).
- **Single instance.** A `SecondInstanceHandler` opens the files of a second launch, or an untitled note when it has none, and brings the active frame to the front. An `OpenFilesHandler` opens the files it receives. The examples share one module, whose beans every example has: both handlers act in Memo only, and the arguments are paths of the application's file system, the one its directories are on.
- **Resources, as in the vision.** `MemoView.toml` overrides `[commands.save]` with the vision's `text`, `icon = "icons/save.svg"`, `shortcut` and `tool-tip-text`, and gives `[editor]` its `font` and `accessible-name`, in English and French. `MemoMessages` has the vision's `saved(String file)` and `words(int count)`, with the vision's English and French texts. The status label shows `words(count)` of the editor's text, and, from `saved(file)` until the next change of the text, `saved` with the file's name.
- **Taskbar.** The taskbar's badge shows the number of modified notes while there is at least one.
- **About.** The About dialog shows the website as a link. Its resources, `MemoApp.toml` and `MemoMessages.toml`, have French variants, as Notes' do.

Its tests check, at least: startup with no argument opens one untitled note, and with two files opens both; a note is saved under the file `answerSaveFile` gives, with the extension added, and the title then shows it; `save` of a titled note writes without a chooser; saving over an existing file asks the replace question; opening a file shows it in a new frame, and opening it again brings that frame to the front; the recent files follow opens and saves, survive a restart on the same file system, and opening a missing one shows the error and removes it; closing a modified note asks `askSave`, and each answer does what it says, `SAVE` closing the frame once written; exiting with two modified notes asks for each; closing the last note exits; typing then `undo` restores the text and makes the note clean again, and `redo` modifies it again; `launchAgain()` opens an untitled note, and `launchAgain(file)` opens the file; `openFiles` opens the files; the badge counts the modified notes; the frames' icon image is set; the About dialog's website link browses it, as `shell().browsed()` shows; the save command has the vision's icon and tooltip; the status label counts the words, in English and French, and shows `saved` after a save until the next change; a new frame is centered on the active frame, and a second frame of the same class is cascaded.

### The other ports

- **`notes/NotesApp`** stays the single-frame application of milestone 2, its `notes.txt` and its tests included; it keeps `name = "Notes"`, which isn't its class's simple name.
- **`saf/DocumentExample`** becomes a single-frame `DocumentView<String>`, whose open, save and save-as are `DocumentCommands`; its exit veto, its `file` property and `file(Path)` are removed, and its title follows the document. Its tests answer the choosers.
- **`saf/SingleFrameExample6`** opens its three secondary windows as frames, as the original does, where milestone 1 showed them as dialog views: `SecondaryWindow` becomes a `FrameView` with its `@Variants` enum, opened with `Frames.open(SecondaryWindow.class, variant)`, with no Cancel button. Each command opens a new frame of its variant.
- **`saf/SingleFrameExample4`** chooses its file with `Dialogs.openFile`; its `JFileChooser` and its window ancestor lookup are removed.
- **`addressbook/AddressBookApp`** gives its icon with the `icon` resource; its `configureFrame` is removed. Its three views stay one view, the application class, until docking (milestone 5): milestone 1 left them for milestones 3 and 4, and the original shows them docked.
- **Default names.** Every example whose `@Application.name` is its class's simple name drops it.

Their tests check, at least: `DocumentExample` opens the file `answerOpenFile` gives, saves to it, and its exit asks the save prompt only while modified; `SingleFrameExample4` loads the file chosen; `SingleFrameExample6` opens each variant in a frame with its title and label, keeps each variant's window state apart, and closing a secondary frame leaves the application running; the Address Book's frame has the icon image; each application's name is its class's simple name where it dropped `name`.

### Desktop without the examples

`swing-desktop`'s own tests check the single instance's transport with two instances in one JVM, the forwarding of arguments, a wrong token, and the races; `Notifications` against a fake of the JDK's `SystemTray` adaptor; and `RecentFiles`' file. `swing-ui`'s own tests check `TaskbarStatus` against a fake of the JDK's `Taskbar` adaptor; the taskbar progress of a task blocking the application, of an indeterminate one, of two at once and of a chain, and its clearing however the task ends; and the mnemonics of the real `askSave` dialog.

---

## Order of work

Each step adds the harness methods its own tests need.

1. Build: the `swing-desktop` module; processor: the default name and the application class's check.
2. UI: `FrameView`, `MultiFrame`, `Frames`, closing frames, vetoes on close, the active frame, the window state of frames, `Window.toFront`; the harness's `frames()`, `frame` and `OpenFrame`.
3. UI: the icon on every window and the taskbar; `SystemShell` and the About dialog's link; `TaskbarStatus` and the progress of tasks blocking the application; frame views' variants; the harness's `shell()` and `taskbar()`.
4. UI: file choosers and `askSave`, with the `directory` state; the harness's answers.
5. UI: `UndoStack`, `undo()` and `redo()`, the text bindings' edits.
6. Desktop and processor: documents, `DocumentCommands`, the save prompt, the title, recent files, and their checks.
7. Desktop: single instance; the harness's `launchAgain`.
8. Desktop: open-file events and notifications; the harness's fakes, and `TesterExtension`.
9. The acceptance examples.

