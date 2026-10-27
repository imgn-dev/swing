# swing-app: vision

swing-app is a framework for desktop applications in Swing, on Java 21. Its ideas come from two earlier frameworks:

- **Guts** (`ogregoire/guts`): Guice-based, with a typed event bus, a window pipeline, tasks with input blocking, and docking.
- **SwingAppFramework** (`ogregoire/SwingAppFramework`, a JSR-296 successor): property-file-driven resources, live locale switching, and a lifecycle you can extend.

Both projects were built around the limits of their era: Guts around what Guice could see at runtime, SwingAppFramework around what `.properties` files could express. This framework drops most of that machinery and spends the effort on what desktop applications need today.

It builds on three libraries:

| Library | Used for |
|---|---|
| [`be.imgn.inject`](https://github.com/imgn-dev/inject) | Dependency injection. Factories are generated at compile time and need no reflection. |
| [`be.imgn.toml`](https://github.com/imgn-dev/toml) | Resources, settings and saved state, all in TOML. Records are bound to TOML by generated code. |
| [gaia](https://github.com/imgn-dev/gaia) | `gaia-core`: collections, I/O, atomic files, platform directories, naming conventions, timing. `gaia-processing`: the base of the annotation processor. The testing libraries and `gaia-memfs` for tests. |

Applets and other legacy deployment models are out of scope.

---

## Principles

1. **The build catches mistakes.** Resource keys, command ids, message placeholders and missing translations are all checked at compile time. A typo fails the build instead of logging a warning at runtime.
2. **Explicit, with generated boilerplate.** Nothing is scanned at runtime and no listener is held through a weak reference that may silently disappear. The wiring is ordinary generated code that you can read in `target/generated-sources`.
3. **No reflection.** One annotation processor generates what the older frameworks discovered by reflection. The runtime only looks up generated code by `Class`. The only runtime discovery is `ServiceLoader`.
4. **Structure in code; text and tuning in TOML.** Text, icons, shortcuts, mnemonics, accessible names, colors and timings go in resources, where translators and designers can edit them. Layout and component trees stay in Java, where refactoring tools work.
5. **A core without Swing, for what is useful outside a UI.** Lifecycle, scopes, observable state, tasks, events, settings, resources and messages never touch Swing. A domain module shared with a command-line tool, a server or batch jobs can use them without requiring `java.desktop`, and the module graph guarantees that it stays free of Swing. Commands are a UI concept, with text, icons, shortcuts and menus, so they belong to the UI layer.
6. **Everything has an owner.** Every subscription, listener, binding and task belongs to a scope (application, window or view). Closing the scope releases them.
7. **The module path comes first.** The framework works on the module path as well as the classpath, and is packaged with `jlink` / `jpackage`.

---

## Modules

The names below are working names.

| Module | Contents | Depends on |
|---|---|---|
| `swing-app-core` | Lifecycle, scopes, observables, tasks, events, settings, resources, messages | inject, toml, gaia-core |
| `swing-app-processor` | Annotation processor (see [What the processor generates](#what-the-processor-generates)) | gaia-processing, toml |
| `swing-app-ui` | Views, windows, commands, resource injection into components, binding, input blockers, dialogs | core |
| `swing-app-desktop` | Single instance, OS integration, documents, recent files | ui |
| `swing-app-flatlaf` | FlatLaf themes, dark mode, SVG icons, theme tokens | ui |
| `swing-app-testing` | Test harness | core, ui, gaia testing libraries, gaia-memfs |
| `swing-app-wizard` (optional) | Multi-step dialogs | ui |
| `swing-app-docking` (optional) | DockingFrames integration | ui |

---

## Features

### Core (no Swing)

These features need nothing from Swing, so a module that doesn't require `java.desktop` can use them.

- **Application lifecycle.**
  - Ordered startup steps, then a *ready* signal once the event queue is idle.
  - Shutdown asks the exit vetoes, publishes a shutdown event, then calls `injector.close()`, which runs every `@PreDestroy`.
  - Steps and vetoes are contributed with inject's `@IntoSet`.
- **Scopes.**
  - The scopes are application, window and view.
  - A scope owns a disposal list. Subscriptions, bindings, listeners and running tasks registered in it are released when it closes.
  - This replaces Guts' weak-referenced consumers.
- **Observable state.**
  - Typed `Property<T>`, computed values (`Observable.map`, `combine`) and observable lists.
  - These replace string-named `PropertyChangeSupport`.
  - State is modelled with records and sealed types.
- **Tasks.**
  - Run on virtual threads.
  - Progress, messages and cancellation (by interrupt) are delivered on the EDT.
  - Completion hooks: succeeded, failed, cancelled.
  - In the UI layer, a command that returns a `Task` is executed for you, with an input-blocking scope: none, the command, the component, the window, or the application.
- **Events.**
  - A small typed bus: `@Subscribe` methods, with an optional topic and a thread policy (`@OnEdt`, `@Background`).
  - The subscription belongs to the subscriber's scope.
  - Consumers can be package-private: generated code calls them.
- **Settings.**
  - `@TomlConfig` records, loaded from and saved to the application's config directory.
  - Writes are atomic.

### Resources and internationalization

- **Typed messages.**
  - You write an interface, the processor checks it against the TOML file, and it generates the implementation.
  - A placeholder that doesn't match a parameter fails the build.
  - So does a key that is missing in any locale file.
- **Typed resource keys.** Generated constants for icons, so code never looks up resources by string. Commands are reached through their generated `<Class>Commands` class instead.
- **Resource injection into components.**
  - Per-class TOML files (`NotesView.toml`, `NotesView_fr.toml`, `NotesView.macos.toml`) are merged along the class hierarchy.
  - Keys are kebab-case properties. Generated accessors write them to the components the class holds.
  - Values use native TOML types where they fit. Strings carry colors, fonts, shortcuts and `${references}`.
- **Live locale switching.** Changing the locale re-injects every open window and refreshes every command and message.
- **Hot reload in development.** The same mechanism reloads a `.toml` file when it changes on disk, so you can edit a label, save, and see it in the running application.
- **Accessibility.**
  - Accessible names and descriptions come from resources.
  - Duplicate mnemonics are checked when menus are built from their descriptions, and in dialogs by the test harness: the build can't see menus built in code.

### UI layer (Swing)

- **Views.**
  - A view is a component plus its commands and its scope.
  - Views are created by the framework, which is how they get their scope and their wiring.
- **Windows.**
  - The window controller handles size and position, centering on the active window, close vetoes, and the main-window lifecycle (single-frame or multi-frame).
  - Window state is saved and restored: bounds, split panes, table columns, selected tabs.
- **Single-frame applications.**
  - An application class that implements `SingleFrame` needs no view class: it acts as the only view of the main window.
  - Its `@Command` methods, `@Subscribe` methods and resources work as in any view.
  - `SingleFrame` has default methods for the hooks: `content()`, `menuBar()` and `configureFrame(JFrame)`.
  - The application class can also implement `ExitVeto`, which is asked by the `exit` command, the window's close button and the operating system's Quit.
  - It is an interface rather than a base class: the hooks stay discoverable and checked by the compiler, and no framework state lives in a superclass that the constructor could reach too early.
  - The shortcut is a thin layer over views. Growing to several windows means moving the content and its commands into a view class: a refactor, not a rewrite.
- **Commands.**
  - Commands replace the action models of both older frameworks.
  - A command has an id, a handler, an enabled state derived from observables, and optionally a selected state.
  - Text, icon, shortcut and tooltip come from resources.
  - Commands are declared with `@Command` on methods and collected by the processor.
  - The id defaults to the method name: `void increment()` declares the command `increment`.
  - The id is also the command's TOML key, spelled exactly the same: `selectAll` is configured under `[commands.selectAll]`, and an explicit `@Command(id = "select-all")` under `[commands.select-all]`.
  - Menus, toolbars, buttons, popups, the command palette and the keymap all read from one command registry.
  - Code never refers to a command by string. It goes through the generated `<Class>Commands` class (see [Commands in the UI](#ui-layer-swing)).
  - Undo/redo plugs into commands.
- **Commands in the UI.**
  - For each class that declares commands, the processor generates a `<Class>Commands` class. It has one method per command, named after its handler method, that returns a `BoundCommand`.
  - The generated class is a bean: it is injected into the constructor of the class that declares the commands, or of any other class that uses them. It holds the UI services, so creating a component needs nothing else.
  - It calls the handlers through an inject `Provider<T>` of the declaring class, which breaks the cycle between the two constructors: inject defers a `Provider` edge past construction.
  - `Provider.get()` resolves again on every call, and the bean's scope decides what it returns. The declaring class must therefore be scoped (a singleton, or the window or view scope), so that the handlers run on the instance that received the commands and not on a new one. A declaring class without a scope is a compile error.
  - The generated class takes the declaring class's scope, or the view scope when the declaring class is a singleton.
  - A `BoundCommand` turns its command into Swing objects:

    | Method | Returns |
    |---|---|
    | `id()` | The command id, which is also the action command of every component created for it |
    | `createButton()`, `createMenuItem()` | A new component on each call, since a component has only one parent |
    | `createToggleButton()`, `createCheckBoxMenuItem()` | The same, for commands with a selected state |
    | `asAction()` | The same Swing `Action` on each call, for plain Swing APIs |
    | `execute()` | Runs the command; tests drive the UI this way |

  - Every generated class extends `StandardCommands`, which provides the standard commands with working handlers:

    | Command | Default behavior |
    |---|---|
    | `exit()` | Asks the exit vetoes, then shuts down |
    | `about()`, `preferences()` | Wired to `java.awt.Desktop`, including the macOS application menu |
    | `cut()`, `copy()`, `paste()`, `selectAll()` | Act on the focused component, and are enabled only when it supports them |
    | `undo()`, `redo()` | Act on the current undo stack |
    | `closeWindow()` | Asks the window's close vetoes, then closes it |

  - Their text and shortcuts come from the framework's own resources, per platform and locale. An application overrides them in its own TOML (`[commands.exit]`), and the build checks those overrides like any other.
- **Binding.**
  - Observable properties connect to text fields, check boxes, combo boxes, lists and tables.
  - Validation shows feedback on the field.
  - A "dirty" state is computed from the bindings.
- **Look and feel.**
  - FlatLaf by default, with dark mode that follows the operating system.
  - SVG icons that stay sharp on HiDPI screens.
  - Theme tokens in TOML.
- **Dialogs.** Messages and confirmations, OK/Cancel/Apply, an error dialog with details and a copy-to-clipboard button, and an About dialog filled from the application's metadata.

### Desktop integration

- **Directories.** Standard config, data, state and cache directories per operating system, from gaia's `ApplicationDirectories`.
- **Single instance.** Launching the application a second time forwards its arguments (for example, a file to open) to the instance already running.
- **OS hooks.**
  - `java.awt.Desktop` handles About, Preferences, Quit and open-file, including the macOS application menu.
  - `Taskbar` shows progress and badges.
  - System notifications.
- **Documents.**
  - Dirty tracking; save, save-as and a prompt when closing unsaved changes.
  - A recent-files list.
  - The window title follows the document.
  - An undo stack per document.
- **Command palette and keymap.** Every command is registered with its text and shortcut. That makes a searchable palette (Ctrl+Shift+P) and a remappable, saved keymap cheap to build.

### Developer experience

- **Test harness.**
  - Fake platform and clock, a memory file system, and an EDT-violation checker.
  - Tests drive the UI by running commands rather than by robot clicks.
  - Every menu bar, menu and dialog a test builds is checked for duplicate mnemonics.
- **Inspector overlay.** In development mode, shows each component's name, resource key and scope.
- **Packaging.**
  - Guidance for `jlink` / `jpackage` installers.
  - Since nothing uses reflection, a GraalVM native image is realistic. Swing support in native images varies by platform, so it must be tested on each target before it is promised.

---

## A sample application

The code below shows the intended feel. It is illustrative, not a settled API.

```java
@Application(name = "Notes", vendor = "imgn", qualifier = "be")
public final class NotesApp {

    public static void main(String[] args) {
        SwingApp.launch(NotesApp.class, args);
    }

    @Startup
    void start(Windows windows, Views views) {
        windows.showMain(views.create(NotesView.class));
    }
}
```

```java
public final class NotesView implements View {

    private final JTextArea editor = new JTextArea();
    private final Property<Boolean> dirty = Property.of(false);
    private final NotesMessages messages;
    private final NoteDocument document;

    @Inject
    NotesView(NotesMessages messages, NoteDocument document) {
        this.messages = messages;
        this.document = document;
    }

    @Override
    public JComponent component() {
        return new JScrollPane(editor);
    }

    @Command(enabled = "dirty", block = Block.WINDOW)
    Task<Path> save() {
        String text = editor.getText();
        return Task.of(() -> document.save(text))
                .onSuccess(file -> {
                    dirty.set(false);
                    StatusBar.show(messages.saved(file.getFileName().toString()));
                });
    }

    @Subscribe
    void onFontSizeChanged(FontSizeChanged event) {
        editor.setFont(editor.getFont().deriveFont(event.size()));
    }
}
```

Its resources live next to it:

```toml
# NotesView.toml
[commands.save]
text = "&Save"
icon = "icons/save.svg"
shortcut = "shortcut S"
tool-tip-text = "Save the note"

[editor]
font = "Monospaced-14"
accessible-name = "Note text"
```

```java
@Messages
public interface NotesMessages {
    String saved(String file);
    String words(int count);
}
```

```toml
# NotesMessages.toml
saved = "Saved {file}."
words = "{count,choice,0#no words|1#one word|1<{count,number,integer} words}"
```

```toml
# NotesMessages_fr.toml
saved = "{file} enregistré."
words = "{count,choice,0#aucun mot|1#un mot|1<{count,number,integer} mots}"
```

The build fails in any of these cases:

- `enabled = "dirty"` names no observable in `NotesView`.
- `[commands.save]` names a command that does not exist.
- `{file}` does not match a parameter of `saved`.
- `NotesMessages_fr.toml` lacks a key.
- `shortcut = "shortcut S"` does not parse as a key stroke.

### A single-frame application

A counter that shows "The count is: X.", asks for confirmation before exiting, and has a menu that exits:

```java
@Application(name = "Counter", vendor = "imgn", qualifier = "be")
public final class CounterApp implements SingleFrame, ExitVeto {

    public static void main(String[] args) {
        SwingApp.launch(CounterApp.class, args);
    }

    @Messages
    interface CounterMessages {
        String count(int count);
        String confirmExit();
    }

    private final Property<Integer> count = Property.of(0);
    private final CounterMessages messages;
    private final CounterAppCommands commands;
    private final StandardMenus menus;
    private final Ui ui;
    private final Dialogs dialogs;

    @Inject
    CounterApp(CounterMessages messages, CounterAppCommands commands, StandardMenus menus,
               Ui ui, Dialogs dialogs) {
        this.messages = messages;
        this.commands = commands;
        this.menus = menus;
        this.ui = ui;
        this.dialogs = dialogs;
    }

    @Override
    public JComponent content() {
        JLabel label = new JLabel();
        ui.bind(label::setText, count.map(messages::count));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.CENTER);
        panel.add(commands.increment().createButton(), BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(menus.file().with(commands.exit()));
    }

    @Override
    public void configureFrame(JFrame frame) {
        frame.setLocation(100, 100);
    }

    @Override
    public boolean canExit() {
        return dialogs.confirm(messages.confirmExit());
    }

    @Command
    void increment() {
        count.set(count.get() + 1);
    }
}
```

`CounterAppCommands` is generated: `increment()` comes from the `@Command` method, and `exit()` from `StandardCommands`. The application declares no exit command; it only vetoes. The File menu comes from `StandardMenus`, so the resources declare no menu.

```toml
# CounterApp.toml
[commands.increment]
text = "&Increment"
```

```toml
# CounterMessages.toml
count = "The count is: {count}."
confirmExit = "Do you really want to exit?"
```

```toml
# CounterMessages_fr.toml
count = "Le compte est : {count}."
confirmExit = "Voulez-vous vraiment quitter ?"
```

---

## What the processor generates

`swing-app-processor` is a gaia `StepProcessor`. It runs next to inject's and toml's processors. Its services are registered through gaia's `ServiceFiles`, and gaia's Maven plugin adds the matching `provides` clauses for the module path.

| Generates | From | Replaces |
|---|---|---|
| `<Class>Commands` classes, extending `StandardCommands`, with one `BoundCommand` method per command: id, handler, enabled/selected observables, blocking scope, whether the handler returns a `Task` | `@Command` methods | SwingAppFramework's actions found by method name; Guts' `GutsAction` field scanning |
| Subscription registrars, with exact generic event types | `@Subscribe` methods | Guts' `@Consumes` discovery through `getMethods()` |
| Message implementations, checked against every locale file | `@Messages` interfaces + TOML | `TextMap`, `MessageFormat` lookups by string |
| Resource key constants | TOML files | String lookups |
| Property accessors for the components a class holds and for common Swing types | Fields, plus a pre-generated list of Swing types | commons-beanutils, Guts' `UntypedProperty` |
| One resource opener per package | Packages that contain resources | Reading other modules' resources, which the module path forbids |
| Build-time checks of TOML resources: syntax with line and column, keys, value types, command ids (see below) | TOML files | Runtime warnings |
| EDT adapters for listener interfaces | `@EdtListener` interfaces | `java.lang.reflect.Proxy` |

### Checks of command resources

The processor reads each class's TOML files through the `Filer`; Maven copies resources to the class output before compiling. For each class that declares commands, it merges the files along the class hierarchy and checks every locale and platform variant:

- Every `[commands.<id>]` table names a command of the class, of a superclass, or of `StandardCommands`.
- Every key of a command table is a known property: `text`, `icon`, `shortcut`, `tool-tip-text`, `accessible-name`, and so on.
- Values have the right type and are valid: `shortcut` parses as a key stroke, `icon` names a resource that exists, and `text` has at most one `&` mnemonic.
- Every command has a `text` in the base file, and no locale file lacks a key that the base file has.

The `Messager` attaches diagnostics to Java elements only, so an error is reported on the `@Command` method or the class, and its message names the TOML file, line and column: `CounterApp_fr.toml:3:1: …`.

Mnemonic collisions are not checked by the build: menus are built in code, and the processor cannot see which commands end up in which menu. The test harness checks them instead.

---

## Out of scope

| Dropped | Why |
|---|---|
| Applets, internal frames (MDI) | Obsolete. |
| Component trees declared in resources; navigating to children by name | Code refactors well; resource paths don't. |
| SwingAppFramework's parameterized templates and cross-file `%{}` templates | Powerful, but hard to read and to check. `${references}` inside strings remain. |
| Weak-referenced consumers | Replaced by scopes. |
| Guts' module system | inject discovers beans; applications configure through beans and contributions. |
| Guts' task groups | Single tasks with blocking scopes cover the common cases. Reconsider if real applications need groups. |
| General-purpose widgets (checkbox tree, tree table, zoom layouts, line numbers) | They belong in a separate component library. |

---

## Roadmap

### Milestone 0: groundwork

- Maven build on the shared `be.imgn` parent, Java 21, module descriptors.
- A processor skeleton on gaia-processing with its test setup (`TestCompiler`, `CompilationAssert`).
- The upstream changes listed below are agreed.

### Milestone 1: first useful release

- Lifecycle and the single-frame main window.
- Commands with menus, toolbars and shortcuts.
- Typed messages and per-class resources, with build-time checks.
- Minimal observable state (`Property`, `map`, `combine`) and one-way binding.
- Tasks on virtual threads, with input blocking.
- Message, confirmation and error dialogs, and dialog views.
- Window state saved and restored.
- FlatLaf with dark mode.
- The test harness.
- The goal is enough to write a real small application.
  - **Acceptance:** port SwingAppFramework's examples and Guts' Address Book demo.

### Milestone 2: state and binding

- Observable lists, binding to editable components, validation, dirty tracking.
- The event bus (`@Subscribe`).
- Settings records.

### Milestone 3: desktop

- Single instance, OS hooks (`Desktop`, `Taskbar`, notifications).
- Documents with save prompts, recent files and undo.
- Multi-frame applications: opening windows from code, and views in windows other than the main frame. From this milestone on, `@Application` is required and `SingleFrame` is not.

### Milestone 4: polish

- Command palette and remappable keymap.
- Hot reload of resources.
- The inspector overlay.
- Accessible-name checks.

### Milestone 5: optional modules

- Wizard and docking.

---

## Upstream changes

| Repository | Change | Why |
|---|---|---|
| imgn-dev/inject | A hook that sees each constructed instance, with the injector it belongs to | Wires exit vetoes, `@Subscribe` and `@Command` on beans the framework does not create itself. |
| imgn-dev/inject | Scopes local to a child injector: hosting, closing with `@PreDestroy`, bindings each hosting child provides, a scope widening check, scopes inherited from supertypes, scoped assisted products | Window and view scopes, and generated commands reaching their owner through `Provider<T>`. |
| imgn-dev/inject | A key that several beans have only through their supertypes is left unbound, and a dependency on it is an ambiguity error | Several views implementing `View`, `SingleFrame` or `DialogView` in one application. |
| imgn-dev/inject | A module descriptor for `inject-processor` | Running every processor of an application from the processor module path. |
| imgn-dev/toml | A module descriptor for `toml-processor` | The same. |
| imgn-dev/toml | Source positions of keys and values | Build-time errors at the right line. |
| imgn-dev/gaia | Resource files in `TestCompiler` | Testing the processor against TOML resources. |
| imgn-dev/gaia | A `Step` that sees every root element | Processing views, which carry no annotation. |
| imgn-dev/gaia | A public check of application names | Checking `@Application` with gaia's exact rules. |
| imgn-dev/toml | Generated writers from records to `TomlTable` | Saving settings and window state. Without them, each state record needs a hand-written conversion. |
| imgn-dev/toml | A lenient binding mode that ignores unknown keys | Reading files saved by an older version. Without it, state files are versioned and reset when they don't match. |

---

## Open questions

- **Declarative menus.** Should menus be declarable in TOML, checked at build time, as an alternative to code? It would ease translation and branding, and let the build check mnemonic collisions, at the cost of a second way to do the same thing.
- **Plurals.** The JDK's `MessageFormat` handles them through `choice`. ICU4J would give proper plural rules per language, at the cost of a large dependency. It could be an optional module.
- **Native image.** Whether to support it officially depends on testing Swing under GraalVM on each target platform.
