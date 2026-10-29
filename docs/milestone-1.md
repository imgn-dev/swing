# Milestone 1: requirements

This is the requirement document of the first useful release described in the [vision](vision.md#milestone-1-first-useful-release). The goal is enough to write a real small application. The release is accepted when SwingAppFramework's examples and Guts' Address Book demo are ported, except the two locale examples, `LocaleExample3` and `LocaleExample4`, which need live locale switching (milestone 4). The Address Book's three views are one view, the application class, and its locale menu is left out (milestones 3 and 4); `SingleFrameExample6` shows its secondary frames as dialog views owned by the main frame (milestone 3). The ports keep the originals' texts, fonts and colors in resources, and their demo data in code.

Every part of every sentence in this document is a requirement, and must be followed exactly: names, packages, signatures, behaviors, orders and error cases. Any deviation, whether a part that can't be implemented as written, a part that contradicts another, or a gap, is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text.

---

## Scope

| In milestone 1 | Later |
|---|---|
| Lifecycle, exit vetoes, the single-frame main window | Multi-frame applications, opening windows from code, views placed in windows other than the main frame (M3) |
| Commands, generated `<Class>Commands`, standard commands, menus, toolbars, shortcuts | Command palette, remappable keymap (M4) |
| Typed messages, per-class resources, build-time checks | Live locale switching, hot reload (M4) |
| Tasks on virtual threads, with input blocking | Task progress in the `Taskbar` (M3) |
| Message, confirmation and error dialogs; dialog views owned by the main frame; a minimal About dialog (displayed name, version, vendor) | About dialog with the rest of the application's metadata, OK/Cancel/Apply forms with validation (M2) |
| A minimal `Observable` / `Property` and one-way binding | Binding to editable components, validation, dirty tracking (M2) |
| Window state: frame bounds, split panes, tabs, table columns | Settings records (M2) |
| FlatLaf, light and dark, following the operating system at startup | Following theme changes while running, theme tokens |
| The test harness | Inspector overlay (M4) |
| — | Event bus and `@Subscribe` (M2) |
| — | Undo/redo (M3, with documents) |

---

## Modules

| Module (Maven artifact) | Java module | Contents |
|---|---|---|
| `swing-core` | `be.imgn.swing` | Lifecycle, scopes, observables, tasks, resources and messages, with their annotations (`@Application`, `@Messages`). No `java.desktop`. |
| `swing-processor` | `be.imgn.swing.processor` | The annotation processor. Its descriptor `provides javax.annotation.processing.Processor`, so it runs from the processor path and from the processor module path (`--processor-module-path`). It requires `java.desktop`, to parse key strokes, fonts and colors with the JDK's own parsers, and `be.imgn.gaia.core` itself, which `gaia-processing` doesn't require transitively. |
| `swing-ui` | `be.imgn.swing.ui` | `Swing`. Commands: `@Command`, `Block`, `BoundCommand`, `StandardCommands`, `CommandRegistry`, `CommandContext`, `Trigger`. Menus. `View`, `SingleFrame`, `DialogView`, `Ui`, `Window`, `Windows`, dialogs, window state, icons, resource injection into components. |
| `swing-flatlaf` | `be.imgn.swing.flatlaf` | FlatLaf installation and dark mode detection. |
| `swing-testing` | `be.imgn.swing.testing` | `AppTester` and the checkers. |

The line between core and UI follows the concept, not the implementation. Core holds what is useful outside a UI, so that a domain module shared with a command-line tool, a server or batch jobs can use resources, messages, observables, tasks and the lifecycle without requiring `java.desktop`. Commands are a UI concept (text, icons, shortcuts, menus, triggered by components), so all of them, `@Command` included, are in `swing-ui`. Testing headless is not a reason for the split: lightweight Swing components work in a headless JVM.

All modules use the `be.imgn` parent `2026-11-SNAPSHOT` and the group id `be.imgn.swing`, and compile to Java 21 (`java.release` 21). The parent's enforcer requires a JDK of at least `java.release`, so JDK 21 or later, and Maven 3.10 or later, through the Maven wrapper. The build is set up in milestone 0.

The framework logs through `System.Logger`, with a logger named after the class that reports. Every "warning" in this document is logged at `WARNING`, and every logged failure at `ERROR`.

Dependencies on the imgn libraries, at the versions milestone 1 builds on. They are snapshots, built and installed locally until they are released:

| Library | Artifact | Used for |
|---|---|---|
| inject | `be.imgn.inject:inject` 0.1.1-SNAPSHOT | Beans, `@Singleton`, `@IntoSet`, `Provider<T>`, assisted factories, child injectors, `@PreDestroy`, and the [changes milestone 1 requires](#imgn-devinject) |
| toml | `be.imgn.toml:toml` 0.1.0-SNAPSHOT | Parsing resources at runtime and in the processor; writing window state |
| gaia | `gaia-core`, `gaia-processing`, `gaia-core-testing`, `gaia-memfs`, `gaia-processing-testing` 0.1.2-SNAPSHOT | `Platform`, `ApplicationDirectories`, `AtomicFiles`, `NamingConvention`, `StepProcessor`, `ServiceFiles`, `TestCompiler` |
| FlatLaf | `com.formdev:flatlaf` | Look and feel |

On the module path, applications add two Maven plugin goals: inject's `register-factories` and gaia's `register-services`.

---

## Application model

### Declaration and launch

```java
@Application(name = "Counter", vendor = "imgn", qualifier = "be", version = "1.0")
public final class CounterApp implements SingleFrame, ExitVeto { ... }

public static void main(String[] args) {
    Swing.launch(CounterApp.class, args);
}
```

`@Application` (`be.imgn.swing`) has:

| Attribute | Meaning |
|---|---|
| `name` | Required. The application's name, as gaia's `ApplicationDirectories` takes it (`Counter`), and the displayed name unless the class's resources give a `name` key. |
| `vendor` | Optional. The organization or person that makes the application (`imgn`). |
| `qualifier` | Optional, and only with `vendor`. The vendor's top-level domain (`be`). |
| `version` | Optional. Shown in the About dialog. |

These are the three names gaia's `Platform.applicationDirectories(...)` takes, so the directories are fully determined by the annotation:

- With `qualifier` and `vendor`: `applicationDirectories(qualifier, vendor, name)`. Only macOS uses the qualifier, for the bundle-style identifier: `~/Library/Application Support/be.imgn.Counter`.
- With `vendor` only: `applicationDirectories(vendor, name)`. Only Windows uses the vendor, as a directory above the application's: `%APPDATA%\imgn\Counter`.
- With neither: `applicationDirectories(name)`. Linux uses the name only, in lower case with spaces replaced by `-`: `~/.config/counter`.

Directories never depend on the locale: a localized displayed name comes from resources, never from `name`. The processor checks the names with gaia's rules (not blank, not `.` or `..`, no leading or trailing whitespace, no control character, none of `/ \ : < > " | ? *`) and reports a `qualifier` without a `vendor`. The application's identifier, for the single instance in milestone 3, is `qualifier.vendor.name`, with the parts that are present.

For the annotated class, the processor generates `$CounterApp$$Application implements ApplicationDescriptor` and registers it with `ServiceFiles`. `Swing.launch` finds the descriptor whose `type()` is the given class through `ServiceLoader`. The descriptor holds no handlers, since the application class is a bean.

```java
public interface ApplicationDescriptor {
    Class<?> type();
    String name();
    Optional<String> vendor();
    Optional<String> qualifier();
    Optional<String> version();
}

public interface ApplicationInfo {                // core, bound in the root injector
    Class<?> type();                              // the application class
    String name();                                // @Application.name
    String displayedName();                       // the `name` resource key, else name()
    Optional<String> vendor();
    Optional<String> qualifier();
    Optional<String> version();
    String identifier();                          // qualifier.vendor.name, with the parts that are present
    Platform platform();
    ApplicationDirectories directories();
}

public record Arguments(List<String> values) {}    // core, the arguments given to launch
```

### Startup

`Swing.launch(Class, String[])` runs these steps, in this order:

1. **Before AWT** (main thread): read the descriptor; on macOS set `apple.awt.application.name` to `@Application.name` and `apple.laf.useScreenMenuBar` to `true`. These must be set before any AWT class is initialized, so before resources are read: the localized displayed name can't be used there.
2. **Injector** (main thread): `Injector.builder()` binds `ApplicationInfo`, `Arguments`, `DisplayLocale` (see [Locale](#locale)) and the application `Scope`, then builds. Building it loads every factory through `ServiceLoader`.
3. **Startup steps** (main thread): every `@IntoSet(StartupStep.class)` bean, sorted by `order()` then by class name. A `StartupStep` has `int order()` and `void run()`. The framework contributes steps for resources (order 100) and window state loading (order 200). Steps run off the EDT, so they may do I/O.
4. **UI** (EDT): install the look and feel through the `LookAndFeelInstaller` bean, check icons (see [Icons](#icons)), start following the focus owner for the standard editing commands, create the main window (see [Windows](#windows)), show it.
5. **Ready** (EDT): after showing, one `invokeLater` calls `ready()` on every `@IntoSet(ReadyListener.class)` bean. Events posted while showing the window run first, so the window is laid out and painted.

`LookAndFeelInstaller` (UI) has `void install()`, called on the EDT before any component is created. `swing-ui` provides a `@Provides @Default` one that installs the system look and feel; `swing-flatlaf` provides one that installs FlatLaf, which replaces the default through inject's `@Default` rule. `ReadyListener` (core) has `void ready()`.

A failure in steps 2 to 4 shows the error dialog if the look and feel is installed, in the application's language once the injector exists and in English before, prints the stack trace otherwise, and exits with status 1 through `Exit`, or through `System.exit` when no injector exists yet.

### Shutdown

Shutdown is requested by `Lifecycle.requestExit()`, the `exit` command, the main window's close button, and the operating system's Quit (`Desktop.setQuitHandler`).

```java
public interface Lifecycle {                       // core, singleton
    void requestExit();                            // status 0
    void requestExit(int status);
}
```

`requestExit` may be called from any thread. It hands the shutdown to the `UiExecutor`: with the UI module, it returns at once and the shutdown runs on the EDT; with core's default, the shutdown runs on the calling thread before it returns. A request made while another is pending or a shutdown runs is ignored. The shutdown runs these steps:

1. Ask every `ExitVeto`, in the order of the [scopes](#scopes) from innermost to outermost. The first one that returns `false` cancels; for the OS Quit, the framework then calls `QuitResponse.cancelQuit()`.
2. Cancel running tasks and wait for them, up to a timeout of 2 s.
3. Run every `@IntoSet(ShutdownStep.class)` bean, sorted by `order()` then by class name. `ShutdownStep` (core) has `int order()` and `void run()`. The UI contributes two steps: saving window state (order 100), then disposing the windows and closing their child injectors from innermost to outermost, which runs `@PreDestroy` on their scoped beans ([I2](#imgn-devinject)) (order 200). A step that throws is logged at `ERROR` and skipped; the remaining steps, the closing and the exit still run.
4. Close the root injector, which runs `@PreDestroy` on the singletons it created.
5. Call `Exit.exit(status)`.

Core reaches the UI only through beans that the UI module provides, each with a `@Provides @Default` fallback in core:

| Bean (core) | Methods | Default in core | Provided by the UI module | Provided by `AppTester` |
|---|---|---|---|---|
| `UiExecutor` | `void execute(Runnable task)`, `boolean isUiThread()` | Runs the task at once on the calling thread; `isUiThread()` is `true` | `EventQueue.invokeLater`, `EventQueue.isDispatchThread()` | The EDT, with the posted work run before each assertion |
| `FailureHandler` | `void failed(Throwable failure)` | Logs the failure at `ERROR` | Shows the error dialog, owned by the focused window, else by the main window | Records the failure and fails the test |
| `Exit` | `void exit(int status)` | `System.exit(status)` | — | Records the status and stops the tester |

`ExitVeto` is a core interface with `boolean canExit()`, called on the `UiExecutor`'s thread. A veto is any `@IntoSet(ExitVeto.class)` singleton, or any bean of a window or view scope that implements the interface, the application class and views included. inject reports every constructed instance to the framework, with the injector it belongs to ([I6](#imgn-devinject)); the framework registers the vetoes among them in their scope. A window or view host is registered with the framework before anything is resolved through it; a scoped veto reported with an injector that hosts no window or view is an error, never silently dropped. An instance found both ways is asked once. Within a scope, vetoes are asked in the order their instances were constructed; the `@IntoSet` singletons, in the application scope, are asked last, sorted by class name.

---

## Scopes

There are three kinds of scope: the application, one per window, and one per view. A dialog's window scope is a child of the scope of the view that opened it. Each is an inject scope:

| Scope | inject annotation | Hosted by |
|---|---|---|
| Application | `@Singleton` | The root injector |
| Window | `@WindowScoped` | A child injector per window |
| View | `@ViewScoped` | A child injector per view, child of its window's |

`@WindowScoped` and `@ViewScoped` are framework annotations meta-annotated with `@ScopingAnnotation`. They rely on scopes local to a child injector, which inject doesn't have yet; the requirements are listed under [Upstream changes](#imgn-devinject) as I1 to I7, and I9 keeps views from colliding on the `View` key.

- **Hosting.** When the framework opens a window, it creates a child injector hosting `@WindowScoped`, and for each view in it a child of that child hosting `@ViewScoped` (I1). A bean of such a scope is created once per hosting child and cached there; resolving it through an injector with no hosting child is an error.
- **Bindings of a scope.** A window's child binds `Window` (see [Windows](#windows)) and `Scope`; a view's child binds `Scope`. A view's child is always a child of a window's child, so a view-scoped bean also sees its window's `Window`; a dialog's window child is a child of the opener's view child. Beans of these scopes depend on these keys directly: `Ui` and `CommandRegistry`, both `@ViewScoped`, depend on `Window` and on the view's `Scope`. The root declares nothing about scopes: inject checks, when each host is built, that its chain binds every key its scope's beans depend on (I3).
- **Which beans are scoped.** `View` carries `@ViewScoped`, and `SingleFrame` and `DialogView` extend it, so views, dialog views and a single-frame application class are view-scoped without annotating them (I5). `Ui`, `StandardCommands`, `StandardMenus` and `CommandRegistry` are `@ViewScoped`. `Dialogs` and the window-state support are `@WindowScoped`. Shared services are `@Singleton`.
- **Scope widening.** A bean of a wider scope can reach a bean of a narrower one only through a `Provider`. swing's processor checks the order `@Singleton`, `@WindowScoped`, `@ViewScoped` at compile time, for every class it compiles. inject checks what it can at runtime (I4): a `@Singleton` depending directly on a window- or view-scoped bean fails when the root is created, and a `@WindowScoped` bean depending directly on a `@ViewScoped` one fails when the main window's host is built. In a dialog's window, whose chain holds the opener's view host, inject can't see that error: the processor's check is the only one there.
- **Closing.** Closing a window closes its child injector, which closes its views' children first, then runs `@PreDestroy` on the instances of its scopes, in reverse creation order (I2). The framework's `Scope` of each is closed right after its injector: the view's injector, the view's `Scope`, the window's injector, the window's `Scope`. Resolving through a closed host fails fast. `@PreDestroy` is therefore allowed on window- and view-scoped beans: a view that holds a resource releases it there.

```java
@ViewScoped
public interface View {                    // UI
    JComponent content();
}
```

In milestone 1, the views are the single-frame application class and dialog views. Placing other views in windows comes with multi-frame applications, in milestone 3.

The framework's own `Scope` (`be.imgn.swing.Scope`) is the disposal list of a scope, for what is not a bean: subscriptions, input map entries, running tasks. The framework closes it right after it closes the scope's injector, so a bean's `@PreDestroy` runs before the subscriptions and tasks of its scope are released.

```java
public interface Scope {
    Optional<Scope> parent();
    void onClose(AutoCloseable resource);   // released in reverse order on close
    boolean isClosed();
}
```

Registering a resource in a closed scope, or subscribing with one, throws `IllegalStateException`.

---

## Observables

Milestone 1 needs enough observable state for enabled and selected commands and for one-way display, as in the counter sample. Binding to editable components comes in milestone 2.

```java
public interface Observable<T> {
    T get();
    Subscription subscribe(Scope scope, Consumer<? super T> listener);   // unsubscribed when the scope closes
    <R> Observable<R> map(Function<? super T, ? extends R> mapper);
    static <A, B, R> Observable<R> combine(
            Observable<A> a, Observable<B> b, BiFunction<? super A, ? super B, ? extends R> combiner);
}

public interface Subscription extends AutoCloseable {
    @Override
    void close();                          // idempotent; no checked exception
}

public interface Property<T> extends Observable<T> {
    void set(T value);
    static <T> Property<T> of(T initial);
}
```

- Properties are thread-safe: `set` may be called from any thread, and `get` on any thread sees the latest value. A property holds its value in an `AtomicReference`.
- Observables never hold `null`: `Property.of(null)` and `set(null)` throw `NullPointerException`; an absent value is an `Optional`.
- Listeners are called synchronously on the thread that calls `set`, only when the value changes (`Objects.equals`). When two threads set the same property at once, listeners may see the values out of order; code for which the order matters sets the property from one thread.
- `map` and `combine` are lazy: a mapped observable subscribes to its sources only while it has subscribers. While it has some, it keeps its last value and notifies them only when a source change gives a new value (`Objects.equals`), as a property does; its `get()` returns that value. Without subscribers, `get()` computes from the sources' current values. Mapping functions may therefore run more than once, on any thread: they must be cheap and free of side effects.
- Core has no EDT. The UI layer moves work to the EDT itself (see below); other code that listens runs on the setter's thread.
- `Ui.bind(Consumer<? super T> target, Observable<T> source)` calls `target` with the current value, then on every change, on the EDT, and registers the subscription in the `Ui`'s view scope.

### Delivery to the EDT

Swing components are created and changed only on the EDT, while an observable may change on any thread, for example at the end of a task's background work. Every subscription the UI layer makes follows one rule: `Ui.bind`, the enabled and selected states of commands, and [dynamic menu entries](#dynamic-entries).

1. The listener, on the setter's thread, touches no component. It sets a pending flag and, if the flag was not already set, posts one update with `EventQueue.invokeLater`. It posts even when it is already on the EDT, so an update never runs in the middle of the code that called `set`.
2. The update, on the EDT, clears the flag and reads the latest value with `get()`, not the value the listener received. Several changes before it runs cause one update, with the final value.
3. If the subscription's scope closed in the meantime, the update does nothing.

The test harness runs posted updates before each assertion, so tests see the components as the user will.

---

## Commands

### Declaration

```java
@Command                                    // id "increment"
void increment() { ... }

@Command(id = "save", enabled = "dirty", block = Block.WINDOW)
Task<Path> save() { ... }

@Command(selected = "wordWrap")
void toggleWordWrap() { ... }
```

| Attribute | Meaning |
|---|---|
| `id` | Defaults to the method name. It is also the TOML key and the Swing action command. |
| `enabled` | Name of a non-private field or no-arg method of the class whose type is `Observable<Boolean>`. Absent: always enabled. |
| `selected` | Same, for a `Property<Boolean>`. The command becomes a toggle: activating it flips the property, then calls the handler. |
| `block` | `Block.NONE` (default), `COMMAND`, `COMPONENT`, `WINDOW` or `APPLICATION`. Allowed only when the handler returns a `Task`: any other value on a `void` handler is a compile error. |

The handler is a non-private, non-static method returning `void` or `Task<?>`. A private handler, `enabled` target or `selected` target is a compile error, since the generated class in the same package must reach it. It takes no parameter, or a single `CommandContext`; any other parameter is a compile error. It is called on the EDT.

```java
@Command
void open(CommandContext context) {
    if (context.modifiers().contains(Modifier.SHIFT)) {
        openInNewWindow();
    } else {
        openHere();
    }
}
```

`CommandContext` describes how the command was activated, on every path:

| Method | Returns |
|---|---|
| `modifiers()` | `Set<Modifier>`: `SHIFT`, `CONTROL`, `ALT`, `META`, and `SHORTCUT`, which is set with `CONTROL` or `META`, whichever is the platform's menu shortcut modifier (⌘ on macOS, Ctrl elsewhere, the platform being gaia's `Platform`, which tests fake, not the toolkit's) |
| `trigger()` | The `Trigger`, with the objects of the path that activated the command |

```java
public sealed interface Trigger {
    record MenuSelection(JMenuItem item) implements Trigger {}                        // a menu of the bar, or built with MenuSpec.createMenu()
    record PopupMenuSelection(JMenuItem item, Component invoker) implements Trigger {}
    record ButtonClick(AbstractButton button) implements Trigger {}
    record Shortcut(KeyStroke keyStroke, Component focusOwner) implements Trigger {}
    record OperatingSystem(SystemAction action) implements Trigger {}                // QUIT, ABOUT, PREFERENCES
    record Programmatic() implements Trigger {}
}
```

No variant shares its simple name with a type of `java.lang`, `java.awt` or `javax.swing`, so importing `Trigger`'s members on demand never makes a name ambiguous.

- Each variant carries only what its path always has, so no component is ever absent. A popup menu always has an invoker, since `Ui.popupMenu` attaches every popup to a component: `PopupMenuSelection.invoker()` is the component that was right-clicked, such as a table. A shortcut always has a focus owner: the component its `KeyEvent` came from.
- A handler that uses the objects switches over `trigger()`. The switch must be exhaustive, so the compiler makes the handler deal with the paths that have no component, such as `Programmatic`. A handler can't rely on a source that doesn't exist.
- Uses include showing a popup under the toolbar button that was clicked (`ButtonClick.button()`), acting on the component a context menu was opened on (`PopupMenuSelection.invoker()`), and telling apart two shortcuts bound to one command (`Shortcut.keyStroke()`).

Modifiers, unlike the trigger's objects, exist on almost every path, so they stay on `CommandContext`:

- From a menu item or a button, they come from the modifiers of the `ActionEvent` the JDK delivers: the keys held while clicking. Shift-click is supported on every platform. On the macOS screen menu bar, the modifiers are those the JDK's native menu bar reports in that event; the framework reads them the same way as everywhere else.
- From a shortcut, they are the modifiers of the key stroke.
- From `OperatingSystem` and `Programmatic` triggers, the set is empty.

`CommandContext`, `Trigger` and `Modifier` are in `swing-ui`, with the rest of commands. That is also required by the language: the variants refer to Swing types, and every permitted subtype of a sealed interface must be in the same module as the interface.

The mapping from an `ActionEvent` to a `CommandContext` is tested with constructed events, not end to end: a test sends `new ActionEvent(item, ActionEvent.ACTION_PERFORMED, id, when, ActionEvent.SHIFT_MASK)` to the command's `Action`, for a `JMenuItem` in a menu bar, in a popup menu and for a `JButton`, and checks the context the handler receives. This works headless, and covers Shift-click on every platform, the macOS screen menu bar included, as far as the framework is concerned.

Tests that only need a context build one with real components, which works headless: `execute(CommandContext.of(new Trigger.ButtonClick(new JButton()), Modifier.SHIFT))`. `CommandContext.of(Trigger trigger, Modifier... modifiers)` builds one; `SHORTCUT` is added when `CONTROL` or `META` is given and is the platform's menu shortcut modifier. `execute()` alone uses `Programmatic` and no modifier.

Adding a variant breaks every exhaustive `switch` without a `default` in user code. The command palette (milestone 4) will add one; before 1.0, that break is accepted rather than declaring a variant with nothing behind it.

Handlers take no `ActionEvent`:

- A command is not always triggered by a component. Shortcuts, the operating system's Quit, `execute()` in tests, and later the command palette and the keymap have no meaningful event. The framework would have to make one up with a fake source, and a handler relying on `getSource()` would break on those paths only.
- What the event carries is rarely useful. The action command is the id the handler already knows. The source is the menu item or button, not the object acted on: in a popup menu it is the item, not the table that was right-clicked, which `Trigger.PopupMenuSelection` gives instead.
- The object a command acts on, such as a selected row, is observable state instead: a `Property<Contact> selection` that the handler reads and that `enabled = "hasSelection"` derives from, so both stay consistent.
- Tests run commands with `execute()`, without building events.
- What handlers do need from it, the modifiers and the component involved, is in `CommandContext`, which exists on every path, with a `Trigger` that says which objects that path has.

### Generated class

For each class that declares commands, the processor generates a public `<Class>Commands` in the same package:

```java
@ViewScoped
public final class CounterAppCommands extends StandardCommands {

    private final BoundCommand increment;

    @Inject
    CounterAppCommands(CommandRegistry registry, Provider<CounterApp> owner) {
        super(registry);
        this.increment = registry.command(CounterApp.class, "increment")
                .handler(context -> owner.get().increment())
                .bind();
    }

    public BoundCommand increment() {
        return increment;
    }
}
```

- The generated class is always `@ViewScoped`, since it builds components for a view through the view's `CommandRegistry`. The declaring class must be `@Singleton`, `@WindowScoped` or `@ViewScoped`; a declaring class without a scope is a compile error: its commands would run on an instance nobody else holds.
- It reaches the declaring class through `Provider<CounterApp>`, which breaks the cycle between the two constructors. The declaring class is cached in its scope, the view's or a wider one, so `get()` returns the instance that received the commands.
- Being scoped, the class holds its `BoundCommand`s: two beans of a view that inject `CounterAppCommands` get the same instance, and so the same enabled state, selected state and shortcuts.
- Its direct dependencies are `CommandRegistry` and `Provider<CounterApp>`; it depends on no scope key itself. Only view-scoped beans inject it directly: views, and other beans of the view. A `@WindowScoped` or `@Singleton` bean, a declaring class of those scopes included, reaches it only through a `Provider`, whose `get()` succeeds only when called through a view's injector (I1, I4).
- The framework generates no unscoped bean that depends on a scope key. `<Class>Menus` is `@ViewScoped` and depends on `Ui`; `$X$$Messages` is a `@Singleton` that depends on `Resources` and `DisplayLocale`, bound in the root injector.
- Handler calls are lambdas, so no reflection is involved. The class has an `@Inject` constructor, so inject's processor generates its factory in the next round.

`CommandRegistry` (UI, `@ViewScoped`) holds every `BoundCommand` of its view, keyed by the class the generated class is for (`StandardCommands` for standard commands) and the id: asking twice for the same command returns the same `BoundCommand`. Generated code builds commands through it:

| Method of `CommandBuilder` | Sets |
|---|---|
| `handler(Consumer<CommandContext> handler)` | A `void` handler |
| `taskHandler(Function<CommandContext, Task<?>> handler)` | A handler returning a `Task` |
| `enabled(Supplier<Observable<Boolean>> enabled)` | The `enabled` observable, read when the command is attached |
| `selected(Supplier<Property<Boolean>> selected)` | The `selected` property, read when the command is attached |
| `block(Block block)` | The input blocking of a `taskHandler` |
| `bind()` | Returns the `BoundCommand`, created on the first call for its class and id |

### Attaching

A command is attached when its owner exists: its `enabled` and `selected` suppliers are called and subscribed, and its shortcut is registered in its window. The framework learns of each generated `<Class>Commands` instance through the construction hook ([I6](#imgn-devinject)):

- An instance constructed while the framework builds a view (the view and its dependencies, then its component resources) is attached when that build ends, so that the owner is in its scope.
- An instance constructed at any other time, such as through a `Provider`, is attached at once.

Every command of an attached instance is attached, whether or not it is placed in a menu, a toolbar or a button: a command's shortcut always works in its window.

### Overriding standard commands

A class may declare a command whose id is a standard command's (`exit`, `about`, `preferences`, `cut`, `copy`, `paste`, `selectAll`, `closeWindow`, `ok`, `cancel`):

- The processor reports it as a compiler warning, on the handler: "Command `exit` of `p.CounterApp` overrides the standard command `exit`. Overriding standard commands may become an error in a future version."
- The standard command is not used where the override applies, and the user-written one is used instead. Its resources default to the standard command's (text, shortcut, icon), and the class's own `[commands.<id>]` table overrides them key by key.
- In the class's generated class, the standard method (`exit()`) returns the user-written command, as does the method named after the handler when it has another name.
- In a view whose class, or one of its superclasses, declares the override, every `StandardCommands` instance of the view returns the user-written command. The framework registers the view class's overrides in the view's `CommandRegistry` before it resolves the view, or, for a view created by a factory, whose class is known only once it exists, as soon as it exists and before its component resources are applied: a standard command asked for in such a view's constructor is the standard one. On macOS, the application menu's Quit, About and Preferences run the main view's commands, so an override in a single-frame application class applies there too.
- A handler whose name is a standard command's method name, but whose id is another one, is a compile error: its generated method would override the standard method with an unrelated command.
- An override declared in a class that is not a view applies nowhere, since only a view's registry holds overrides; the processor warns about it.

### Inheritance and naming

A class inherits the commands of its superclasses: its generated class has a method for each, which calls the handler on the subclass's instance. An inherited handler, `enabled` target or `selected` target in another package must be `public`, since the generated class of the subclass is not a subclass of the declaring class and can't reach a `protected` member there; otherwise it is a compile error.

A nested class gets `<Outer><Nested>Commands`. Two classes in one package that would produce the same name are a compile error, whether they are compiled together or not: the processor also checks that no type with the name exists already.

### `BoundCommand`

| Method | Returns |
|---|---|
| `id()` | The id, also the action command |
| `createButton()`, `createMenuItem()` | A new component on each call |
| `createToggleButton()`, `createCheckBoxMenuItem()` | The same, for commands with a selected state; they throw `IllegalStateException` on other commands |
| `asAction()` | The same `javax.swing.Action` on each call |
| `execute()`, `execute(CommandContext)` | Runs the command as if activated, if enabled, and returns `true`; returns `false` otherwise. Whether it is enabled is the `enabled` observable's current `get()`, and the command not being blocked by its own running task, not the state of the `Action`, which follows them one posted update later. Without a context, the trigger is `Trigger.Programmatic` and no modifier is set. |

`BoundCommand` also implements `MenuEntry` (see [Menus and toolbars](#menus-and-toolbars)).

Each created component shares the command's `Action`, so text, icon, tooltip, enabled and selected state stay in sync. Buttons in toolbars hide their text when they have an icon (`hideActionText`).

### Standard commands

`StandardCommands` (UI) is a `@ViewScoped` bean, injectable on its own by views that declare no command, and the superclass of every generated class. Its constructor takes the view's `CommandRegistry`, so every instance in a view returns the same `BoundCommand` for a standard command. It provides, in milestone 1:

| Method | Behavior |
|---|---|
| `exit()` | `Lifecycle.requestExit()` |
| `about()` | Shows a minimal About dialog: displayed name, `version` and `vendor` from `@Application` |
| `preferences()` | Calls `PreferencesHandler.showPreferences()`. Disabled unless a `PreferencesHandler` bean exists, which the framework probes with `Injector.getBeanDefinition` |
| `cut()`, `copy()`, `paste()`, `selectAll()` | Delegate to the focused component's `TransferHandler` actions or its action map; enabled state follows focus and selection |
| `closeWindow()` | Closes the current window: on the main frame, same as `exit()`, after the vetoes; in a dialog, same as `cancel()`, since a dialog has no vetoes |
| `ok()`, `cancel()` | In dialog views only: close the dialog with or without a result. Outside a dialog view's scope, calling them throws `IllegalStateException` |

On macOS, `exit()`, `about()` and `preferences()` are wired to `Desktop` handlers, with the `Trigger.OperatingSystem` of `SystemAction.QUIT`, `ABOUT` and `PREFERENCES`. They are left out of every menu built from descriptions (`Ui.menuBar`, `Ui.popupMenu`, `MenuSpec.createMenu()`), since the application menu shows them; toolbars and buttons keep them.

```java
public enum SystemAction { QUIT, ABOUT, PREFERENCES }                // UI

public interface PreferencesHandler {                               // UI, a singleton bean of the application
    void showPreferences();                                         // called on the EDT
}
```

The framework's resources (`StandardCommands.toml` and its locale and platform variants) give their text and shortcuts. Milestone 1 ships English and French.

### Resources of a command

```toml
[commands.save]
text = "&Save"
icon = "icons/save.svg"
shortcut = "shortcut S"
tool-tip-text = "Save the note"
accessible-description = "Saves the note to its file"
```

- `text`: `&` marks the mnemonic; `&&` is a literal ampersand. On macOS, mnemonics are not set, following the platform's guidelines.
- `icon`: a path relative to the package of the class whose file names it, loaded as described in [Icons](#icons).
- `shortcut`: the `KeyStroke.getKeyStroke(String)` syntax, where `shortcut` stands for the platform's menu shortcut modifier (Ctrl, or ⌘ on macOS).
- `tool-tip-text`, `accessible-name`, `accessible-description`: as named.

### Keyboard

- Menu items show the shortcut as their accelerator.
- Each window registers the shortcut of every command [attached](#attaching) in its scopes in its root pane's `WHEN_IN_FOCUSED_WINDOW` input map, so a shortcut works even when the command is only on a toolbar or a button, or nowhere.
- Two commands with the same shortcut in one class's resources, on any one platform (the base file merged with that platform's variant), fail the build; `shortcut` counts as its own modifier, so `shortcut S`, `ctrl S` and `meta S` are three shortcuts. Two in the same window but from different classes are reported when the second one is attached: an exception in development mode (`-Dbe.imgn.swing.dev=true`) and in tests, a warning otherwise. The same command, attached through several views of one window, registers its shortcut once.

### Menus and toolbars

Menus are built in code, from descriptions. For each application, command or view class whose resources have `[menus.<id>]` tables, the processor generates a `<Class>Menus` class, as it does for commands:

```java
@ViewScoped
public final class CounterAppMenus extends StandardMenus {

    private final BoundMenu counter;

    @Inject
    CounterAppMenus(Ui ui) {
        super(ui);
        this.counter = menu(CounterApp.class, "counter");   // from [menus.counter]
    }

    public BoundMenu counter() {
        return counter;
    }
}
```

- The generated class is `@ViewScoped`, like generated commands.
- `StandardMenus` (UI) is a `@ViewScoped` bean, injectable on its own by classes that declare no `[menus.*]` table. Its `protected final BoundMenu menu(Class<?> owner, String id)` builds a menu from that class's resources.
- The method is named after the id, converted to lower camel case (`recent-files` gives `recentFiles()`). An id that doesn't convert to a Java identifier is a compile error.
- `StandardMenus` provides `file()`, `edit()`, `view()`, `window()` and `help()`, with text and mnemonics from the framework's resources, so most applications declare no `[menus.*]` table for them. A `[menus.file]` table in the resources of the application class, or of the view class, overrides their text in that view, the view class's winning, and generates no method. A class whose only `[menus.*]` tables override standard menus gets no generated class.
- A `BoundMenu` holds the menu's resources and belongs to the scope, like a `BoundCommand`. When the locale changes (milestone 4), the scope refreshes it.

`BoundMenu.with(MenuEntry...)` returns a `MenuSpec`, an immutable description of the menu. The `Ui` turns descriptions into components:

```java
ui.menuBar(
        menus.file().with(commands.increment(), MenuEntry.SEPARATOR, commands.exit()),
        menus.edit().with(commands.cut(), commands.copy(), commands.paste()));

ui.popupMenu(table, commands.copy(), commands.paste());

ui.toolBar(commands.increment(), ToolBarEntry.SEPARATOR, commands.copy());
```

| Method | Returns |
|---|---|
| `Ui.menuBar(MenuSpec...)` | A new `JMenuBar` |
| `Ui.popupMenu(JComponent invoker, MenuEntry...)` | A new `JPopupMenu`, set as the invoker's component popup menu (`setComponentPopupMenu`): Swing shows it on the invoker's popup trigger, so its invoker is always the component |
| `Ui.toolBar(ToolBarEntry...)` | A new `JToolBar` |
| `MenuSpec.createMenu()` | A new `JMenu`, for a menu outside the bar |

Building from descriptions lets the framework see the whole menu at once:

- It drops `exit()`, `about()` and `preferences()` on macOS, where the application menu shows them, then hides leading, trailing and doubled separators.
- It checks for duplicate mnemonics in each menu and among the bar's menus when it builds them: an exception in development mode and in tests, a warning otherwise.
- It can rebuild the components when the locale changes.

Three sealed interfaces type the entries, so a misplaced entry doesn't compile:

| Interface | Permits | Accepted by |
|---|---|---|
| `MenuEntry` | `BoundCommand`, `Separator`, `MenuSpec` (a submenu), `Dynamic` | `BoundMenu.with`, `Ui.popupMenu` |
| `DynamicEntry` | `BoundCommand`, `Separator`, `MenuSpec`, `Item` | `MenuEntry.dynamic` |
| `ToolBarEntry` | `BoundCommand`, `Separator` | `Ui.toolBar` |

`Separator` has one instance, which `MenuEntry.SEPARATOR`, `DynamicEntry.SEPARATOR` and `ToolBarEntry.SEPARATOR` all name.

### Dynamic entries

Recent files, the window list and contributions from plugins change at runtime, while a description doesn't. A dynamic entry marks a section of a menu whose entries come from an observable:

```java
menus.file().with(
        commands.open(),
        MenuEntry.dynamic(recentFiles.map(files -> DynamicEntry.numbered(files.stream()
                .map(file -> DynamicEntry.item(file.getFileName().toString(), () -> open(file)))
                .toList()))),
        MenuEntry.SEPARATOR,
        commands.exit())
```

- `MenuEntry.dynamic(Observable<? extends List<? extends DynamicEntry>>)`: when the observable changes, the framework rebuilds that section of every menu created from the description, checks its mnemonics again and hides the separators it leaves doubled. The subscription belongs to the scope the menu was built in; the components of the section, a submenu's own dynamic sections included, subscribe in a child scope of it that is closed and opened again at each rebuild, so a replaced submenu takes its subscriptions with it.
- Threading follows the [delivery rule](#delivery-to-the-edt). The `map` that builds the entries runs on the thread that changed the list, which is safe: entries are immutable descriptions, not components. Only the rebuild, on the EDT, creates components. If the menu is open, the rebuild revalidates it and resizes its popup with `pack()`.
- An item's action runs on the EDT, like a command handler.
- `DynamicEntry.item(String text, Runnable action)` returns an `Item`, a plain item. `Item` is a `DynamicEntry` and not a `MenuEntry`, so it exists only inside a dynamic entry. Dynamic items are data, like a file name: they have no resources, no fixed id, and an action that needs an argument, which command handlers don't take. They have no shortcut. `&` marks a mnemonic as in resources; `DynamicEntry.numbered(List<Item>)` returns the items with `&1 ` to `&9 `, a space after the digit, prefixed to the first nine, as recent-file lists usually do. It takes items only: a command's text belongs to its `Action`, shared by every component.
- A dynamic entry can also hold commands and submenus.

Entries are built from the observable rather than filled when the menu opens (`menuSelected`), which is Swing's usual approach. The menu is then always up to date, the mnemonic check and tests see it without opening it, and the result doesn't depend on how `menuSelected` behaves with the macOS screen menu bar.

Fixed entries are commands, with resources and build-time checks. Plain items exist only for the sections that hold data.

---

## Resources

### Files and merge order

Resources of a class `p.NotesView` live in `p/` next to the class file. For locale `fr_BE` on macOS, these files are read, each one overriding the previous:

1. Resources of the superclass, recursively, merged the same way.
2. `NotesView.toml`
3. `NotesView.macos.toml`
4. `NotesView_fr.toml`, then `NotesView_fr.macos.toml`
5. `NotesView_fr_BE.toml`, then `NotesView_fr_BE.macos.toml`

Platform ids are `windows`, `macos` and `linux`, from gaia's `Platform`. On a Unix that gaia recognizes as neither Linux nor macOS, no platform variant is read. Merging is deep: a table merges key by key, and any other value replaces the previous one. toml has no merge, so the framework implements it over `TomlTable.entries()` and `toBuilder()`.

### Reading resources on the module path

For each package that contains a class with resources, the processor generates `$$Resources implements ResourceOpener`. It has `String packageName()` and `Optional<InputStream> open(String fileName)`, which calls `$$Resources.class.getResourceAsStream(fileName)`. That call is in the package's own module, so it works without opening the package. The opener is written in the first round that adds no class to the package, so that a class gaia defers to a later round, such as a view injecting its own generated menus class, still adds its icons; a class checked after that is a warning, since the startup check of the icon loaders doesn't cover its icons. It is registered through `ServiceFiles`; on the module path, gaia's `register-services` adds the `provides` clause.

`Resources` (core, singleton) finds openers through `ServiceLoader` by package name, parses with `Toml.parse(ByteSource)`, merges, and caches the merged table per class; the locale is fixed per `Resources` instance.

### Icons

Icons are loaded by `IconLoader` beans (UI), contributed with `@IntoSet(IconLoader.class)`:

```java
public interface IconLoader {
    boolean supports(String path);                        // by extension
    Icon load(ResourceOpener opener, String path);
}
```

- `swing-ui` contributes the PNG loader, with `@2x` variants (`MultiResolutionImage`). `swing-flatlaf` contributes the SVG loader, through `FlatSVGIcon`.
- A command icon is loaded against the package of the class whose table gives it; a component icon against the view class's package, then its superclasses' packages, nearest first, since the merged table no longer tells which file named it.
- Each `$$Resources` also has `Set<String> icons()`, the icon paths its package's resources name, which the processor knows. Startup step 4 checks that a loader supports every one of them: an `.svg` icon without `swing-flatlaf` on the module path is an error at startup.

### Locale

In milestone 1 the locale is chosen once, at startup: `Locale.getDefault(Locale.Category.DISPLAY)`, the category for user-interface text. Java's standard properties set it, so the framework adds none: `-Duser.language=fr -Duser.country=BE` sets every category, and `-Duser.language.display=fr` only the display one. A locale that no file supports falls back to the base file. Changing the locale at runtime comes later.

The locale reaches `Resources` and messages through `DisplayLocale` (core), a `record DisplayLocale(Locale locale)` bound in the root injector: `Swing.launch` binds the JVM's display locale, and `AppTester.start(type, locale)` binds the test's. Nothing in the framework reads `Locale.getDefault` after startup, so tests with different locales run in parallel.

### Injection into components

At the top level of a class's resources:

- The tables `commands` and `menus` hold commands and menus.
- Every other table names a component field of that class. Its keys are properties written to the component the field holds.
- Plain values, not tables, are properties of the class itself: `name` and `theme` on the application class, `title` on a single-frame application class and on dialog views. Any other plain value, or one on a class it doesn't apply to, is a compile error.

Messages are not in a class's resources: they have their own files (see [Messages](#messages)).

```toml
[editor]
font = "Monospaced-14"
accessible-name = "Note text"
```

- The processor checks that `editor` is a non-private field whose type is a `java.awt.Component` subtype, and that each key is a supported property of that type.
- It generates `$NotesView$$Injector`, which sets the properties with plain setter calls. Every property except `tab-title` is applied after the view's constructor, before `content()` is called; `tab-title` is applied after `content()` returns, once tabs exist.
- Milestone 1 supports: `text`, `tool-tip-text`, `icon`, `font`, `foreground`, `background`, `enabled`, `visible`, `accessible-name`, `accessible-description`, `columns`, `rows`, `title` and `tab-title`. Wider coverage comes with the pre-generated property list of the vision.
- `title` applies to `JComponent` fields: it is the title of the component's `TitledBorder`. When the component has no border, the injector sets `BorderFactory.createTitledBorder(title)`; when it has another kind of border, it throws `IllegalStateException`.
- `tab-title` applies to any component field: it is the title of the component's tab in the `JTabbedPane` that holds it. When the component is not a tab, the injector throws `IllegalStateException`.

Value formats:

| Kind | Format |
|---|---|
| Color | `"#rrggbb"`, `"#rrggbbaa"` |
| Font | `Font.decode` syntax: `"Monospaced-14"`, `"SansSerif-BOLD-12"` |
| Icon | A path, as for commands, relative to the package of the class whose file names it |
| References | `${key.path}` inside a string, resolved after merging, in the same class's table |

---

## Messages

```java
@Messages
interface CounterMessages {
    String count(int count);
    String confirmExit();
}
```

```toml
# CounterMessages.toml
count = "The count is: {count}."
confirmExit = "Do you really want to exit?"
```

- The key is the method name, spelled the same, as for commands.
- Placeholders name parameters: `{count}`, `{count,number,integer}`, `{count,choice,...}`. At compile time, the processor checks every pattern of every locale file against the method's parameter names, and generates, per method, the table from parameter names to indices. At runtime, the generated code rewrites the pattern of the current locale from names to indices with that table, once, when it loads it, then formats with `MessageFormat` for that locale.
- Methods return `String` and take any parameters `MessageFormat` handles.
- Patterns follow `MessageFormat`'s quoting rules: a single `'` starts a quoted section, so a literal apostrophe is written `''`, as in `{file} n''existe pas.`
- The processor generates `$CounterMessages$$Messages`, a `@Singleton` bean exposed as `CounterMessages`, reading its patterns from `Resources` on construction.
- A `@Messages` interface nested in a class has its own file, named after the interface (`CounterMessages.toml`), in the same package. Its implementation is named after its binary name: `$CounterApp$CounterMessages$$Messages`.

---

## Build-time checks

For every application, command or view class, and every `@Messages` interface, the processor reads the class's TOML files from `CLASS_OUTPUT` with `Filer.getResource`. A TOML file in such a package whose base name is a class the processor generates nothing for is reported as a warning, so that a file doesn't vanish silently. Maven copies resources there before compiling. For IDEs that compile without copying, the option `-Abe.imgn.swing.resources=<dir>` adds a directory to look in.

| Check | Error when |
|---|---|
| Syntax | The file doesn't parse (`TomlParseException` gives line and column) |
| Command ids | A `[commands.<id>]` table names no command of the class, its superclasses or `StandardCommands`, or a handler named like a standard command has another id. A command overriding a standard command is a warning, not an error (see [Overriding standard commands](#overriding-standard-commands)) |
| Command properties | A key is not a known property, or its value has the wrong type |
| Shortcuts | A `shortcut` doesn't parse, or two commands of the class share one on a platform, the base file merged with the platform's variant; the error is reported at the shortcut the later file set, naming the other file |
| Mnemonics | A `text` has more than one `&` mnemonic |
| Icons | An `icon`, its `${…}` references resolved within its own file, names a resource that doesn't exist. A reference to a key the file lacks, which only the merged table resolves, is a warning, and the icon goes unchecked |
| Text | A command or menu has no `text` in the base file |
| Translations | A language file (`X_fr.toml`) lacks a key of the base file, or any locale or platform file has a key the base file lacks; a country file and a platform file override their base key by key, so they may hold a few keys only |
| `enabled`, `selected` | The name matches no field or method of the right type |
| `block` | A value other than `NONE` is given on a handler that doesn't return a `Task` |
| Scope widening | A class whose scope is `@Singleton`, `@WindowScoped` or `@ViewScoped`, declared or inherited, has an injection point (constructor parameter, `@Inject` field or method parameter) whose type has a narrower one of these scopes, not through `Provider`. The order is `@Singleton`, then `@WindowScoped`, then `@ViewScoped`. Every class the processor compiles is checked, its inherited injection points included, with the types of its injection points read from sources and from class files; records and enums are not beans of a scope and are not checked; classes compiled without swing's processor are left to inject's runtime checks (I4) |
| Messages | A placeholder names no parameter, or a pattern doesn't parse as a `MessageFormat`; a `@Messages` interface is private, declares a generic method, or two methods with one name |
| Component fields | A top-level table other than `commands` and `menus` names no non-private component field, or a key is not a supported property of its type |
| Class properties | A top-level plain value is not a property of the class (`name` and `theme` on the application class, `title` on a single-frame application class or a dialog view), is not a string, or `theme` is none of `system`, `light` and `dark` |
| Application | A name of `@Application` breaks gaia's rules, `qualifier` is given without `vendor`, or the annotated class doesn't implement `SingleFrame` |

Diagnostics are attached to the Java element (the `@Command` method, the class, the field), with the file, line and column in the message:

- The processor parses each file with `Toml.parse`, which returns a `TomlDocument`: its `root()` table and its `positions()`.
- It checks each file against its own parse, before merging, since positions belong to the parsed tables: a merged or copied table has none. The duplicate-shortcut check is the exception: it merges the base file with each platform variant and reports at the position of the file that set the colliding key.
- An error on a key is reported at `positions().key(table, key)`, on a value at `value(table, key)`, on an array element at `element(array, index)`. The lookups throw when the key is absent, so the processor looks a key up only after finding it in the table.
- A syntax or semantic error is reported at `TomlParseException.position()`.
- A key that a locale file lacks is reported at the base file's position of that key.

---

## Tasks

```java
public interface Task<T> {
    static <T> Task<T> of(Callable<T> work);
    static Task<Void> of(Runnable work);
    static <T> Task<T> of(TaskWork<T> work);
    Task<T> onSuccess(Consumer<? super T> handler);
    Task<T> onFailure(Consumer<? super Throwable> handler);
    Task<T> onCancel(Runnable handler);
}

public interface TaskContext {          // passed to work that needs it: Task.of((TaskContext context) -> ...)
    void progress(double fraction);     // 0..1, or -1 for indeterminate
    void message(String text);
    boolean isCancelled();
}

@FunctionalInterface
public interface TaskWork<T> {
    T run(TaskContext context) throws Exception;
}
```

- Work runs on a virtual thread (`Thread.ofVirtual()`), named after the command, or `task` when no command runs it.
- Callbacks, progress and messages are delivered through the `UiExecutor` that core receives from the UI module (`EventQueue::invokeLater`), or from the test harness.
- Cancelling interrupts the thread. A task that ends by `InterruptedException` or `CancellationException` after cancellation runs `onCancel`.
- A failure with no `onFailure` handler goes to the `FailureHandler`, which shows the error dialog with the UI module.
- A task belongs to the scope it was started in. Closing the scope cancels it.
- `progress` throws `IllegalArgumentException` outside 0..1 and -1. Starting a task twice, or in a closed scope, throws `IllegalStateException`.

`TaskService.run(Task<?> task, Scope scope)` (core) runs a task in a scope, without blocking any input. `Ui.run(Task<?> task, Block block)` (UI) runs it in the `Ui`'s view scope with input blocking; outside a command, only `NONE`, `WINDOW` and `APPLICATION` apply, and `COMMAND` or `COMPONENT` throws `IllegalArgumentException`. A command whose handler returns a `Task` is run by its `BoundCommand`, which applies its `block` with the command itself and the command's `CommandContext`.

### Input blocking

| `Block` | While the task runs |
|---|---|
| `NONE` | Nothing is blocked |
| `COMMAND` | The command is disabled |
| `COMPONENT` | One component is disabled, by trigger: for `ButtonClick`, the button, unless it shares the command's `Action`, in which case the command is disabled instead, since the `Action` would re-enable the button; for `PopupMenuSelection`, the invoker; for `Shortcut`, the focus owner. For `MenuSelection`, `OperatingSystem` and `Programmatic`, which have no component acted on, the command is disabled instead, as with `COMMAND` |
| `WINDOW` | A blocking glass pane covers the window: it consumes mouse and key events, the window's shortcuts included, shows a busy cursor and, after 300 ms (within 350 ms), a progress indicator with a Cancel button, which cancels the task that blocked the window last |
| `APPLICATION` | The same glass pane on every window, those opened while the task runs included |

---

## Windows

### `SingleFrame`

```java
public interface SingleFrame extends View {
    default JMenuBar menuBar() { return null; }
    default JToolBar toolBar() { return null; }       // placed at BorderLayout.NORTH
    default void configureFrame(JFrame frame) {}
    default boolean restoreBounds() { return true; }
}
```

`SingleFrame` extends `View`. When the application class implements it, the framework, on the EDT:

1. Creates the main window with `Windows.createFrame()`, with `DO_NOTHING_ON_CLOSE` and a listener that requests exit.
2. Creates the window's host with `scopedChildBuilder(WindowScoped.class)`, binding that `Window` and the window's `Scope`, and the view's host as its child with `scopedChildBuilder(ViewScoped.class)`, binding the view's `Scope`.
3. Resolves the application class through the view's child injector. `SingleFrame` makes it view-scoped, so this is the instance that its generated commands reach.
4. Applies its component resources, then calls `content()`, `menuBar()` and `toolBar()`, puts them in the window's root pane, then applies the `tab-title` properties. The build of the view ends here, and its commands are [attached](#attaching).
5. Sets the window's title from the class's `title` resource, else the displayed name.
6. Calls `configureFrame(frame)` with the `JFrame` that the real `Windows` wraps, then `pack()`. With no saved state, centers the frame on the screen unless `configureFrame` moved it or asked the platform to place it (`setLocationByPlatform`).
7. Restores the saved window state, which overrides the position set by `configureFrame`. A frame that must always open at the same place overrides `restoreBounds()` to return `false`.

Windows are created by `Windows` (UI, singleton), and the framework sees them as `Window` (UI):

```java
public interface Window {
    JRootPane rootPane();
    void setTitle(String title);
}

public interface Windows {
    Window createFrame();
    Window createDialog(Window owner);
    void show(Window window);
    void dispose(Window window);
}
```

The real `Windows` wraps a `JFrame` or a modal `JDialog`. `AppTester`'s headless `Windows` wraps a bare `JRootPane`, which is lightweight: steps 6 and 7 are skipped there, since no frame exists, and every other step runs.

In milestone 1, the `@Application` class must implement `SingleFrame`; otherwise it is a compile error. Opening windows from code comes with multi-frame applications, in milestone 3: from then on, `@Application` is required and `SingleFrame` is not. `ReadyListener` is part of milestone 1 regardless: it is the ready signal of the lifecycle, for work that must wait until the main window is shown.

### Dialogs

`Dialogs` (UI, `@WindowScoped`) shows standard dialogs, owned by its window:

```java
boolean confirm(String message);
void inform(String message);
void error(String message, Throwable cause);   // details area and Copy button
```

A dialog view has its own content, commands and scope:

```java
public interface DialogView<R> extends View {
    Optional<R> result();                      // read after ok()
    default List<BoundCommand> buttons(StandardCommands commands) {
        return List.of(commands.ok(), commands.cancel());
    }
}
```

```java
<R> Optional<R> showDialog(Class<? extends DialogView<R>> type);                                  // Ui
<F, R> Optional<R> showDialog(Class<F> factory, Function<? super F, ? extends DialogView<R>> create);

Optional<String> name = ui.showDialog(RenameDialog.class);
Optional<String> name = ui.showDialog(RenameDialogFactory.class, f -> f.create(current));
```

Both block until the dialog closes, on the EDT, as modal dialogs do, and return the result of `ok()`, or an empty result for `cancel()`.

- `Ui.showDialog` is called on the opener's `Ui`, which is view-scoped: the dialog is owned by that view's window, is modal to it, and is centered on it.
- The dialog's window is created with `Windows.createDialog(owner)`. Its child injector is a child of the opener's view injector, and the dialog view gets a view child of it, as any view. The dialog's `title` resource gives the dialog's title, else the displayed name. The dialog is document-modal; the error dialog is application-modal.
- The second form passes arguments through an inject assisted factory, resolved through the dialog view's child injector. inject builds an assisted factory with the context of the injector it is resolved through, so the dialog's dependencies come from the dialog's scopes. The dialog itself must become the view scope's instance of `RenameDialog`, so that its generated commands reach it: an assisted product of a child-local scope is cached in that scope (I7).
- `buttons` receives the dialog view's `StandardCommands` bean, so its `ok()` and `cancel()` are the dialog's own.
- Enter activates `ok()`, whose button is the root pane's default button, Escape activates `cancel()`; closing the dialog is `cancel()`.

### `Ui`

`Ui` (UI, `@ViewScoped`) gathers what a view needs to build its components. Every method is called on the EDT, and every subscription it makes belongs to its view's scope.

| Method | See |
|---|---|
| `<T> void bind(Consumer<? super T> target, Observable<T> source)` | [Observables](#observables) |
| `JMenuBar menuBar(MenuSpec... menus)` | [Menus and toolbars](#menus-and-toolbars) |
| `JPopupMenu popupMenu(JComponent invoker, MenuEntry... entries)` | [Menus and toolbars](#menus-and-toolbars) |
| `JToolBar toolBar(ToolBarEntry... entries)` | [Menus and toolbars](#menus-and-toolbars) |
| `void run(Task<?> task, Block block)` | [Tasks](#tasks) |
| `showDialog(...)`, both forms | [Dialogs](#dialogs) |

---

## Window state

The state of the main frame is saved on exit, and the state of a dialog when it closes, or on exit if it is still open. Each is restored when its window is created.

```toml
[windows.main]
bounds = [100, 100, 640, 480]          # x, y, width, height
maximized = false

[windows.main.components.split]        # a JSplitPane field named split
divider-location = 240

[windows."p.RenameDialog"]             # a dialog, by the binary name of its view class
bounds = [300, 200, 320, 160]
```

| What | Saved for | Key |
|---|---|---|
| Bounds | Every window | `bounds` |
| Maximized (`Frame.MAXIMIZED_BOTH`) | The main frame | `maximized` |
| `JSplitPane` divider location | Fields of the window's view | `components.<field>.divider-location` |
| `JTabbedPane` selected index | Fields of the window's view | `components.<field>.selected-index` |
| `JTable` column order and widths | Fields of the window's view | `components.<field>.columns`, an array of tables with `model-index` and `width`, in view order |

Components are found through the non-private fields of the view, which the processor knows: no component needs a name. A private `JSplitPane`, `JTabbedPane` or `JTable` field, or one inherited from another package that is not public, is skipped, with a compile warning. The processor generates the field access in `$NotesView$$State`.

Saved bounds that don't lie entirely within the usable bounds of one current screen (its `GraphicsConfiguration` bounds minus its insets) are ignored: the window opens as with no saved state, not maximized, and its components' state is still restored.

The state is written to `window-state.toml` in gaia's `ApplicationDirectories.state()`, created with `Files.createDirectories` when missing, with toml's `Toml.writeAtomically`. toml has no writer from records to TOML, so milestone 1 converts its few state records to `TomlTable` by hand. A file that doesn't bind (unknown keys are rejected) is discarded with a warning, and the defaults are used.

---

## Look and feel

`swing-flatlaf` provides the `LookAndFeelInstaller` that installs FlatLaf, which startup step 4 calls on the EDT before any component is created.

- The `-Dbe.imgn.swing.theme` property, else the `theme` key of the application's resources, read through `ApplicationInfo.type()`, selects `system` (default), `light` or `dark`. The property wins, so a user can override what the application ships. An unknown value, of either, is logged as a warning and ignored.
- `system` reads the operating system's setting once at startup: on macOS, `defaults read -g AppleInterfaceStyle`; on Windows, the `AppsUseLightTheme` registry value through `reg query`; on Linux, `gsettings get org.gnome.desktop.interface color-scheme`. Any failure means light.
- Without `swing-flatlaf`, the system look and feel is used.

Following the setting while running needs native hooks or polling, and is left for later.

---

## Test harness

```java
@Test
void incrementShowsTheCount() {
    try (AppTester<CounterApp> app = AppTester.start(CounterApp.class, Locale.FRENCH)) {
        app.commands(CounterAppCommands.class).increment().execute();

        assertThat(app.component(JLabel.class).getText()).isEqualTo("Le compte est : 1.");
    }
}

@Test
void exitAsksForConfirmation() {
    try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
        app.dialogs().answerConfirm(false);

        app.commands(CounterAppCommands.class).exit().execute();

        assertThat(app.isRunning()).isTrue();
        assertThat(app.dialogs().confirmed()).containsExactly("Do you really want to exit?");
    }
}
```

- **Headless.** `AppTester` runs the real lifecycle with its headless `Windows` (see [Windows](#windows)): frames and dialogs are never realized, which works in headless JVMs. The content, menu bar and toolbar are built and laid out in a bare root pane, so components can be found and inspected.
- **Finding components.** `component(Class<T> type)` returns the one component of that type in the main window, and throws `IllegalStateException` when there is none or more than one. `components(Class<T> type)` returns them all, in depth-first order.
- **Exit.** `AppTester`'s `Exit` records the status instead of exiting: after an exit, `isRunning()` is `false` and `exitStatus()` returns the status.
- **EDT.** Every call on `AppTester` that reaches the application, and every call on the commands it returns, runs on the EDT and waits; `dialogs()` and `ticker()` return the fakes, which tests configure from their own thread and the application reads on the EDT. `execute()` waits for the task a command starts, through the test `UiExecutor`. Before returning, the tester runs the updates posted by observables, so assertions see the components up to date.
- **Fakes.** `FakeDialogs` records dialogs and answers them. `AppTester.start(type, locale)` binds `DisplayLocale`; `AppTester.start(type)` binds `Locale.ROOT`, which reads the base files. The platform and directories are gaia's `FakePlatform` and `FakeDirectories` on a `gaia-memfs` file system, so window state is saved in memory. Time uses `InstantSource` and gaia's `FakeTicker`.
- **Close.** `AppTester.close()` closes the windows, then the root injector and the application `Scope` through the lifecycle's close, without asking the exit vetoes or running the shutdown steps: tasks and subscriptions registered in the application scope are released between tests.
- **EDT checker.** A `RepaintManager` replacement fails the test when a component is repainted or invalidated off the EDT.
- **Mnemonic checker.** Menus check themselves when they are built and rebuilt (see [Menus and toolbars](#menus-and-toolbars)); in tests that check throws, and the tester records the failure and fails the test at its next call. The tester itself checks every dialog it builds for duplicate mnemonics and duplicate shortcuts. 

---

## Processor

`swing-processor` is a gaia `StepProcessor`, with these steps:

| Step | Triggered by | Generates |
|---|---|---|
| Application | `@Application` | `$X$$Application` descriptor, service entry |
| Commands | `@Command` | `XCommands` |
| Messages | `@Messages` | `$X$$Messages` |
| Views | Types implementing `SingleFrame`, `DialogView` or `View` | `$X$$Injector`, `$X$$State` |
| Menus | `[menus.*]` tables in the resources of any of the above | `XMenus` |
| Resources | Any of the above | `$$Resources` per package, service entries, the build-time checks |

The views step is not triggered by an annotation: it overrides `Step.processesRootElements()` and gets each round's root elements through `AnnotatedElements.rootElements()`. A view that injects its own generated menus class can't be resolved until that class exists; the menus step finds such classes again through their resource files in later rounds rather than by holding elements back.

Generated classes with `@Inject` constructors are picked up by inject's processor in the next round, so the generated code never builds beans by hand.

Tests use gaia's `TestCompiler` and `CompilationAssert`. They give TOML files with `TestCompiler.withResource(StandardLocation.CLASS_OUTPUT, path, text)`, and icons with its `byte[]` overload, where the processor reads them as in a Maven build.

---

## Upstream changes

### imgn-dev/inject

Milestone 1 builds on these changes, which inject provides from 0.1.1-SNAPSHOT. They have no fallback.

| Id | Change | Needed for |
|---|---|---|
| I1 | **Child-local scopes.** A child-local scope is a scoping annotation with no `Scope` service: swing registers none for `@WindowScoped` and `@ViewScoped`, and their annotations need only CLASS retention, read from class files of other compilations too. A host is a child built with `scopedChildBuilder(WindowScoped.class)` or `scopedChildBuilder(ViewScoped.class)`, one scope per host; `childBuilder()` stays for plain children. A bean of a child-local scope is created once per host and cached there, in the nearest ancestor-or-self host of its scope; through an injector with no such host in its chain, resolving it fails fast with a clear error. Hosts nest, to any depth, with any number of children per host. Unscoped beans keep being rebuilt in the child they are resolved through, and `@Singleton` beans keep anchoring to the root. A `Provider` of a child-local key, injected into a bean, looks up the host when `get()` is called, from the injector that bean was built in. Creating a host stays as cheap as a child injector today: it only does lookups prepared when the root is created, and loads no factory. Hosts are created, closed and resolved through on the EDT, so child-local scopes need no concurrency guarantee beyond today's. | Window and view scopes; `Provider<T>` returning the right instance in generated commands |
| I2 | **Closing a child.** Closing a host closes its live children first, then runs `@PreDestroy` on the instances cached in it, in reverse creation order, with the root's error handling: the remaining beans are still destroyed, and the first failure is thrown with the others suppressed. `@PreDestroy` is allowed on beans of child-local scopes; unscoped beans built in a host are not tracked. After closing, resolving through the host, or calling `get()` on a provider that resolves through it, its own bindings' providers included, fails fast. Closing a host never closes its parent; closing the root closes its live hosts first. | Releasing view and window resources |
| I3 | **Bindings of a scope.** The root declares nothing about child-local scopes. When a host is built, inject checks that its chain (the host and its ancestors, the root included) binds every key that the beans of its scope depend on directly, such as `Window` and `Scope`, and fails otherwise with a message naming the bean, the key and the parameter. A key bound by several enclosing hosts (`Scope`) resolves to the nearest one. | Beans that depend on their window or scope |
| I4 | **Scope widening check.** No order between child-local scopes is declared; inject checks what it can at runtime, with explicit messages. A `@Singleton` depending directly, not through `Provider`, on a bean of a child-local scope fails when the root is created. When a host is built, a direct dependency of its scope's beans on a child-local scope with no host of it in the chain fails: a `@WindowScoped` bean depending on a `@ViewScoped` one fails for the main window. A `Provider` edge is exempt in every direction; unscoped beans are outside the check. swing's processor checks the order between window and view scopes at compile time (see [Build-time checks](#build-time-checks)). | Catching a singleton that captures a view |
| I5 | **Inherited scopes.** A scoping annotation on a supertype applies to the beans that extend or implement it, through the superclass chain and all superinterfaces, transitively, including types from other compilations. A scope declared on the class itself wins. Two different scopes inherited from different supertypes, with none declared on the class, are a compile error. | Views and single-frame applications scoped by implementing `View`, `DialogView` or `SingleFrame` |
| I6 | **Construction hook.** Root beans contributed with `@IntoSet(ConstructionListener.class)`, whose method is `void constructed(Object instance, Injector injector)`, are resolved when the root is created, then called by generated factories, synchronously, on the constructing thread, with every instance of a child-local scope once it is fully built (constructor, members, `@PostConstruct`), and the injector it is cached in; assisted products included. Instances bound with `bind(instance)` are not reported, nor instances built while the listeners are resolved. An exception thrown by a listener fails that construction: the instance is neither cached nor tracked, so no `@PreDestroy` runs for it. | Exit vetoes on any scoped bean; later `@Subscribe` and `@Command` on beans the framework doesn't create |
| I7 | **Scoped assisted products.** inject's processor accepts any scoped assisted recipe; a product whose scope isn't child-local fails when the root is created. An assisted factory whose product has a child-local scope, resolved and called through a host of that scope, builds the product with dependencies from that host and caches it there under the product's key. From then on, the instance is resolvable through that host and its descendants, `Provider` included. Before the call, `get()` on a provider of the product fails, as resolving an assisted type does today, but the provider itself can be injected. A second call through the same host fails. Assisted products without a scope keep today's behavior. | Dialogs that take arguments and have commands |
| I8 | **A module for the processor.** `inject-processor` gets a `module-info.java` that requires its dependencies and `provides javax.annotation.processing.Processor`. | Running every processor of an application from the processor module path, as modules |
| I9 | **Ambiguous implicit keys.** When several beans get a key only through their supertypes (implicit exposure), the duplicate is not an error: the key is left unbound. A dependency on it fails when the injector that must satisfy it is created (the root for `@Singleton` and unscoped beans, the host for beans of a child-local scope), and `getInstance` on it fails; the messages name the key, the injection point when there is one, and every bean implementing it. Explicit keys (a bean's own class, `@ExposeAs`, qualifiers, `@Provides`) still fail as duplicates. swing marks nothing: views implementing `View`, `SingleFrame` and `DialogView` stay exposed under their own class, and injecting one of those interfaces directly is an ambiguity error as soon as two views implement it. | Two views in one application: without it, both are exposed under `View`, and creating the root fails with `DuplicateBindingException` |

Existing inject behavior the design relies on, as inject's sources and documentation describe it:

- `Provider<T>` breaks cycles, at compile time and at runtime.
- An assisted factory resolves container parameters through the context of the injector it was resolved through.
- Classes generated by another processor with `@Inject` constructors are picked up in a later round; elements referring to types not generated yet are deferred.

### Other libraries

| Repository | Change | Needed for | API swing uses |
|---|---|---|---|
| imgn-dev/toml | A module for `toml-processor`: a `module-info.java` that requires its dependencies and `provides javax.annotation.processing.Processor` | Running every processor of an application from the processor module path, as modules | Module `be.imgn.toml.processor` |
| imgn-dev/toml | Source positions of keys, values and array elements, and of syntax and semantic errors, at the offending key, header or value | Build-time errors at the right line and column | `TomlDocument`, `TomlPositions.key`, `value`, `element`, `TomlParseException.position()` |
| imgn-dev/gaia | `TestCompiler` support for text and binary resource files on the class output | Processor tests, icons included | `TestCompiler.withResource(Location, String, String)` and `withResource(Location, String, byte[])` |
| imgn-dev/gaia | A `Step` that sees every root element | The views step | `Step.processesRootElements()`, `AnnotatedElements.rootElements()` |
| imgn-dev/gaia | A public check of application names | Checking `@Application` at compile time with gaia's exact rules | `ApplicationDirectories.checkName(String name, String role)`, with the attribute's name as the role |

The toml writer and the lenient mode from the vision's list are not needed in milestone 1: window state is converted by hand, and a state file that doesn't bind is discarded.

---

## Changes to the vision

These depart from the [vision](vision.md), and are to be applied to it:

- **Scope of the generated commands class.** The vision has it take the declaring class's scope, or the view scope for a singleton. It is always `@ViewScoped`: it builds components for a view through the view's `CommandRegistry`, which no wider scope could hold (see [Generated class](#generated-class)).
- **What the generated class holds.** The vision has it hold the UI services. Its direct dependencies are the view's `CommandRegistry` and a `Provider` of the declaring class; the registry is the UI service it needs.
- **Resource key constants.** The vision generates constants for icons so that code never looks up a resource by string. No constants are generated: commands and components get their icons from their resources, and no API takes an icon path, so the goal is met without them. Reconsider if an API ever takes a resource key.
- **EDT adapters.** The vision's processor generates EDT adapters for `@EdtListener` interfaces. None are generated; the delivery rule and the executors cover the framework's own needs. The row is dropped from the vision, or scheduled with the inspector of milestone 4.
- **The ready signal.** The vision fires it "once the event queue is idle". One `invokeLater` posted after the main window is shown is what idle means on the EDT.
- **Timings.** The vision puts timings in resources. The framework's own timings, the 300 ms of the blocking glass pane and the 2 s of the shutdown, are constants until an application needs to tune one; an application's timings go in its resources. The principle is about what applications write.
- **`content()`.** The vision's `NotesView` overrides `component()`; the hook is `content()`, as its `CounterApp` and this document have it.
- **`SingleFrame` hooks.** `toolBar()` and `restoreBounds()` join the vision's `content()`, `menuBar()` and `configureFrame(JFrame)`.
- **`execute()`.** It returns `boolean`, `false` when the command is disabled, so that tests can tell.
- **The processor's dependencies.** `swing-processor` requires `java.desktop` and `gaia-core` besides the vision's gaia-processing and toml (see [Modules](#modules)).

---

## Order of work

1. Build, modules, processor skeleton with its tests (milestone 0).
2. Releases of the parent `2026-11`, gaia 0.1.2, toml 0.1.0 and inject 0.1.1, which carry the upstream changes; until then, local snapshots.
3. Core: scopes, observables, `Resources` with merging, `Lifecycle`, `ExitVeto`, startup and shutdown without UI.
4. Processor: `@Application` descriptor, `$$Resources`, messages, with the build-time checks for messages.
5. UI: `SingleFrame`, `Ui`, generated commands, `BoundCommand`, standard commands, menus, toolbars, shortcuts, and the command checks.
6. Tasks and input blocking.
7. Dialogs and dialog views.
8. Component resources and window state.
9. FlatLaf and dark mode.
10. Test harness, then the acceptance ports.
