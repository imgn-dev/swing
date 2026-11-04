# Milestone 2: requirements

This is the requirement document of state and binding, described in the [vision](vision.md#milestone-2-state-and-binding): observable lists, binding to editable components, validation, dirty tracking, the event bus and settings records. It also delivers what [milestone 1](milestone-1.md#scope) left for it: OK/Cancel/Apply forms with validation, and the About dialog with the rest of the application's metadata. And it takes up the [ideas](ideas.md) that milestone 1's ports suggested for it: a command's running state, stop, restarting and serial commands, partial results, chained and periodic tasks, the resources of reusable panels, selection by key, form layout and button rows. The release is accepted when the [acceptance](#acceptance) examples and their tests pass.

Every part of every sentence in this document is a requirement, and must be followed exactly: names, packages, signatures, behaviors, orders and error cases. Any deviation, whether a part that can't be implemented as written, a part that contradicts another, or a gap, is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text.

Milestone 2 builds on milestone 1 as implemented. Everything milestone 1 specifies stays, except where a section below changes it; [Changes to milestone 1](#changes-to-milestone-1) lists those changes.

---

## Scope

| In milestone 2 | Later |
|---|---|
| Observable lists, delivered to the EDT change by change | Sorted and filtered views of a list |
| Binding text components, buttons and combo boxes to properties, and lists and tables to observable lists, with single selection | Multiple selection, trees, spinners, sliders, editable combo boxes |
| Validation, with feedback on the field and the status of each requirement; dirty tracking | Save prompts and undo (M3, with documents) |
| OK/Cancel/Apply dialogs on forms | Wizards (M5) |
| The event bus, on gaia-core: `@Subscribe` and `@OnVirtualThread` (gaia-core), and `@OnEdt` (UI) | — |
| Settings records, in the config directory | Watching settings files for changes made outside the application |
| The About dialog with description, copyright, website and icon; the application icon on the main frame | Opening the website from the About dialog (M3, with OS hooks) |
| Window state written and read through toml's generated code, ignoring unknown keys | — |
| A command's running state, stop commands, restarting and serial commands, partial results, chained tasks and periodic actions | Groups of tasks, which the vision leaves out |
| Resources of the component fields of a view's component fields | — |
| A form layout builder and button rows | Toolbars that don't float by default (M4) |
| The test harness for all of the above | — |

Milestone 2 adds no module. Each module gains the types of its layer: core gets observable lists, `Rule`, `Validator`, `Validation` and `Check`, the event bus, settings and the task additions; `swing-ui` gets `@OnEdt`, the bindings, forms, `Converter`, `Column`, `ValidationFeedback`, `FormPanel`, `Concurrency` and the command additions; `swing-flatlaf` gets FlatLaf's validation feedback; `swing-processor` gets two steps and the checks of the command and resource additions; `swing-testing` gets the harness additions. Core still doesn't read `java.desktop`, which `ModuleTest.doesNotReadJavaDesktop` keeps checking.

The imgn libraries are those of milestone 1, at their releases rather than milestone 1's snapshots: gaia 0.1.2 (`gaia.version`), inject 0.1.1 (`inject.version`) and toml 0.1.0 (`toml.version`), from Maven Central, which carry the [upstream changes](#upstream-changes) that milestone 2 requires; plus `be.imgn.gaia:gaia-processor` at `gaia.version` on the annotation processor path. The parent is at its release 2026-11-01, from Maven Central, rather than 2026-11-SNAPSHOT. The event bus is in `gaia-core`, which core already depends on.

---

## Observable lists

An observable list holds the elements that a list, a table or a combo box shows, and tells its listeners each change, so components update one row rather than all of them.

```java
public interface ObservableList<E> extends Observable<List<E>> {      // core
    @SafeVarargs
    static <E> ObservableList<E> of(E... elements);
    static <E> ObservableList<E> copyOf(Collection<? extends E> elements);

    int size();
    E get(int index);
    void add(E element);
    void add(int index, E element);
    void addAll(Collection<? extends E> elements);
    E set(int index, E element);                                       // returns the previous element
    E remove(int index);                                               // returns the removed element
    boolean remove(E element);                                         // the first equal element
    void setAll(Collection<? extends E> elements);
    void clear();

    Subscription subscribeChanges(Scope scope, Consumer<? super ListChange<E>> listener);
}

public sealed interface ListChange<E> {                                // core
    record Added<E>(int index, List<E> elements) implements ListChange<E> {}
    record Removed<E>(int index, List<E> elements) implements ListChange<E> {}
    record Replaced<E>(int index, List<E> removed, List<E> added) implements ListChange<E> {}
}
```

- `get()`, from `Observable`, returns an unmodifiable snapshot of the elements; the snapshot never changes afterwards. `get(int)` and `size()` read the current elements.
- Elements are never `null`: every method that takes an element throws `NullPointerException` for `null`, and `of` and `copyOf` for a `null` element.
- Each method that modifies the list makes one change, with unmodifiable lists of elements: `add` and `addAll` give `Added`; `remove`, and `clear` on a list that isn't empty, give `Removed`; `set` gives `Replaced` of one element; `setAll` gives `Replaced(0, previous, new)`, or `Added` when the list was empty, or `Removed` when the new elements are none.
- A modification that leaves the list equal (`List.equals`) makes no change and calls no listener: `set` with an equal element, `setAll` with equal elements, `clear` on an empty list, `remove` of an element that isn't there, `addAll` of no element. `set` and `setAll` still store the elements they are given, so that the list holds those instances, as `get()`, `get(int)` and later changes show; `set` returns the element it replaced.
- Indices out of range throw `IndexOutOfBoundsException`, as `List` does.
- `ObservableList` is sealed: only core's implementation, which `of` and `copyOf` return, implements it (see [Sealed types](#changes-to-milestone-1)).

Threading:

- A list is thread-safe. Modifications are serialized by a lock that each one holds while it changes the elements and calls the listeners. Listeners therefore see the changes in the order they were made, each with indices that are valid for the list just before it. Reads (`get()`, `get(int)`, `size()`) take the lock too.
- Listeners, of `subscribeChanges` and of `subscribe` alike, are called synchronously on the thread that modifies the list, while it holds the lock, in the order they subscribed. They must be quick: the UI layer's listeners only queue the change. A listener that reads another observable list risks a deadlock: it waits for that list's lock while holding its own, and a thread that modifies that list while one of its listeners reads the first waits the other way. Listeners therefore read only the change they receive.
- A listener that throws doesn't stop the change: the elements stay changed, every listener is still called, and the modifying method rethrows the first exception once all have been, with the later ones added to it as suppressed.
- A listener that calls a modifying method of an observable list, the one it listens to or any other, even one that would change nothing, throws `IllegalStateException`: its change would reach the other listeners before the one being delivered, and two lists whose listeners modify each other from two threads would deadlock.
- `subscribe` listeners receive the new snapshot, as for any observable; `map` and `combine` work on lists as on any observable.

A dynamic menu entry ([milestone 1](milestone-1.md#dynamic-entries)) takes any `Observable<List<...>>`, so it takes an observable list of entries, or one mapped to entries.

### Delivery of list changes to the EDT

The [delivery rule](milestone-1.md#delivery-to-the-edt) of milestone 1 reads the latest value when its update runs, so several changes cause one update. A component that shows a list needs every change instead, in order, to update its rows and keep its selection. For every component bound to an observable list:

1. The UI layer keeps, on the EDT, its own copy of the list, made when the component is bound. It takes the copy and subscribes under the list's lock, in one call, so that no change falls between them or is applied twice: core's implementation of `ObservableList` offers that call in its internal package, which core already exports to the UI module.
2. Its change listener, on the thread that modified the list, touches no component: it adds the change to a queue and, if no update is pending, queues one in milestone 1's delivery queue, as rule 1 does, among the framework's updates: a view's `Ui.bind` targets see the component's rows already following the change.
3. The update, on the EDT, applies every queued change in order to the copy, and fires the model event of each: for `Added`, an interval added (`fireIntervalAdded`, `fireTableRowsInserted`); for `Removed`, an interval removed (`fireIntervalRemoved`, `fireTableRowsDeleted`); for `Replaced` of as many elements as it removes, the rows changed (`fireContentsChanged`, `fireTableRowsUpdated`); for any other `Replaced`, the whole content changed (`fireContentsChanged` over the copy, `fireTableDataChanged`).
4. If the subscription's scope closed in the meantime, the update does nothing.

The component's model reads the copy, never the list itself, so the indices it reports always match the events it fired.

---

## Event bus

The event bus carries events between beans that don't know each other, such as a list that publishes the selected contact and the panels that show it. It is gaia's `EventBus` (`be.imgn.gaia.core.event`, in `be.imgn.gaia:gaia-core`), with its `@Subscribe` and its subscribing annotations: swing adds two annotations, a bus bound in the root injector, and the registration of beans in their scopes.

```java
@Subscribe
void onContactSelected(ContactSelected event) { ... }      // on the publisher's thread

@OnEdt
void onImportProgress(ImportProgress event) { ... }        // on the EDT

@OnVirtualThread
void onContactChanged(ContactChanged event) { ... }        // on a virtual thread
```

### Subscribing annotations

Two subscribing annotations, each `@SubscribingAnnotation(executor = ...)`, on methods, deliver off the publisher's thread:

| Annotation | Declared by | Executor | Its tasks run |
|---|---|---|---|
| `@OnVirtualThread` (`be.imgn.gaia.core.event`) | gaia-core ([upstream change](#imgn-devgaia)) | `VirtualThreadExecutor` (`be.imgn.gaia.core.event`), which needs no shutdown | Each on a new virtual thread |
| `@OnEdt` (`be.imgn.swing.ui`) | `swing-ui`, with `CLASS` retention | `EdtExecutor` (`be.imgn.swing.ui`), a public class that implements `java.util.concurrent.Executor`, with a public constructor without parameters | With `EventQueue.invokeLater` |

- Virtual threads are a need of most applications, not of Swing ones only, so their annotation is gaia's; core declares none. `@OnEdt` is in `swing-ui`, since the EDT is a Swing concept.
- These executors are what a bus that the framework didn't build uses, such as one made with `EventBus.create()` in a domain module or a test. The framework's bus replaces both, as described below.
- The method rules are gaia's: not private, not static, not in an interface, one parameter (the event, which is not primitive, an array, a type variable or a parameterized type), returning `void`, with no checked exception. A method carries a single subscribing annotation: `@Subscribe` with `@OnEdt` or `@OnVirtualThread` is a compile error. A method that overrides a subscriber receives its events without the annotation; annotating it again is a compile error. gaia-processor checks these rules.

Build setup, for every module with subscribers, which the framework's modules are not, since they have no subscribers of their own: the dependency `be.imgn.gaia:gaia-core`, which core already declares; `be.imgn.gaia:gaia-processor` on the annotation processor path, never on the class path; and, on the module path, gaia's `register-services` goal, which applications already add. gaia-processor generates a subscriber index per class, `$X$$Subscribers implements be.imgn.gaia.core.event.spi.SubscriberIndex`, registered under that service name in `META-INF/services`; the bus finds a class's index with `ServiceLoader`, by the index's `listenerType()`, not by its name; `register-services` adds the matching `provides be.imgn.gaia.core.event.spi.SubscriberIndex with ...` directive. Core's descriptor already `requires transitive be.imgn.gaia.core`; `swing-ui`, which exports its own subscribing annotation, `requires transitive be.imgn.gaia.core` too; `EdtExecutor` is in its exported package `be.imgn.swing.ui`, as gaia-processor requires of an executor. Milestone 1's internal class of that name, the `UiExecutor` of the UI module, is renamed `EdtUiExecutor`. A module whose subscribers use `@OnEdt` requires `be.imgn.swing.ui`, not `requires static`, since the generated code calls `new EdtExecutor()`.

### The application's bus

Core binds an `EventBus` singleton in the root injector, `@Eager` ([eager singletons](#imgn-devinject)), built with `EventBus.newBuilder()`:

- **Executors.** For each `@IntoSet(EventExecutor.class)` bean, `executor(annotation(), executor())`. `EventExecutor` (core) has `Class<? extends Annotation> annotation()` and `Executor executor()`. Core contributes `@OnVirtualThread`'s: it runs each task with `TaskService.run`, in the application scope, so on a virtual thread named `task`, and so that shutdown cancels and waits for running deliveries as for any task. gaia's bus gives an executor one task per method and event, without the instance it belongs to, so the delivery can't run in the instance's own scope: closing a window's or view's scope closes the subscriptions of its instances, so that a delivery that hasn't started doesn't call the method, but it doesn't cancel a delivery that is running. An event published once shutdown has started, when no exit veto stopped it, or once the application scope is closed, reaches no `@OnVirtualThread` method: its deliveries are dropped, with a `DEBUG` log, rather than started after shutdown has cancelled and awaited the running tasks, or in a closed scope. The UI module contributes `@OnEdt`'s: it hands each task to the `UiExecutor`, so that `AppTester` runs those deliveries as it runs the other posted work. The bus therefore never creates `VirtualThreadExecutor` or `EdtExecutor`.
- **Failures.** `exceptionHandler((event, exception) -> failureHandler.failed(exception))`: an exception thrown by a subscriber, or by an executor, goes to the `FailureHandler`, and the event goes on to its other subscribers. An `Error` thrown by a `@Subscribe` or `@OnEdt` method is not caught, as gaia specifies; one thrown by an `@OnVirtualThread` method is caught by the task that runs it, and goes to the `FailureHandler` as a task's failure does. The bus's own messages are logged under `be.imgn.gaia.core.event.EventBus`.
- **No bus executor.** A `@Subscribe` method runs on the thread that publishes, before `publish` returns, unless `publish` is called from a subscriber, as described below.

Delivery follows gaia's bus:

- An event reaches the subscribers of its class and of all its supertypes, so a subscriber of a sealed interface receives every variant: the subscribers of a type before those of its supertypes, and those of one type in the order they subscribed.
- An event published from a subscriber is queued on that thread, and delivered once the event being delivered has reached all its subscribers.
- For a method with a subscribing annotation, the bus hands the executor a task per method and event, in delivery order, and goes on without waiting. The EDT runs them in that order. `@OnVirtualThread` deliveries reach their method in no particular order, and one method may run on several threads at once, so its listener must be thread-safe; the framework's executor keeps both properties.
- A task that runs after its subscription was closed doesn't call the method.
- `publish` may be called from any thread.

### Registration

- The framework registers on the bus every instance that the construction hook reports ([milestone 1](milestone-1.md#imgn-devinject)). inject's hook reports every instance the container builds, singletons included, as its 0.1.1 release does, except those built while the listeners themselves are resolved. The registering listener doesn't depend on the bus: the hook runs inside the reported instance's own resolution, so resolving the bus while the bus or one of its dependencies is being built would be a circular dependency. The bus being an eager singleton, it is built while the root injector is created, after the listeners exist, so that it and its dependencies are reported too. Core's `@Provides` method of the bus injects the registering listener, and hands it the bus once it is built. Until then, the listener keeps each instance reported, with the scope it is cached in, in a queue; it then registers the queued instances, in the order they were reported, and from then on registers each instance reported at once.
- The listener calls `EventBus.register` on every such instance, and takes its `IllegalArgumentException` to mean that the instance's class, and the classes it extends, have no subscribers. `register` alone decides which instances it accepts, so the framework doesn't look up gaia's subscriber indexes itself.
- An instance built on the thread that builds a view, during that build, is registered when the build ends, as its commands are [attached](milestone-1.md#attaching), so that no event reaches a view still being built. An instance built meanwhile on another thread doesn't belong to that build, and is registered at once.
- The subscription returned is registered with `onClose` in the scope the instance is cached in: the application scope for a singleton, its window's or view's scope otherwise. Closing the scope unsubscribes it. An instance of a class without a scope, which only a class compiled without swing's processor can be (see below), is cached nowhere: its subscription is registered in the scope of the injector that built it, the application scope for the root injector, its window's or view's scope otherwise, so that a library's beans subscribe as the application's do.
- An instance with subscribers built by an injector that hosts no window or view, such as a child injector that application code creates, has no scope that would unsubscribe it: the construction hook throws `IllegalStateException` naming its class when it reports the instance, so that the instance's resolution fails, even during a view's build.
- The class that declares subscribers must be `@Singleton`, `@WindowScoped` or `@ViewScoped`, declared or inherited; a class without a scope is a compile error, as for commands: its subscriptions would belong to no scope. swing's processor checks it, for methods with `@Subscribe` or with any annotation meta-annotated `@SubscribingAnnotation`.
- A `@Singleton` class with subscribers must also be `@Eager` ([eager singletons](#imgn-devinject)), declared on the class itself, even when its `@Singleton` is inherited; without it, it is a compile error. An eager singleton is built with the root injector, so its subscriptions start before startup, rather than whenever something first injects it, which a singleton that only listens would never be. Eager singletons are built on the thread that creates the root injector, not on the EDT, so their constructors and `@PostConstruct` methods build no components. They are not dependencies of the registering listener: a dependency of the listener is built before any listener exists, and isn't reported.
- Code that subscribes a lambda with `EventBus.subscribe` gives the subscription an owner itself: `scope.onClose(bus.subscribe(ContactSelected.class, this::show))`.

---

## Settings

A settings record holds what the user chooses and keeps between runs, such as a font size. It is a `@TomlConfig` record, which toml's processor binds to and from TOML, annotated `@Settings`:

```java
@Settings("editor")
@TomlConfig
public record EditorSettings(int fontSize, boolean wordWrap) {

    public EditorSettings() {
        this(14, true);
    }
}
```

- `@Settings` (core, `CLASS` retention, on types) takes the record's id, the name of its file: `editor` is saved in `editor.toml`. An id is lower-case kebab case: one or more groups of `a`-`z` and `0`-`9`, separated by single `-`.
- The processor checks that the annotated type is a record, that it is also annotated `@TomlConfig`, that it has a public constructor with no parameter, which gives the defaults, and that the id is valid. Each is a compile error otherwise. Two settings records with the same id in one compilation are a compile error; across compilations, startup fails with an error naming both records.
- The processor generates `$EditorSettings$$Settings implements SettingsDescriptor<EditorSettings>` (`$Outer$Nested$$Settings` for a nested record), with `Class<T> type()`, `String id()`, `T defaults()`, which calls the constructor with no parameter, and `Set<String> nullable()`, the TOML keys of the record's `@Nullable` components, and registers it with `ServiceFiles`. `SettingsDescriptor` is in `be.imgn.swing.spi`; core declares `uses SettingsDescriptor`.

```java
public interface SettingsStore {                                       // core, singleton
    <T extends Record> Property<T> get(Class<T> type);
}
```

`get` returns the same property on every call for a type, and throws `IllegalArgumentException` for a type that has no descriptor. Called before the [loading step](#loading) has run, such as from an earlier startup step, it throws `IllegalStateException`.

### Loading

A startup step (order 150, between resources and window state) loads every settings record whose descriptor `ServiceLoader` finds, those that another application of the same module declares included, from `<id>.toml` in the `config()` directory of the [`ApplicationDirectories` bean](milestone-1.md#declaration-and-launch), which the step injects:

- A record whose binder or writer is missing fails startup, whatever its file: it is a packaging error, not an invalid file.
- A missing file gives the defaults, and no file is written.
- Otherwise, the file's table is merged over the table of the defaults, written with toml's [writer](#upstream-changes), at the level of the record's components only: a key the file has replaces the default's value whole, a table included, so that an entry removed from a map stays removed. The `@Nullable` components are left out of the defaults' table first: toml writes no `null`, so a `@Nullable` component the file lacks is `null`, as the user may have set it, rather than its default. The merged table is bound with toml's binder, ignoring unknown keys. A key the file lacks, such as a component added by a newer version of the record, therefore takes its default, unless it is `@Nullable`, and a key the record no longer has is ignored.
- A file that doesn't parse or doesn't bind gives the defaults, with a warning naming the file and the error. The file is moved to `<id>.toml.invalid`, replacing a previous one, so that the next save doesn't destroy what the user wrote.
- A file that can't be read, such as one the user may not read, or one that can't be moved, gives the defaults, with a warning naming the file and the error, and the record isn't saved during that run, so that the file stays as the user left it.

### Saving

- Setting a settings property to a different value saves the record. Saves run on virtual threads named `settings`, one at a time per record. A save writes the property's latest value, so changes made while a save runs cause one more save, of the last value.
- A save writes every component of the record, defaults included, with toml's writer and `Toml.writeAtomically`, creating the config directory with `Files.createDirectories` when it is missing.
- A save that fails goes to the `FailureHandler`; the property keeps its value.
- Saves are not tasks: shutdown's cancellation of running tasks doesn't interrupt them. A shutdown step (order 300, after the windows are disposed, so that values set by `@PreDestroy` methods are saved) waits up to 2 seconds for the pending saves, then logs a warning naming the records it couldn't save. A value set while the root injector closes, after that step, is not saved.

Settings are observable like any property. A view follows them with `ui.bind(editor::setFont, settings.map(s -> font(s.fontSize())))`.

---

## Tasks and commands

Milestone 1's ports write by hand what a command does with its task: a generation counter so that an older run ignores its results, a `stopEnabled` property and a count of the loads in flight, the same call at the end of every hook, a `Semaphore` for a serial executor, and a sleeping loop for a periodic update ([ideas](ideas.md)). Milestone 2 provides them.

### Running state

`BoundCommand` gains:

| Method | Returns |
|---|---|
| `Observable<Boolean> running()` | `true` from the moment the command starts a task until every task it started has ended, waiting ones included (see [Concurrency](#concurrency)); `false` otherwise. A command whose handler returns `void` is never running |
| `void cancel()` | Cancels every task the command started that hasn't ended, waiting ones included. Does nothing when there is none. It may be called from any thread, and runs on the EDT, as `execute()` does |

- A task has ended once its last hook has run: `onSuccess`, `onFailure` or `onCancel`, then `onEnd`. `running()` changes on the EDT, after those hooks.
- A handler that returns `Task.none()` starts nothing, so the command doesn't become running.
- The running state, cancellation and [concurrency](#concurrency) belong to the `BoundCommand`, and so to the view: a command of a `@Singleton` or `@WindowScoped` class has one per view that uses it, each with its own tasks.
- A hook that throws, whichever it is, an `Error` included, goes to the `FailureHandler`, and the task proceeds as if it had returned: the remaining hooks run, `onEnd` included, the task ends, and the command's running state and waiting tasks follow.

### Cancellation

A task that is cancelled before its hooks run runs `onCancel`, then `onEnd`, and never `onSuccess` or `onFailure`, even when its work had already returned or thrown, so that its results never arrive. This holds however it is cancelled: by `BoundCommand.cancel()`, a stop command, a restarting activation, the cancellation of a chain, the Cancel button of the glass pane, or the closing of its scope. In milestone 1, work that returned normally after its cancellation ran `onSuccess`.

### Stop commands

```java
@Command(concurrency = Concurrency.RESTART)
Task<Integer> go() { ... }

@Command(cancels = "go")
void stop() {}
```

- `cancels` names a command of the same class, declared or inherited, whose handler returns a `Task`. The stop command is enabled while that command is `running()`; its activation cancels that command's tasks, then calls its own handler, which may do nothing.
- A stop command's handler returns `void`, and it has neither `enabled`, `selected`, a `block` other than `NONE` nor a `concurrency` other than `PARALLEL`. `cancels` on a `@Command` field is a compile error, as are the other cases (see [Build-time checks](#build-time-checks)).

### Concurrency

`@Command` gains `concurrency`, which says what an activation does while a task the command started still runs:

| `Concurrency` (UI) | The new activation |
|---|---|
| `PARALLEL` (default) | Starts its task at once, as in milestone 1 |
| `RESTART` | Cancels the command's tasks that haven't ended, then starts its own. The cancelled ones run `onCancel` and `onEnd`, as any [cancelled](#cancellation) task does |
| `SERIAL` | Starts its task once every task the command started before it has ended, in activation order. A waiting task holds no thread and blocks no input; cancelled while it waits, it never starts, and runs `onCancel` and `onEnd` |

- The handler is called at every activation, whatever the concurrency: with `SERIAL`, it returns the task that waits.
- With `RESTART`, the handler is called first. If it returns a task, the command's tasks that haven't ended are cancelled, their `onCancel` and `onEnd` run, and only then does the new task start. If it returns `Task.none()` or throws, nothing is cancelled. The cancelled tasks' hooks so run after the new handler: what the handler shows is followed by their hooks.
- Closing the scope cancels the tasks that wait as it cancels the running ones: they run `onCancel` and `onEnd`, and never start. Shutdown cancels them with the running tasks. Nothing starts in a closed scope.
- A concurrency other than `PARALLEL` requires a handler that returns a `Task`, and excludes `block = COMMAND` and `COMPONENT`, which disable the command while its task runs, so that there would be no second activation. Both are compile errors.

### Hooks and chains

`Task` gains:

```java
Task<T> onEnd(Runnable handler);
<R> Task<R> then(Function<? super T, ? extends Task<R>> next);
```

- `onEnd`'s handler runs through the `UiExecutor`, on the EDT with the UI module, after `onSuccess`, `onFailure` or `onCancel`, however the task ended, so code that must run in every case is written once.
- `then` returns a task, the chain, that runs this task and, when it succeeds, calls `next` with its result through the `UiExecutor`, after this task's own `onSuccess`, then runs the task `next` returns, in the same scope.
  - The chain succeeds with the second task's result. It fails or is cancelled when either task fails or is cancelled; once the first task has failed or been cancelled, `next` isn't called. A chain whose `next` returns `Task.none()` succeeds with `null` after the first task.
  - Cancelling the chain cancels whichever task runs.
  - Run by a command or by `Ui.run`, the chain blocks input once, from the start of the first task to the end of the second, rather than once per task.
  - This task's own hooks still run, for it alone; the chain's hooks run once, for the chain. A failure reaches the `FailureHandler` once, and only when neither the failing task nor the chain has `onFailure`.
  - `next` throwing fails the chain with that exception; `next` returning `null` fails it with a `NullPointerException`.
  - The first task belongs to the chain: running it on its own throws `IllegalStateException`, as does calling `then` on a task that has started or that already belongs to a chain, and `next` returning such a task fails the chain with that exception.
  - `Task.none().then(next)` returns `Task.none()`: there is nothing to run, so `next` is never called. `Task.none()` is one shared instance, which no chain owns.
  - The glass pane's Cancel cancels the chain. Its indicator follows the step that runs, its progress and message, without restarting its delay between the steps.
  - Run by a command, every step of the chain runs on a thread named after the command, and counts as the command's task, so `execute()` in tests waits for the whole chain.
  - Chains chain: `a.then(f).then(g)` runs three tasks.

### Partial results

```java
@FunctionalInterface
public interface PublishingWork<T, P> {                                // core
    T run(TaskContext context, Consumer<? super P> publish) throws Exception;
}
```

`Task` gains `static <T, P> Task<T> of(PublishingWork<T, P> work, Consumer<? super List<P>> onPublish)`:

- The work calls `publish` any number of times while it runs, from any thread. `onPublish` receives the published values through the `UiExecutor`, on the EDT with the UI module, in order, in batches: each call holds the values published since the previous one, so values published faster than the EDT takes them reach it in one call, as `SwingWorker.process` does.
- Every value published before the work returns or throws reaches `onPublish` before `onSuccess` or `onFailure` runs.
- An `onPublish` that throws goes to the `FailureHandler`, as a hook does, and the later batches are still delivered.
- Once the task is cancelled, the values not yet delivered are dropped, and `onPublish` isn't called again.
- `publish(null)` throws `NullPointerException`; calling `publish` after the work has returned throws `IllegalStateException`.

### Periodic actions

`Ui` gains `Subscription every(Duration period, Runnable action)`:

- It runs `action` on the EDT every `period`, the first time one period after the call, until the returned subscription or the view's scope is closed.
- The period is measured with the `Ticker` bean, so tests drive it with the tester's `FakeTicker`. A late run doesn't make up for the runs it missed: the next one comes one period after it.
- The actions are kept by an internal bean of the UI module that runs those whose period has elapsed. The running application polls it with a `javax.swing.Timer` every 50 ms, as the glass pane polls its delay; the tester starts no timer, and polls it while it settles.
- An exception thrown by `action` goes to the `FailureHandler`, and the action keeps its schedule. An `Error` is not caught.
- A `period` that isn't positive throws `IllegalArgumentException`.

---

## Binding lists and tables

`Ui` (UI, `@ViewScoped`) gains methods that show an observable list in a `JList` or a `JTable`, optionally with a single selection bound to a property:

| Method | Binds |
|---|---|
| `<E> void bind(JList<E> list, ObservableList<E> items, Function<? super E, String> text)` | The list's model to `items`, and its cell renderer to `text` |
| `<E> void bind(JList<E> list, ObservableList<E> items, Function<? super E, String> text, Property<Optional<E>> selection)` | The same, with the selected element |
| `<E> void bind(JTable table, ObservableList<E> rows, List<Column<E, ?>> columns)` | The table's model to `rows`, with one column per `Column`, in order |
| `<E> void bind(JTable table, ObservableList<E> rows, List<Column<E, ?>> columns, Property<Optional<E>> selection)` | The same, with the selected row |
| `<E> void bind(JList<E> list, ObservableList<E> items, Function<? super E, String> text, Property<Optional<E>> selection, Function<? super E, ?> key)` | The same as with a selection, matching the selected element by key |
| `<E> void bind(JTable table, ObservableList<E> rows, List<Column<E, ?>> columns, Property<Optional<E>> selection, Function<? super E, ?> key)` | The same as with a selection, matching the selected row by key |

- Each method sets a new model on the component, which follows the list as described in [Delivery of list changes to the EDT](#delivery-of-list-changes-to-the-edt). The subscription belongs to the `Ui`'s view scope. Binding a component a second time throws `IllegalStateException`.
- The list's cell renderer is a `DefaultListCellRenderer` showing `text` of the element.
- A table's model index of a column is its position in `columns`. The table has no row sorter (`setAutoCreateRowSorter(false)`), so a view index of a row is its model index, and so its index in the list. A table bound in `content()` has its columns before window state is restored, so their order and widths are restored ([milestone 1](milestone-1.md#window-state)).

### Columns

```java
public final class Column<E, V> {                                      // UI
    public static <E, V> Column<E, V> of(String header, Class<V> type, Function<? super E, ? extends @Nullable V> value);
    public Column<E, V> editable(BiFunction<? super E, ? super V, ? extends E> with);
}
```

- `header` is the column's header text; it comes from typed messages, like any text that code holds. `type` is what the model's `getColumnClass` returns, so the table's default renderers apply: check boxes for `Boolean`, right-aligned numbers for `Number`. A `null` value shows an empty cell.
- `editable` returns a new column whose cells can be edited. When an edit is committed, on the EDT, the binding replaces the row with `rows.set(index, with.apply(row, value))`: rows are typically records, and `with` returns a copy with the new value. The change comes back through the list's listener, as any other change. If the list no longer holds an element equal to the edited row at that index, because another thread changed it while the change was queued, the edit is dropped. An edit that commits no value, such as a number cell left empty, is dropped.
- Editing uses the table's default editors, so `editable` accepts only the columns whose `type` is `String`, `Boolean`, `Integer`, `Long` or `Double`, and throws `IllegalArgumentException` for any other.

### Selection

The selection binding sets the component's selection mode to `ListSelectionModel.SINGLE_SELECTION`, and keeps the selection and the property in step:

- When the user selects an element, the binding sets the property to it, once the selection is no longer adjusting; when the selection becomes empty, to `Optional.empty()`. Selection events that the binding's own updates cause, such as `JTable` clearing its selection on a data change or the selection model shifting on a removal, are not the user's: they don't set the property.
- When the property changes, its update, on the EDT, first applies the list's queued changes to the copy, so that an element added then selected is found, then keeps the selected element if it is already equal to the value, and otherwise selects the first element of the copy equal to it, and scrolls to it. An empty value clears the selection. A value that isn't in the list clears the selection and sets the property to `Optional.empty()`.
- After applying the queued changes of the list, the update keeps the selection on the selected element. It tracks the selected index through each change, in order: any change before it, a `Replaced` one included, shifts it, a `Removed` change that holds it loses it, and a `Replaced` change of one element by one at that index keeps it, on the new element. Once every change is applied, the element at the tracked index keeps the selection, and the property is set to it when it differs from the value, which keeps a contact selected after it is modified. If the index was lost, or a `Replaced` change of another size, or of more than one element, covered it, the selection moves to the first element equal to the property's value, or is cleared, with the property set to `Optional.empty()`, when no element is equal to it.

- **Keys.** With a `key`, elements are matched by their key rather than by equality, keys being compared with `Objects.equals`: in each rule above, an element equal to the value reads an element with the value's key. After a change of the list, the element with the selected element's key keeps the selection, wherever it moved, and the property is set to that element when it differs from the value, so a contact modified, moved or reloaded stays selected. The key rule replaces the tracking of the index: with a key, a replaced element keeps the selection only if it keeps the key. Without a `key`, the index is tracked as above.
- **The property after a change of the list.** With or without a key, the update sets the property, to the element that keeps the selection or to `Optional.empty()`, only if the property still held the value the selection showed before the changes. Otherwise the property changed since, and its own update, queued, applies its value.

---

## Forms

A form binds the input components of a view to properties, validates their values, and tracks whether they changed. Each view has one:

```java
@ViewScoped
public final class Form {                                              // UI
    public Binding<String> bind(JTextComponent field, Property<String> property);
    public <T> Binding<T> bind(JTextComponent field, Property<T> property, Converter<T> converter);
    public Binding<Boolean> bind(AbstractButton button, Property<Boolean> property);
    public <E> Binding<Optional<E>> bind(JComboBox<E> combo, ObservableList<E> items,
            Function<? super E, String> text, Property<Optional<E>> selection);
    public void track(Observable<?> value);

    public Observable<Boolean> valid();
    public Observable<Boolean> dirty();
    public boolean validate();
    public void markClean();
}

public final class Binding<T> {                                        // UI
    public Binding<T> check(Rule<? super T> rule);
    public Binding<T> check(Validator<? super T> validator);
    public Binding<T> revalidateOn(Observable<?> other);
    public Observable<Validation> validation();
    public Observable<Boolean> shown();
    public Observable<Boolean> validated();
}
```

Every method of `Form` and `Binding` is called on the EDT, and every subscription they make belongs to the view's scope. Binding a component a second time throws `IllegalStateException`.

### Bindings

The bindings are two-way. In both directions, the component is changed only on the EDT, following the [delivery rule](milestone-1.md#delivery-to-the-edt): the property may be set from any thread.

- **Text.** The field's text is set from the property when bound. Each change of the document the field has when bound (insert, remove, change) sets the property: to the text, or, with a converter, to `converter.parse(text)`. When the property changes, its update sets the field's text, unless the text already equals the value or, with a converter, already parses to an equal value, or the converter doesn't parse the text and the value is the last one the field's text parsed to or was set from, so that typing is never interrupted and the caret stays in place.
- **Buttons.** `AbstractButton` covers check boxes, toggle buttons and radio buttons. The button's selected state is set from the property when bound; an item event sets the property; a change of the property sets the selected state.
- **Combo boxes.** The combo box shows `items`, through a model that follows the list as described in [Delivery of list changes to the EDT](#delivery-of-list-changes-to-the-edt), with a renderer showing `text` of each element. The binding makes the combo box not editable. Its selection and `selection` are kept in step as for [lists](#selection), `Optional.empty()` meaning no selected item. A combo box matches its items by equality, so the user's choice among equal elements selects the first of them, unless one of them is already selected, which stays.

Changes that a binding makes to its own component, from the property, are not user changes: they don't [show errors](#feedback). The document events that its own `setText` causes, a removal then an insertion, don't set the property, so the property never passes through an empty text.

A user change of a component sets the property, and updates `validation()`, `valid()` and `dirty()`, synchronously on the EDT, before the change's event returns: a command whose `enabled` reads them, executed right after, sees the new state. Only the updates of components, from properties, are always posted, following the delivery rule, among the framework's updates.

A change of a property, or of an observable that a binding revalidates on or that the form tracks, made on the EDT, also validates the bindings that depend on it, and updates `valid()` and `dirty()`, before it returns; a change of an observable list made on the EDT does so once the list has released its lock, before the modifying method returns. A change made on another thread does so in a posted update, among the framework's updates.

### Conversion

```java
public interface Converter<T> {                                        // UI
    T parse(String text);
    String format(T value);
    String requirement();

    static <T> Converter<T> of(Function<String, ? extends T> parse, Function<? super T, String> format,
            String requirement);
    static <T> Converter<Optional<T>> optional(Converter<T> converter);
}
```

- `parse` rejects a text by throwing `IllegalArgumentException` or `DateTimeException`, which `NumberFormatException` and `DateTimeParseException` extend. The binding then leaves the property unchanged. `requirement()` is a message from typed messages that names the format the text must have, such as "A whole number": it is the first [check](#validation) of the binding. Any other exception is a bug in the converter: it goes to the `FailureHandler`, the property is left unchanged, and the converter's check and the rules' are `UNKNOWN`. An `Error` is not caught (see [Errors](#changes-to-milestone-1)).
- A `format` that throws is a bug in the converter too: the exception goes to the `FailureHandler`, the field's text is left unchanged, and the converter's check and the rules' are `UNKNOWN`.
- `optional` converts an empty or blank text to `Optional.empty()`, and any other text with `converter`; it formats `Optional.empty()` as an empty text. Its `requirement()` is `converter`'s.

### Validation

A validation gives the status of every requirement of a value, met or not, so that a view can show each requirement turning from unmet to met as the user types, and not only the first error:

```java
public interface Rule<T> {                                             // core
    String requirement();                                             // a message from typed messages, such as "At least 8 characters"
    boolean test(T value);                                            // whether the value meets the requirement

    static <T> Rule<T> of(String requirement, Predicate<? super T> test);
    static Rule<String> required(String requirement);                 // met when not blank
}

public record Validator<T>(List<Rule<? super T>> rules) {              // core
    @SafeVarargs
    public static <T> Validator<T> of(Rule<? super T>... rules);
    public Validation validate(T value);
}

public record Validation(List<Check> checks) {                         // core
    public boolean valid();                                           // every check is MET
    public Optional<String> error();                                  // the requirement of the first check that isn't MET
}

public record Check(String requirement, Status status) {               // core
    public enum Status { MET, UNMET, UNKNOWN }
}
```

- A rule's requirement names what the value must be, not what is wrong with it, so that the same message reads right whether the check is met or not.
- A validator holds the rules of a value, so that a domain module declares them once and tests them in a form, a command-line tool or a service. Requirements are texts of the display locale, so a validator is built from typed messages, by a factory rather than as a constant: `static Validator<String> password(AccountMessages messages)`. `validate` tests the value against every rule, in order, and gives one check per rule: `MET` or `UNMET`.
- The constructors of `Validator` and `Validation` copy their lists.
- `Binding.check` adds a rule, or the rules of a validator in their order, to the binding and returns the binding. The binding's validation has a check per requirement: first the converter's, if it has one, then one per rule, in the order they were added. Every rule is tested at each validation, whether or not an earlier one failed.
- When the converter rejects the field's text, its check is `UNMET` and the rules' are `UNKNOWN`: the property doesn't hold the text, so they can't be tested. Otherwise the converter's check is `MET` and the rules are tested against the property's value.
- A rule that throws is a bug in the rule: the exception goes to the `FailureHandler`, and the rule's check is `UNKNOWN`. An `Error` is not caught.
- A binding is validated when it is bound, at each change of its component and at each change of its property. `validation()` holds the last validation.
- `revalidateOn` validates the binding again at each change of `other`, on the EDT, and returns the binding. A rule that compares the value with another field reads that field's property, and the binding revalidates on it: a password confirmation checks `Rule.of(messages.matches(), s -> s.equals(password.get()))` and revalidates on `password`. A change of `other` is not a user change of the binding's component: it doesn't change `shown()` or `validated()`.
- `shown()` is `false` until the user changes the component or `validate()` is called, then `true` for the binding's life. A view that lists the requirements uses it to show the unmet ones as not yet checked rather than as failed, before the user has typed anything.
- `validated()` is `false` until `validate()` is called, then `true` until the user changes the component, and `true` again at the next `validate()`. A change of the property, or of an observable the binding revalidates on, doesn't change it. A view that lists the requirements uses it to show them as failed after `validate()`, such as on OK, even while the value is empty, which it otherwise shows as not yet checked.
- `Rule`, `Validator`, `Validation` and `Check` are in core, so that a domain module can declare and test the rules of its values without Swing.
- `valid()` is `true` while every binding's validation is valid, whether or not the errors are shown.

Limitation:

- **Rules are synchronous and cheap.** Every rule of a binding is tested on the EDT at each change, so a rule that takes time, or that asks a server, such as whether a user name is taken, can't be a rule.

### Feedback

A binding's error, `validation().get().error()`, is shown on its component while `shown()` is `true`, and hidden as soon as the binding has no error. A shown error that changes is shown again with its new message.

`validate()` shows the error of every binding that has one, moves the focus to the first component with an error, in binding order, and returns `valid().get()`. When that component is in a tab of a `JTabbedPane` that isn't selected, `validate()` selects the tab first, from the outermost pane in. It then focuses the component through its window's `Window.focus(Component)`, which the real window does with `requestFocusInWindow` and the tester's headless window records. A component that isn't visible up to its window's root pane, such as one on another card of a wizard, isn't focused.

Errors are shown by the `ValidationFeedback` bean (UI), on the EDT:

```java
public interface ValidationFeedback {
    void show(JComponent component, String error);                     // shows the error, or its new message
    void hide(JComponent component);                                   // hides the error, if one is shown
}
```

- `swing-ui` provides a `@Provides @Default` one. It wraps the component's border in a compound border, with a red one-pixel `LineBorder` outside it, sets the component's tool tip text and accessible description to the message, and restores the three when the error is hidden.
- `swing-flatlaf` provides one that replaces it, through inject's `@Default` rule. It sets the component's `JComponent.outline` client property to `"error"`, sets its tool tip text and accessible description to the message, and restores them when the error is hidden.
- For a component in the viewport of a `JScrollPane`, such as a text area, the feedback is shown on the scroll pane, which is what draws the component's border.

### Dirty tracking

- Each binding and each `track`ed observable has a baseline: its property's value, or the observable's, when it is bound or tracked. A combo box binding's baseline is its value once bound, after the combo box has emptied a value its list doesn't hold.
- `dirty()` is `true` while the value of at least one of them differs from its baseline (`Objects.equals`). Editing a field back to its original value makes the form clean again. A text a converter rejects leaves the property unchanged, so it doesn't make the form dirty.
- `track` adds an observable that has no component in the form, such as an observable list edited in a table.
- `markClean()` makes every current value the new baseline.

A command declares its enabled state from the form like from any observable: `@Command(enabled = "canSave")` on a field `@Exposed final Observable<Boolean> canSave;`, assigned in the constructor with `this.canSave = form.dirty();`, exposed as [milestone 1](milestone-1.md#exposed-fields) requires of the fields that generated code reads. Dirty state asks nothing at exit in milestone 2: save prompts come with documents, in milestone 3.

---

## Dialogs with forms

### OK, Cancel and Apply

The standard commands gain `apply()`, in dialog views only, like `ok()` and `cancel()`: outside a dialog view's scope, calling it throws `IllegalStateException`. Its text comes from the framework's resources, in English (`&Apply`) and French (`&Appliquer`). `apply` joins the standard command ids a class may override ([milestone 1](milestone-1.md#overriding-standard-commands)).

`DialogView` gains a hook:

```java
public interface DialogView<R> extends View {
    Optional<R> result();                                              // read after ok()
    default List<BoundCommand> buttons(StandardCommands commands) {
        return List.of(commands.ok(), commands.cancel());
    }
    default void commit() {}                                           // applies the form's values
}
```

The dialog's form is the `Form` bean of the dialog view's scope, which the framework resolves through the dialog's view injector: a dialog that binds nothing has an empty form, valid and clean.

| Command | Enabled | Activation |
|---|---|---|
| `ok()` | Always | If `validate()` returns `false`, nothing more: the dialog stays open, showing the errors. Otherwise, if the form is dirty, calls `commit()`; then closes the dialog with `result()` |
| `apply()` | While the form is dirty | If `validate()` returns `false`, nothing more. Otherwise calls `commit()`, then `markClean()`; the dialog stays open |
| `cancel()` | Always | Closes the dialog with an empty result, as in milestone 1, whatever the form's state |

- `ok()` stays enabled while the form is invalid, so that activating it shows the user what is wrong, rather than leaving a disabled button with no explanation.
- A dialog with an Apply button lists it: `buttons` returns `List.of(commands.ok(), commands.cancel(), commands.apply())`.
- Enter still activates `ok()` and Escape `cancel()` ([milestone 1](milestone-1.md#dialogs)).

### Renaming standard commands in a view

A `[commands.<id>]` table for a standard command, in the resources of a view class or of one of its superclasses, without a handler of that id, overrides that standard command's resources in that view, key by key: the build already checks such tables ([milestone 1](milestone-1.md#build-time-checks)), and the view's `CommandRegistry` already builds the standard command from the framework's resources merged with the table, as `saf/SecondaryWindow` relies on. Milestone 2 documents this behavior of milestone 1 and doesn't change it. A dialog renames its OK button so, and keeps the standard handler that closes it:

```toml
# ContactDialog.toml
[commands.ok]
text = "&Save"
```

---

## Form layout and button rows

### Form panels

```java
public final class FormPanel {                                         // UI
    public static FormPanel create();
    public FormPanel row(JLabel label, JComponent field);
    public FormPanel wide(JComponent component);
    public FormPanel section(JLabel title);
    public JPanel build();
}
```

A `FormPanel` lays out a form in two columns, with `GridBagLayout`, in the order its methods are called:

- `row` adds a label and its field: the label at the line end of the first column, the field filling the width of the second. It calls `label.setLabelFor(field)`, so the label's mnemonic focuses the field.
- `wide` adds a component across both columns, filling the row's width and sharing the extra height with the other wide components, as a nested panel or a text area needs.
- `section` adds a title across both columns, in one cell that fills the row's width: a nested panel, with `GridBagLayout`, holding the label at its line start, then a `JSeparator` that fills the rest of the row, with an inset of 4 pixels on its leading side only. `GridBagLayout` mirrors the columns and the insets in a right-to-left orientation, such as one that `applyComponentOrientation` sets, so the gap stays on the label's trailing side.
- Every cell of the panel has an inset of 4 pixels on each side, and the panel an empty border of 4 pixels.
- When no `wide` component takes the extra height, the rows stay at the top of the panel, which leaves the extra height below the last row.
- `build` returns the panel. It may be called once: any call on the `FormPanel` afterwards throws `IllegalStateException`. Every method is called on the EDT.

The labels are `@Exposed` fields of the view, so their texts and mnemonics come from its resources, as for any component.

### Button rows

`Ui` gains `JPanel buttonBar(BoundCommand... commands)`: a panel with a centered `FlowLayout`, holding one button per command, in order, made with `createToggleButton()` for a command with a selected state and with `createButton()` for any other. Unlike the buttons of a toolbar, they show their text.

---

## Resources of components in components

In milestone 1, resources reach the `@Exposed` component fields of a view, and no further. A panel that several views hold, such as the address panel of three dialogs, sets the texts inside it from typed messages, while the views set its title. Milestone 2 reaches the component fields of those components, through nested tables:

```toml
# ContactDialog.toml
[home]
title = "Home Address"

[home.streetLabel]
text = "S&treet"
```

- In a table that names a component field, a key whose value is a table names an `@Exposed` component field of that field's declared type, declared or inherited; its keys are properties of that component, checked and applied as for the view's own fields. Tables nest to any depth: `[home.phone.label]`. Variants nest the same way: `[variants.<name>.home.streetLabel]`.
- The generated injector covers the nested paths that the resources of the view and of its superclasses compiled with it name, in their base files and in every locale, platform and variant file. It doesn't follow every component field of every component, which would never end for a component type that holds itself.
- `ComponentResources`'s lookups take a field path: a field name, or names joined by `.`, such as `home.streetLabel`, which reaches the nested table.
- The generated injector reads the nested component through the enclosing fields, `view.home.streetLabel`, after the view's constructor, as for the view's own fields. A nested field must therefore be reachable from the view's package, so `public` when its class is in another package, and set by the enclosing component's constructor. A `null` nested field that the resources name throws `IllegalStateException` naming its key path.
- The nested field's class needs no resources of its own, and a TOML file of its own isn't read for it: each view sets the texts of the components it holds, so two views may name one panel's labels differently. A view whose superclass, compiled with it, names the same nested key overrides it key by key, as resources merge.
- Window state stays that of the view's own fields, as in milestone 1.

Limitation:

- **Superclasses compiled separately.** The processor sees only the resources of its own compilation, so a superclass from another compilation, such as one in a library, can't give nested resources to its subclasses.

---

## About dialog and application icon

The application class's resources take four more plain values, which the processor checks as it checks `name` and `theme`:

| Key | Type | Used for |
|---|---|---|
| `description` | String | A sentence about the application, in the About dialog |
| `copyright` | String | The copyright notice, in the About dialog |
| `website` | String, an absolute `http` or `https` URI | The application's website, in the About dialog |
| `icon` | String, an icon path as for commands | The application's icon, in the About dialog and on the main frame |

Being resources, `description` and `copyright` are translated like any text, and every value may have platform variants. A `website` that doesn't parse as an absolute `http` or `https` URI, or an `icon` that names no resource, is a compile error, once the `${…}` references of either are resolved within its own file, as [milestone 1](milestone-1.md#build-time-checks) does for icons: a reference to a key the file lacks, which only the merged table resolves, is a warning, and the value goes unchecked; the icon's loader is checked at startup with the other icons ([milestone 1](milestone-1.md#icons)).

`AboutDialog`, package-private in milestone 1, becomes a public final class of `be.imgn.swing.ui`, a `DialogView<Void>` with no public constructor, so that tests answer it with `FakeDialogs.answerDialog(AboutDialog.class, ...)`. It shows, from top to bottom, each one that is present: the icon, the displayed name, `version`, `vendor`, `description`, `copyright` and `website`. The website is a read-only, selectable `JTextField` without border, so the user can copy it; opening it in a browser comes with the OS hooks of milestone 3.

`Window` gains `void setIconImage(Image image)`, and `void focus(Component component)`, which [forms](#feedback) use. Step 2 of the [single-frame startup](milestone-1.md#singleframe), which sets the title, also sets the main frame's icon image from `icon`: for an `ImageIcon`, its image; for any other icon, the icon painted into an ARGB `BufferedImage` of its size. An icon other than an `ImageIcon` whose width or height is zero sets no image, with a `WARNING` log naming the icon; the About dialog still shows it. `AppTester`'s headless `Window` records the image, which `AppTester.iconImage()` returns.

---

## Window state

Window state keeps the file of [milestone 1](milestone-1.md#window-state), keys and layout included, but is written and read through toml's generated code: its records are `@TomlConfig` records, written with toml's writer, and bound ignoring unknown keys. The hand-written conversion is removed.

Milestone 1's records hold `Optional`, `OptionalInt` and `Rectangle`, which toml's binder doesn't support. They become, in `be.imgn.swing.ui.internal`:

```java
@TomlConfig
record WindowStates(Map<String, SavedWindow> windows) {}

record SavedWindow(@Nullable List<Integer> bounds, @Nullable Boolean maximized,
        @Nullable Map<String, SavedComponent> components) {}

record SavedComponent(@Nullable Integer dividerLocation, @Nullable Integer selectedIndex,
        @Nullable List<SavedColumn> columns) {}

record SavedColumn(int modelIndex, int width) {}
```

- Their keys, which toml derives in kebab case from the components' names, are those of milestone 1's file: `bounds` (x, y, width, height), `maximized`, `components`, `divider-location`, `selected-index`, `columns`, `model-index` and `width`. A `null` component is left out, so a dialog's state has no `maximized`, and a window without saved components no `components`.
- `bounds` is checked once bound: other than 4 integers discards the file with a warning, as in milestone 1.
- `swing-ui` gains toml-processor on its annotation processor path, next to inject-processor.

- A state file with keys that the current version doesn't know, such as one written by a newer version, is no longer discarded: the unknown keys are ignored, and the rest is restored.
- A file that still doesn't bind, such as one with a value of the wrong type, is discarded with a warning, as in milestone 1.

---

## Changes to milestone 1

These change what milestone 1 specifies, from milestone 2 on:

- **Failures of handlers.** An exception thrown by a command handler or by a dynamic item's action goes to the `FailureHandler`, as a task's failure does. In milestone 1 it went nowhere the user could see. `BoundCommand.execute` returns `true` all the same, since the command ran.
- **Errors.** An `Error` thrown by a command handler, a dynamic item's action, a converter, a rule or a periodic action is not caught: it propagates to the caller, as one thrown by a `@Subscribe` or `@OnEdt` method does. Only tasks catch an `Error`: one thrown by a task's work fails the task, as in milestone 1, and one thrown by a hook, `onPublish` or `then`'s `next` is handled as an exception thrown there is.
- **Sealed types.** `Observable`, `Property` and `ObservableList` are sealed, and implemented only by core's observables, so that every observable delivers its changes as [Delivery](#changes-to-milestone-1) requires. `Task` is sealed, and implemented only by the tasks that `Task.of`, `Task.none()`, `then` and the other methods of `Task` return.
- **Preferences.** `PreferencesHandler.showPreferences()` becomes `void showPreferences(Ui ui)`. A `PreferencesHandler` is a singleton, which can't reach a view's `Ui` in milestone 1, and so can't open a dialog view. The `ui` is the `Ui` of the view whose `preferences()` command runs; on macOS's application menu, the main view's. The handler uses it during the call and must not keep it.
- **Cancellation.** A cancelled task never runs `onSuccess` or `onFailure`, even when its work returned or threw after the cancellation (see [Cancellation](#cancellation)).
- **`apply()`** joins the standard commands, and `commit()` joins `DialogView`. `ok()`, which closed the dialog, now validates the form first and calls `commit()` when it is dirty (see [OK, Cancel and Apply](#ok-cancel-and-apply)).
- **`Window.setIconImage` and `Window.focus`** join `Window` (see [About dialog and application icon](#about-dialog-and-application-icon)).
- **The About dialog** shows the description, copyright, website and icon, and `AboutDialog` becomes public (see [About dialog and application icon](#about-dialog-and-application-icon)). The class-properties check accepts `description`, `copyright`, `website` and `icon`, which milestone 1 rejected.
- **`EdtExecutor`.** The UI module's internal `UiExecutor`, `EdtExecutor`, is renamed `EdtUiExecutor`; `EdtExecutor` becomes the public executor of `@OnEdt` (see [Subscribing annotations](#subscribing-annotations)).
- **`CommandBuilder`** gains `concurrency` and `cancels` (see [Processor](#processor)).
- **Delivery.** The updates that the listeners of one change queue, those of nested changes included, reach the [delivery](milestone-1.md#delivery-to-the-edt) queue together, once every listener of that change has been called on that thread, even if one threw, so that the framework's updates still run before the views' `Ui.bind` targets (rule 4) when the EDT drains the queue meanwhile. A listener that opens a nested event loop, such as a modal dialog, so holds back the updates queued meanwhile until it returns, those of the changes made in the dialog included. The framework's updates of rule 4 also include those of the bindings of lists, tables and combo boxes, and of forms (see [Delivery of list changes to the EDT](#delivery-of-list-changes-to-the-edt) and [Bindings](#bindings)).
- **Settling.** Every tester call also waits for bus deliveries and settings saves, and runs the periodic actions that are due (see [Test harness](#test-harness)).
- **Window state** is written and read through toml's generated code (see [Window state](#window-state)).
- **Commands** gain `running()` and `cancel()` on `BoundCommand`, and `cancels` and `concurrency` on `@Command`; **tasks** gain `onEnd`, `then` and `Task.of(PublishingWork, Consumer)`; **`Ui`** gains `every` and `buttonBar` (see [Tasks and commands](#tasks-and-commands) and [Form layout and button rows](#form-layout-and-button-rows)).
- **Nested resources.** A table inside a component field's table names a component field of that component, where milestone 1 rejected it as an unknown property (see [Resources of components in components](#resources-of-components-in-components)).
- **Attaching.** A `<Class>Commands` instance constructed during a view's build waits for the build's end only if it is constructed on the thread that builds the view; one constructed meanwhile on another thread doesn't belong to that build, and is [attached](milestone-1.md#attaching) at once, as instances are registered on the bus (see [Registration](#registration)).
- **Generated names.** The names of generated types, `$Outer$Inner$$Kind` for a type and `$$Kind` for a package, come from gaia's `ClassName.generatedName` rather than from the processor's own helper (see [Processor](#processor)). The names of milestone 1 don't change, except that a `$` within a simple name is now written `$0`, as gaia's scheme requires so that no two types get one name.

---

## Build-time checks

Milestone 2 adds these checks to [milestone 1's](milestone-1.md#build-time-checks), with diagnostics attached to the Java element as there. The diagnostics of a nested table are attached to the view's own component field that encloses it, `home` for `[home.streetLabel]`, since the nested field may be declared in another file or another compilation:

| Check | Error when |
|---|---|
| Subscribers | A class with a method annotated `@Subscribe`, or with an annotation meta-annotated `@SubscribingAnnotation`, has no scope among `@Singleton`, `@WindowScoped` and `@ViewScoped`, declared or inherited, or is `@Singleton` without `@Eager` declared on it. The rules of the methods themselves are gaia-processor's |
| Settings | A `@Settings` type is not a record, is not annotated `@TomlConfig`, has no public constructor without parameter, or has an invalid id; or two settings records of the compilation have the same id |
| Stop commands | A `cancels` names no command of the class, declared or inherited, or one whose handler returns `void`; the command with `cancels` has a handler that doesn't return `void`, or also has `enabled`, `selected`, a `block` other than `NONE` or a `concurrency` other than `PARALLEL`; or `cancels` is on a `@Command` field |
| Concurrency | A `concurrency` other than `PARALLEL` is on a command whose handler returns `void`, on a `@Command` field, or with `block = COMMAND` or `COMPONENT` |
| Nested resources | A nested table names no `@Exposed` component field of the enclosing field's declared type, or a field the view's package can't reach; or one of its keys is not a supported property of that field's type |
| Class properties | A `description`, `copyright`, `website` or `icon` value is not a string; a `website`, its `${…}` references resolved within its own file, is not an absolute `http` or `https` URI; an `icon` names no resource. These values are allowed on the application class only |

The commands' check of `[commands.<id>]` tables also accepts `apply`, as a standard command.

---

## Processor

`swing-processor` gains two steps:

| Step | Triggered by | Generates |
|---|---|---|
| Subscribers | Every root element, as the views step | Nothing: it checks the scope of classes with subscribers, and that singletons among them are `@Eager` |
| Settings | `@Settings` | `$X$$Settings`, service entry |

The commands step also checks `cancels` and `concurrency`, which `<Class>Commands` passes to the commands it builds through two new methods of `CommandBuilder`: `concurrency(Concurrency concurrency)` and `cancels(String id)`. The command that `cancels` names is resolved when the view's commands are [attached](milestone-1.md#attaching), since the generated code builds commands in declaration order, and a stop command may come before the command it cancels. `CommandBuilder.bind()` throws `IllegalStateException` for what the build-time checks of concurrency and stop commands reject in the command itself; a `cancels` that names no command of its class whose handler returns a `Task` throws `IllegalStateException` when the view's commands are attached. The resources step checks nested tables, and `$X$$Injector` sets their properties through the enclosing fields.

The subscribers' code is generated by gaia-processor, which runs next to swing's, inject's and toml's processors. The settings descriptors are services that core finds with `ServiceLoader`, not beans. On the module path, gaia's `register-services` adds the `provides` clauses of both, `SubscriberIndex` and `SettingsDescriptor`, as for the other services.

Every `$`-named type swing's processor generates is named through gaia's `ClassName`, from gaia-processing 0.1.2 (`<Class>Commands`, `<Class>Menus` and `<Class>Factory` keep their names): `ClassName.of(type).generatedName(kind)` for a type (`$Outer$Inner$$Settings`), `ClassName.generatedName(packageName, kind)` for a package (`$$Resources`). The processor's own naming helper of milestone 1 is removed (see [Changes to milestone 1](#changes-to-milestone-1)).

---

## Test harness

```java
@Test
void applyChangesTheFontSize() {
    try (AppTester<NotesApp> app = AppTester.builder(NotesApp.class)
            .settings(new EditorSettings(12, true))
            .start()) {
        app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
            dialog.component(JTextField.class).setText("18");
            dialog.commands(StandardCommands.class).apply().execute();
            dialog.commands(StandardCommands.class).cancel().execute();
        });

        app.commands(NotesAppCommands.class).preferences().execute();

        assertThat(app.bean(SettingsStore.class).get(EditorSettings.class).get().fontSize()).isEqualTo(18);
        assertThat(app.component(JTextArea.class).getFont().getSize()).isEqualTo(18);
    }
}
```

`AppTester` gains:

| Method | Does |
|---|---|
| `Builder.settings(Record value)` | Joins milestone 1's [`AppTester.builder(type)`](milestone-1.md#test-harness), next to `locale` and `arguments`: writes a settings record to the fake config directory before the startup steps run, once the root injector, and its eager singletons, are built, with the store's own writer, a later value of the same record replacing an earlier one. When it and `Builder.file` write the same file, the `file` wins, whatever the order of the calls |
| `<B> B bean(Class<B> type)` | Resolves a bean through the main view's injector, so beans of every scope of the main window are reachable |
| `void run(Runnable action)`, `<R> R call(Supplier<R> action)` | Runs the action on the EDT and waits, then settles as every other call does. Tests change components this way, since the EDT checker fails a test that changes them on its own thread |
| `void publish(Object event)` | Publishes the event on the application's bus, on the EDT, then settles |
| `<E> List<E> published(Class<E> type)` | The events published on the application's bus since startup that are instances of `type`, in order. The tester records them with a subscription to `Object` |
| `void activate(BoundCommand command)` | Activates the command on the EDT as `execute()` does, with a `Programmatic` trigger, and runs the posted updates, but doesn't wait for the task it starts, so that a test can act while the task runs, such as activating it again or checking a stop command. Tests hold such tasks with latches |
| `Builder.file(Function<ApplicationDirectories, Path> path, String content)` | Writes a file before the startup steps run, as `Builder.settings` does, at the path the function gives in the application's fake directories, creating its directories, so that a test starts on a settings file, a data file or a window state of its choice |
| `Builder.fileSystem(FileSystem fileSystem)` | Uses this memory file system rather than a new one, and doesn't close it, so that a test starts the application a second time on what the first run saved |
| `Optional<Image> iconImage()` | The icon image set on the main window, if any |
| `Optional<Component> focused()` | The last component focused through the main window's `Window.focus`, if any. `OpenDialog` has the same method for the dialog's window |

- **Settling.** Before returning, every call already runs the posted updates and waits for the tasks commands start. It now also waits for the `@OnVirtualThread` deliveries, which the runner tags as such, and the pending settings saves, wherever they come from; it doesn't wait for other tasks an application starts. `@OnEdt` deliveries go through the tester's `UiExecutor`, so they run with the posted updates. No acceptance example needs `@OnEdt` or `@OnVirtualThread` subscribers, so the tests of their settling use subscribers written for them in the test sources of `swing-examples`. Those tests run on the class path, where the service loader finds their beans and subscriber indexes in `META-INF/services`; on the module path, the module's descriptor provides none of them.
- **Closing.** `close()` waits up to 2 seconds for the pending settings saves, those started by `@PreDestroy` methods included, before it closes the file system.
- **Periodic actions.** Once a test advances the tester's `FakeTicker`, each periodic action whose period has elapsed runs at the next call, once.
- **Partial results.** The values a task published reach `onPublish` before the call that waits for the task returns.
- **Dialogs.** In an answer given with `FakeDialogs.answerDialog`, which runs on the EDT, a test changes the dialog's components directly. `OpenDialog` gains `boolean isOpen()`, `false` once `ok()` or `cancel()` closed the dialog, `<B> B bean(Class<B> type)`, which resolves through the dialog's view injector, the dialog's `Form` included, `focused()`, and `void settle()`, which settles from within the answer as every tester call does: it runs the updates posted on the EDT so far, such as those of the dialog's `Ui.bind` targets, which would otherwise wait for the answer to return, the periodic actions that are due, and waits for the tasks of commands, the `@OnVirtualThread` deliveries and the pending settings saves. A dialog left open when the answer returns is cancelled, as in milestone 1.
- **Feedback.** `AppTester` keeps the `ValidationFeedback` of the modules on its path, so tests check the errors as the user sees them: through the tool tip text and accessible description.
- **Directories.** The fake config directory is `app.bean(ApplicationDirectories.class).config()`, the default directories that follow the tester's `FakePlatform`, on the memory file system, so tests read what a save wrote with `Files.readString`.

---

## Upstream changes

Milestone 2 builds on these changes. They have no fallback.

### imgn-dev/toml

Provided from toml 0.1.0, whose generated binders are named `$Record$$TomlBinder`.

| Change | Needed for | API swing uses |
|---|---|---|
| **Generated writers.** For each `@TomlConfig` record, and each record it refers to, the processor also generates the inverse of the binder, registered with it. It writes every type the binder reads, as the binder reads it: integers as `TomlInteger`, `double` and `float` as `TomlFloat`, enums in kebab case or by their `@TomlKey`, `Duration` in ISO-8601 (`Duration.toString()`), `Path` and `URI` with `toString()`, lists and sets as arrays, maps and records as tables, `TomlValue`s as they are. A `null` component, which only a `@Nullable` one can be, is left out. Binding what the writer wrote gives an equal record | Saving settings and window state | `Toml.toTable(Record value)`, returning a `TomlTable`, for a `@TomlConfig` record |
| **Binding a table.** Binding a `TomlTable` already in memory, such as one merged over defaults, as `Toml.parse(..., Class<T>)` binds a parsed one | Settings merged over their defaults | `Toml.bind(TomlTable table, Class<T> type)` |
| **Ignoring unknown keys.** A binding mode in which a key of a table that no component takes is ignored, in the root record and every record it refers to, instead of being an error. The default stays the current, strict mode | Files written by another version of the application | `Toml.bind(TomlTable table, Class<T> type, UnknownKeys unknownKeys)`, with `enum UnknownKeys { REJECT, IGNORE }` (`be.imgn.toml`) |

### imgn-dev/gaia

Provided from gaia 0.1.2, whose `gaia-core` holds the event bus (`be.imgn.gaia.core.event`) and whose `gaia-processor` generates the subscriber indexes.

| Change | Needed for | API swing uses |
|---|---|---|
| **Virtual threads.** A subscribing annotation of gaia's bus whose executor, `VirtualThreadExecutor`, runs each task on a new virtual thread and needs no shutdown | Delivering events off the publisher's thread, in core and in any domain module | `@OnVirtualThread` (`be.imgn.gaia.core.event`) |

### imgn-dev/inject

Provided from inject 0.1.1, whose `docs/features/eager-singletons.md` describes it.

| Change | Needed for | API swing uses |
|---|---|---|
| **Eager singletons.** A `@Singleton` bean, or `@Singleton @Provides` method, annotated `@Eager` is built while the root injector is created, on the thread that creates it, after the construction listeners are resolved and before the injector is returned, so that it and every dependency built for it are reported to the listeners. Beans whose condition fails, or whose keys an override shadows, are not built. `@Eager` without `@Singleton` is a compile error | Singletons that only subscribe to events, which nothing injects | `@Eager` (`be.imgn.inject`) |

### Existing behavior

Milestone 2 relies on this behavior of the libraries, as they describe it:

- gaia's subscribing annotations: `@SubscribingAnnotation(executor = ...)`, with the processor's rules on the annotation and its executor, and `EventBus.Builder.executor(Class<? extends Annotation> annotation, Executor executor)`, which replaces an annotation's executor for one bus.
- gaia's `EventBus.register` throws `IllegalArgumentException` for an instance whose class, and the classes it extends, have no subscriber index. The framework's registration relies on it.

- inject's construction hook reports every instance the container builds, singletons and unscoped beans included, not only those of child-local scopes that [milestone 1](milestone-1.md#imgn-devinject) requires. Subscribers of singletons are registered through it.
- inject's `@Provides @Default` beans are replaced by a bean of the same key in another module, as `LookAndFeelInstaller` is: `ValidationFeedback` follows the same rule.

---

## Changes to the vision

These depart from the [vision](vision.md), and are to be applied to it:

- **No topics.** The vision gives `@Subscribe` an optional topic. A topic is a second key next to the event type, a string the build can't check, which a typo silently disconnects. An event record per topic does the same, checked by the compiler: `ContactSelected` and `ContactOpened` rather than a `Contact` published on two topics.
- **The bus is gaia's.** The vision has swing's processor generate subscription registrars, and core define the bus, `@Subscribe`, `@OnEdt` and `@Background`. gaia-core provides the bus, `@Subscribe` and `@OnVirtualThread`, which replaces `@Background`, and gaia-processor the generated code; swing declares `@OnEdt` in `swing-ui`, since core has no EDT. The vision's table of libraries adds the event bus to `gaia-core`'s contents and `gaia-processor` to gaia's row.
- **Reifiable event types.** The vision's registrars have "exact generic event types". Events are matched by their runtime class, which has no type arguments, so a subscriber's parameter can't be a parameterized type, as gaia's bus requires. An event that carries a typed value wraps it in a record.
- **`@Settings`.** Settings records are `@TomlConfig` records, as in the vision, also annotated `@Settings`, which gives the file's id; each record has its own file in the config directory.
- **Roadmap.** Milestone 2 also holds OK/Cancel/Apply forms with validation, and the About dialog with the rest of the application's metadata, which milestone 1 moved there.
- **Commands and tasks.** The vision has no running state, stop commands, concurrency, partial results, chains or periodic actions; milestone 2 adds them, from the ports of milestone 1. Groups of tasks stay out of scope: `then` runs one task after another, it doesn't group them.
- **Layout.** The vision has no layout helper. `FormPanel` and `Ui.buttonBar` cover rows of labelled fields and rows of buttons only; they are not a layout manager.
- **Nested resources.** The vision's resources name the components of a view; they also reach the component fields of those components.
- **Upstream changes.** The vision lists toml's generated writers and its mode that ignores unknown keys; toml's binding of a table in memory, gaia's `@OnVirtualThread`, and inject's eager singletons are added.

---

## Acceptance

`./mvnw -B verify` succeeds on JDK 21 and on JDK 25, with the release of the parent 2026-11-01, and the releases of gaia 0.1.2 with its event bus, gaia-processor and `@OnVirtualThread`, inject 0.1.1 with eager singletons, and toml 0.1.0 with its generated writers, binding a table in memory and ignoring unknown keys. Every test of milestone 1 still passes, except those of the behavior milestone 2 changes and of the code it removes, which milestone 2's tests replace: `AddressBookAppTest.throwsItsException`, which expected `execute()` to throw, and `StorageExamplesTest`, which calls the removed `saveMap()` and `loadMap()`. `swing-examples` has the following, each with `AppTester` tests of every behavior listed.

### The Address Book, as in Guts

The Address Book port of milestone 1 is reworked to use what milestone 2 provides, which brings it closer to Guts:

- **Contacts.** `AddressBookService.contacts()` returns an `ObservableList<Contact>`. The contacts table is bound with `Ui.bind(JTable, ...)`, with the columns first name, last name and home city, its selection matched by the key `Contact::id`. The hand-written `ContactsTableModel`, and the code that found the selected contact's new row by its id, are removed.
- **Selection as events.** As in Guts, where the contacts list publishes the selected contact through a `Channel` and the detail view, the picture view and `ContactActions` consume it: the table's selection is published as a `ContactSelected(Optional<Contact> contact)` event, and the detail panel, the picture and `ContactActions` update from `@Subscribe` methods. `ContactSelection` is removed.
- **Forms.** The form, tabs and wizard dialogs bind their fields with `Form`. First and last names are required. The birth date is empty or an ISO date, through `Converter.optional`. Their OK button reads `&Save`, as in Guts, through `[commands.ok]` in their resources. When it modifies a contact, the tabs dialog adds Apply, whose `commit()` modifies the contact in the service without closing the dialog; creating a contact has no Apply. The three dialogs, `AddressPanel` and the application's detail panel lay out their fields with `FormPanel`; the detail panel shows the birth date with the date converter's `format`, and `Forms` is removed. `AddressPanel` holds its labels in `@Exposed` fields, whose texts come from nested tables of each dialog's resources, such as `[home.streetLabel]`; `AddressBookMessages` loses `street`, `zip`, `city` and `phone`. No mnemonic is used twice in a dialog: the home panel's labels have mnemonics that the dialog's other labels and buttons don't use, and the office panel's have none.
- **Tasks.** `TaskTestActions`' serial command has `concurrency = Concurrency.SERIAL`, and its `Semaphore` is removed; the two commands that started a second task from the first one's `onSuccess`, `twoSerialTaskDialogBlocker` and `twoSerialGroupsDialogBlocker`, chain them with `then`.

Its tests check, at least: modifying a contact keeps it selected; the table follows a contact created, modified and removed; `ContactSelected` is published on each selection change and the detail panel shows the contact; an empty first name shows its error and keeps the dialog open on OK; an invalid birth date does too; Apply is enabled only while a value differs from the last applied one, so editing a field and back disables it again; the address panels show the labels of their dialog's resources; two activations of the serial command, the second made with `activate` while the first runs, run one after the other; each chained command blocks the application once, for both tasks.

### The other ports of milestone 1

The ports that the [ideas](ideas.md) come from lose the code they write by hand:

- **`saf/ActionExample4`.** `go` lists the files with `concurrency = Concurrency.RESTART` and `Task.of(PublishingWork, onPublish)`, adding each batch to the list; `stop` is `@Command(cancels = "go")`. Its generation counter, its `stopEnabled` property, its buffer and its `EventQueue.invokeLater` are removed.
- **`saf/SingleFrameExample5`.** The previous, next and refresh commands choose the image and execute one `load` command, with `concurrency = Concurrency.RESTART`, whose task reads it; `stopLoading` is `@Command(cancels = "load")`. The image reader's listener aborts the read once the task is cancelled. Its generation, its count of the loads in flight and `stopLoadingEnabled` are removed.
- **`saf/PeriodicTaskExample`.** The label is updated by `ui.every(Duration.ofMillis(500), ...)`; the task's sleeping loop is removed.
- **`saf/DocumentExample`.** The text area is bound with `Form`; `modified` is `form.dirty()`, made clean with `markClean()` after a load and after a save. Its `DocumentListener` is removed.
- **`saf/LocalStorageExample1`.** Its entries are a settings record, `@Settings("entries") Entries(Map<String, String> entries)`, saved at each change and loaded at startup; its save and load commands, and their tasks, are removed.
- **Button rows.** `saf/ActionExample2`, `saf/BlockingExample1` and `saf/LocalStorageExample1` build their rows of buttons with `ui.buttonBar`. `saf/ActionExample4` keeps its own row, which also holds a text field.

Their tests check, at least, activating commands with `activate` where a task must still run: activating `go` twice shows only the second listing, and `stop` is enabled only while `go` runs; files reach the list while the listing runs, and none after `stop`; `load` shows the last image asked for, and `stopLoading` shows the cancelled message; the periodic label changes when the ticker advances by half a second; editing the document enables save, and saving disables it; the entries are loaded back by a second start on the same file system.

### Notes

A new example, `be.imgn.swing.examples.notes`, a single-frame take on the vision's [sample application](vision.md#a-sample-application), which has no document support before milestone 3:

- **`NotesApp`**, an `@Application(name = "Notes", vendor = "imgn", qualifier = "be")` single-frame class with a `JTextArea editor` bound with `Form` to the `Property<String>` of a singleton `NoteStore`. A `StartupStep` of `NoteStore` loads it from `notes.txt` in the data directory, off the EDT; it is empty when the file is missing. A file that can't be read is logged as a `WARNING` and also leaves it empty, so the application starts; the file stays as it is until the user saves.
- **Saving.** A `save` command, `enabled` while the form is dirty, `block = Block.WINDOW`, returns a `Task` that writes the text atomically to `notes.txt`, then calls `markClean()` and shows `saved(file)` from typed messages in a status label.
- **Settings.** `@Settings("editor") EditorSettings(int fontSize, boolean wordWrap)`, with the defaults 14 and `true`. The editor follows them through `ui.bind`: its font size and its line wrap.
- **Preferences.** A `PreferencesHandler` opens `PreferencesDialog`, a dialog view with a font size field bound through an integer converter and a rule of the range 6 to 72, and a word wrap check box, with OK, Cancel and Apply. `commit()` sets the settings property.
- **About.** The application's resources give `description`, `copyright`, `website` and `icon`, in English and French.
- **One module.** The examples share `swing-examples`, so Notes' beans are in every example application of the module, as the services of one module are: its `PreferencesHandler` enables `preferences()` in each, and `NoteStore`'s startup step runs in each, where it finds no `notes.txt`.

Its tests check, at least: the text is saved and loaded back; `save` is disabled until the text changes, and again after saving; the text is loaded from a `notes.txt` written with `Builder.file`; a `notes.txt` that can't be read leaves the text empty and the file as it is; settings given to the builder are applied at startup; Apply changes the font while the dialog stays open; a font size of 5 or a non-number shows the error, keeps the dialog open on OK and leaves the settings unchanged; `editor.toml` holds both values after a change; a file with an unknown key, written with `Builder.file`, loads, and one that doesn't parse is moved to `editor.toml.invalid`; the About dialog, answered as `AboutDialog`, shows the four values; the main window's icon image is set.

### Change password

A new example, `be.imgn.swing.examples.password`, shows the requirements of a new password turning from unmet to met as the user types:

- **`PasswordApp`**, an `@Application` single-frame class with a `changePassword` command and a status label. The command shows `ChangePasswordDialog`; when it returns a password, the command sets it on a singleton `Account`, which holds the password in a `Property<String>` starting at `"secret1"`, and shows `changed()` from typed messages in the status label.
- **`ChangePasswordDialog`**, a dialog view returning the new password, with three `JPasswordField`s, each labelled, bound with `Form` to properties of the dialog: the current password, the new one and its confirmation. The texts below come from `PasswordMessages`, typed messages in English and French.
  - The current password checks `Rule.required(currentRequired())` and `Rule.of(currentMatches(), s -> s.equals(account.password().get()))`.
  - The new password checks `PasswordRules.newPassword(messages)`, a factory of a `Validator<String>` of two rules: `minLength(8)`, met by at least 8 characters, and `digit()`, met by a text with a digit.
  - The confirmation checks `Rule.of(confirmationMatches(), s -> s.equals(newPassword.get()))` and revalidates on the new password's property.
- **The checklist.** Below the new password, the dialog shows a label per check of that binding's validation, in order, following `validation()`, `shown()`, `validated()` and the new password's property. While `shown()` is `false`, or the new password is empty and `validated()` is `false`, each label is the requirement after `•`, in the label's default foreground: the dialog derives that state from `binding.shown()`, `binding.validated()` and `newPassword` with `Observable.combine`. OK on the untouched dialog thus shows both requirements with `✗`, as the field shows its error, until the user types. Otherwise a `MET` check is the requirement after `✓`, in green (`#2e7d32`), an `UNMET` one after `✗`, in red (`#c62828`), and an `UNKNOWN` one after `•`, in the label's default foreground. The marks keep the status readable without the colors.

Its tests check, at least: before anything is typed, both requirements show `•`; typing `abc` shows both with `✗`; `abc1` turns the digit requirement to `✓` while the length stays `✗`; `abcdefg1` shows both with `✓`; deleting the whole text shows both with `•` again; OK on the untouched dialog shows both with `✗`; a confirmation equal to the new password is valid, and changing the new password afterwards shows the confirmation's error without the confirmation being changed; a wrong current password shows its error, and OK with any error keeps the dialog open and focuses the first field with an error, as `focused()` shows; OK with valid fields closes the dialog, sets the account's password and shows `changed()`; in French, the requirements are in French.

### Core without Swing

A test in `swing-core`, which has no `java.desktop`, builds an inject root with core's beans and the application `Scope`, as `Swing.launch` binds them, publishes events to subscribers of its eager singletons, which nothing injects, with `@Subscribe` and with `@OnVirtualThread`, and checks the deliveries, the order of the `@Subscribe` ones, that a failing subscriber reaches the `FailureHandler`, and that closing the application scope unsubscribes them. It is the counterpart of Guts' event bus demo, which runs without a UI.

---

## Order of work

Each step adds the harness methods its own tests need.

1. toml 0.1.0 with generated writers, binding a table in memory and ignoring unknown keys, gaia 0.1.2 with `@OnVirtualThread`, and inject 0.1.1 with eager singletons, from Maven Central.
2. Processor: generated names through gaia's `ClassName`.
3. Core: observable lists, rules and validations.
4. Core, UI and processor: cancellation, the task additions, the running state, stop commands and concurrency, with their checks; the harness's `activate`.
5. Core, UI and processor: the application's bus, `@OnEdt` and the renaming of the internal `EdtExecutor`, the registration of beans, and the subscribers step; the harness's `publish`, `published` and settling on deliveries.
6. Core and processor: settings, the settings step and its checks; window state on toml's generated code; the harness's `Builder.settings`, `Builder.file`, `Builder.fileSystem` and settling on saves.
7. UI: binding lists and tables, with the delivery of list changes and selection by key.
8. UI: forms, converters and validation feedback, in `swing-ui` and `swing-flatlaf`, with `Window.focus` and the harness's `focused()`.
9. UI: dialogs with forms, the other changes to milestone 1; the harness's `OpenDialog.isOpen`, `bean` and `settle`.
10. UI and processor: form panels, button rows and the resources of nested components.
11. UI: the About dialog and the application icon, with `Window.setIconImage` in the headless window and `iconImage()`.
12. The acceptance examples.
