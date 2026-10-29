# Milestone 2: requirements

This is the requirement document of state and binding, described in the [vision](vision.md#milestone-2-state-and-binding): observable lists, binding to editable components, validation, dirty tracking, the event bus and settings records. It also delivers what [milestone 1](milestone-1.md#scope) left for it: OK/Cancel/Apply forms with validation, and the About dialog with the rest of the application's metadata. The release is accepted when the [acceptance](#acceptance) examples and their tests pass.

Every part of every sentence in this document is a requirement, and must be followed exactly: names, packages, signatures, behaviors, orders and error cases. Any deviation, whether a part that can't be implemented as written, a part that contradicts another, or a gap, is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text.

Milestone 2 builds on milestone 1 as implemented. Everything milestone 1 specifies stays, except where a section below changes it; [Changes to milestone 1](#changes-to-milestone-1) lists those changes.

---

## Scope

| In milestone 2 | Later |
|---|---|
| Observable lists, delivered to the EDT change by change | Sorted and filtered views of a list |
| Binding text components, buttons and combo boxes to properties, and lists and tables to observable lists, with single selection | Multiple selection, trees, spinners, sliders, editable combo boxes |
| Validation, with feedback on the field; dirty tracking | Save prompts and undo (M3, with documents) |
| OK/Cancel/Apply dialogs on forms | Wizards (M5) |
| The event bus, on gaia-core: `@Subscribe` and `@OnVirtualThread` (gaia-core), and `@OnEdt` (UI) | — |
| Settings records, in the config directory | Watching settings files for changes made outside the application |
| The About dialog with description, copyright, website and icon; the application icon on the main frame | Opening the website from the About dialog (M3, with OS hooks) |
| Window state written and read through toml's generated code, ignoring unknown keys | — |
| The test harness for all of the above | — |

Milestone 2 adds no module. Each module gains the types of its layer: core gets observable lists, `Validator`, the event bus and settings; `swing-ui` gets `@OnEdt`, the bindings, forms, `Converter`, `Column` and `ValidationFeedback`; `swing-flatlaf` gets FlatLaf's validation feedback; `swing-processor` gets two steps; `swing-testing` gets the harness additions. Core still doesn't read `java.desktop`, which `ModuleTest.doesNotReadJavaDesktop` keeps checking.

The imgn libraries are those of milestone 1, plus `be.imgn.gaia:gaia-processor` at `gaia.version` (0.1.2-SNAPSHOT) on the annotation processor path, and the [upstream changes](#upstream-changes) that milestone 2 requires: toml's, from toml 0.2.0-SNAPSHOT, and gaia's. The event bus is in `gaia-core`, which core already depends on. The root POM's `toml.version` becomes `0.2.0-SNAPSHOT`.

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
- A modification that leaves the list equal (`List.equals`) makes no change and calls no listener: `set` with an equal element, `setAll` with equal elements, `clear` on an empty list, `remove` of an element that isn't there, `addAll` of no element.
- Indices out of range throw `IndexOutOfBoundsException`, as `List` does.

Threading:

- A list is thread-safe. Modifications are serialized by a lock that each one holds while it changes the elements and calls the listeners. Listeners therefore see the changes in the order they were made, each with indices that are valid for the list just before it.
- Listeners, of `subscribeChanges` and of `subscribe` alike, are called synchronously on the thread that modifies the list, while it holds the lock, in the order they subscribed. They must be quick: the UI layer's listeners only queue the change.
- A listener that modifies the list it listens to throws `IllegalStateException`, since its change would reach the other listeners before the one being delivered.
- `subscribe` listeners receive the new snapshot, as for any observable; `map` and `combine` work on lists as on any observable.

A dynamic menu entry ([milestone 1](milestone-1.md#dynamic-entries)) takes any `Observable<List<...>>`, so it takes an observable list of entries, or one mapped to entries.

### Delivery of list changes to the EDT

The [delivery rule](milestone-1.md#delivery-to-the-edt) of milestone 1 reads the latest value when its update runs, so several changes cause one update. A component that shows a list needs every change instead, in order, to update its rows and keep its selection. For every component bound to an observable list:

1. The UI layer keeps, on the EDT, its own copy of the list, made when the component is bound.
2. Its change listener, on the thread that modified the list, touches no component: it adds the change to a queue and, if no update is pending, posts one with `EventQueue.invokeLater`, as rule 1 does.
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
| `@OnVirtualThread` (`be.imgn.gaia.core.event`) | gaia-core ([G2](#upstream-changes)) | `VirtualThreadExecutor` (`be.imgn.gaia.core.event`), which needs no shutdown | Each on a new virtual thread |
| `@OnEdt` (`be.imgn.swing.ui`) | `swing-ui`, with `CLASS` retention | `EdtExecutor`, public, with a public constructor without parameters | With `EventQueue.invokeLater` |

- Virtual threads are a need of most applications, not of Swing ones only, so their annotation is gaia's; core declares none. `@OnEdt` is in `swing-ui`, since the EDT is a Swing concept.
- These executors are what a bus that the framework didn't build uses, such as one made with `EventBus.create()` in a domain module or a test. The framework's bus replaces both, as described below.
- The method rules are gaia's: not private, not static, not in an interface, one parameter (the event, which is not primitive, an array, a type variable or a parameterized type), returning `void`, with no checked exception. A method carries a single subscribing annotation: `@Subscribe` with `@OnEdt` or `@OnVirtualThread` is a compile error. A method that overrides a subscriber receives its events without the annotation; annotating it again is a compile error. gaia-processor checks these rules.

Build setup, for every module with subscribers, the framework's own included: the dependency `be.imgn.gaia:gaia-core`, which core already declares; `be.imgn.gaia:gaia-processor` on the annotation processor path, never on the class path; and, on the module path, gaia's `register-services` goal, which applications already add. gaia-processor generates a subscriber index per class, `$X$$Subscribers implements be.imgn.gaia.core.event.spi.SubscriberIndex`, registered under that service name in `META-INF/services`; the bus finds a class's index with `ServiceLoader`, by the index's `listenerType()`, not by its name; `register-services` adds the matching `provides be.imgn.gaia.core.event.spi.SubscriberIndex with ...` directive. Core's descriptor already `requires transitive be.imgn.gaia.core`; `swing-ui`, which exports its own subscribing annotation, `requires transitive be.imgn.gaia.core` too, and exports the package of `EdtExecutor`. A module whose subscribers use `@OnEdt` requires `be.imgn.swing.ui`, not `requires static`, since the generated code calls `new EdtExecutor()`.

### The application's bus

Core binds an `EventBus` singleton in the root injector, built with `EventBus.newBuilder()`:

- **Executors.** For each `@IntoSet(EventExecutor.class)` bean, `executor(annotation(), executor())`. `EventExecutor` (core) has `Class<? extends Annotation> annotation()` and `Executor executor()`. Core contributes `@OnVirtualThread`'s: it runs each task with `TaskService.run`, in the application scope, so that shutdown cancels and waits for running deliveries as for any task. The UI module contributes `@OnEdt`'s: it hands each task to the `UiExecutor`, so that `AppTester` runs those deliveries as it runs the other posted work. The bus therefore never creates `VirtualThreadExecutor` or `EdtExecutor`.
- **Failures.** `exceptionHandler((event, exception) -> failureHandler.failed(exception))`: an exception thrown by a subscriber, or by an executor, goes to the `FailureHandler`, and the event goes on to its other subscribers. An `Error` is not caught, as gaia specifies. The bus's own messages are logged under `be.imgn.gaia.core.event.EventBus`.
- **No bus executor.** A `@Subscribe` method runs on the thread that publishes, before `publish` returns.

Delivery follows gaia's bus:

- An event reaches the subscribers of its class and of all its supertypes, so a subscriber of a sealed interface receives every variant: the subscribers of a type before those of its supertypes, and those of one type in the order they subscribed.
- An event published from a subscriber is queued on that thread, and delivered once the event being delivered has reached all its subscribers.
- For a method with a subscribing annotation, the bus hands the executor a task per method and event, in delivery order, and goes on without waiting. The EDT runs them in that order. `@OnVirtualThread` deliveries reach their method in no particular order, and one method may run on several threads at once, so its listener must be thread-safe; the framework's executor keeps both properties.
- A task that runs after its subscription was closed doesn't call the method.
- `publish` may be called from any thread.

### Registration

- The framework registers an instance with `EventBus.register` when the construction hook reports it ([I6](milestone-1.md#imgn-devinject)) and its class, or a class it extends, has subscribers, which it asks the bus with [G1](#upstream-changes). inject's hook reports every instance the container builds, singletons included, as its 0.1.1 snapshot does: milestone 2 relies on that.
- The subscription returned is registered with `onClose` in the scope the instance is cached in: the application scope for a singleton, its window's or view's scope otherwise. Closing the scope unsubscribes it.
- The class that declares subscribers must be `@Singleton`, `@WindowScoped` or `@ViewScoped`, declared or inherited; a class without a scope is a compile error, as for commands: its subscriptions would belong to no scope. swing's processor checks it, for methods with `@Subscribe` or with any annotation meta-annotated `@SubscribingAnnotation`.
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
- The processor generates `$EditorSettings$$Settings implements SettingsDescriptor<EditorSettings>` (`$Outer$Nested$$Settings` for a nested record), with `Class<T> type()`, `String id()` and `T defaults()`, which calls the constructor with no parameter, and registers it with `ServiceFiles`. `SettingsDescriptor` is in `be.imgn.swing.support`; core declares `uses SettingsDescriptor`.

```java
public interface SettingsStore {                                       // core, singleton
    <T extends Record> Property<T> get(Class<T> type);
}
```

`get` returns the same property on every call for a type, and throws `IllegalArgumentException` for a type that has no descriptor. Called before the [loading step](#loading) has run, such as from an earlier startup step, it throws `IllegalStateException`.

### Loading

A startup step (order 150, between resources and window state) loads every settings record whose descriptor `ServiceLoader` finds, from `<id>.toml` in the `config()` directory of the [`ApplicationDirectories` bean](milestone-1.md#declaration-and-launch), which the step injects:

- A missing file gives the defaults, and no file is written.
- Otherwise, the file's table is merged over the table of the defaults, written with toml's [writer](#upstream-changes), as resources are merged: tables key by key, any other value replacing the previous one. The merged table is bound with toml's binder, ignoring unknown keys. A key the file lacks, such as a component added by a newer version of the record, therefore takes its default, and a key the record no longer has is ignored.
- A file that doesn't parse or doesn't bind gives the defaults, with a warning naming the file and the error. The file is moved to `<id>.toml.invalid`, replacing a previous one, so that the next save doesn't destroy what the user wrote.

### Saving

- Setting a settings property to a different value saves the record. Saves run on virtual threads named `settings`, one at a time per record. A save writes the property's latest value, so changes made while a save runs cause one more save, of the last value.
- A save writes every component of the record, defaults included, with toml's writer and `Toml.writeAtomically`, creating the config directory with `Files.createDirectories` when it is missing.
- A save that fails goes to the `FailureHandler`; the property keeps its value.
- Saves are not tasks: shutdown's cancellation of running tasks doesn't interrupt them. A shutdown step (order 300, after the windows are disposed, so that values set by `@PreDestroy` methods are saved) waits up to 2 seconds for the pending saves, then logs a warning naming the records it couldn't save. A value set while the root injector closes, after that step, is not saved.

Settings are observable like any property. A view follows them with `ui.bind(editor::setFont, settings.map(s -> font(s.fontSize())))`.

---

## Binding lists and tables

`Ui` (UI, `@ViewScoped`) gains methods that show an observable list in a `JList` or a `JTable`, optionally with a single selection bound to a property:

| Method | Binds |
|---|---|
| `<E> void bind(JList<E> list, ObservableList<E> items, Function<? super E, String> text)` | The list's model to `items`, and its cell renderer to `text` |
| `<E> void bind(JList<E> list, ObservableList<E> items, Function<? super E, String> text, Property<Optional<E>> selection)` | The same, with the selected element |
| `<E> void bind(JTable table, ObservableList<E> rows, List<Column<E, ?>> columns)` | The table's model to `rows`, with one column per `Column`, in order |
| `<E> void bind(JTable table, ObservableList<E> rows, List<Column<E, ?>> columns, Property<Optional<E>> selection)` | The same, with the selected row |

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
- `editable` returns a new column whose cells can be edited. When an edit is committed, on the EDT, the binding replaces the row with `rows.set(index, with.apply(row, value))`: rows are typically records, and `with` returns a copy with the new value. The change comes back through the list's listener, as any other change. If the list no longer holds the edited row at that index, because another thread changed it while the change was queued, the edit is dropped.
- Editing uses the table's default editors, so `editable` accepts only the columns whose `type` is `String`, `Boolean`, `Integer`, `Long` or `Double`, and throws `IllegalArgumentException` for any other.

### Selection

The selection binding sets the component's selection mode to `ListSelectionModel.SINGLE_SELECTION`, and keeps the selection and the property in step:

- When the user selects an element, the binding sets the property to it, once the selection is no longer adjusting; when the selection becomes empty, to `Optional.empty()`.
- When the property changes, its update, on the EDT, selects the first element of the copy equal to the value, and scrolls to it. An empty value clears the selection. A value that isn't in the list clears the selection and sets the property to `Optional.empty()`.
- After applying the queued changes of the list, the update keeps the selection on the selected element: a `Replaced` change of one element at the selected index selects the new element and sets the property to it, which keeps a contact selected after it is modified; for any other change, the selection moves to the first element equal to the property's value, or is cleared, with the property set to `Optional.empty()`, when no element is equal to it.

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
    public Binding<T> check(Validator<? super T> validator);
    public Observable<Optional<String>> error();
}
```

Every method of `Form` and `Binding` is called on the EDT, and every subscription they make belongs to the view's scope. Binding a component a second time throws `IllegalStateException`.

### Bindings

The bindings are two-way. In both directions, the component is changed only on the EDT, following the [delivery rule](milestone-1.md#delivery-to-the-edt): the property may be set from any thread.

- **Text.** The field's text is set from the property when bound. Each change of the document the field has when bound (insert, remove, change) sets the property: to the text, or, with a converter, to `converter.parse(text)`. When the property changes, its update sets the field's text, unless the text already equals the value or, with a converter, already parses to an equal value, so that typing is never interrupted and the caret stays in place.
- **Buttons.** `AbstractButton` covers check boxes, toggle buttons and radio buttons. The button's selected state is set from the property when bound; an item event sets the property; a change of the property sets the selected state.
- **Combo boxes.** The combo box shows `items`, through a model that follows the list as described in [Delivery of list changes to the EDT](#delivery-of-list-changes-to-the-edt), with a renderer showing `text` of each element. The binding makes the combo box not editable. Its selection and `selection` are kept in step as for [lists](#selection), `Optional.empty()` meaning no selected item.

Changes that a binding makes to its own component, from the property, are not user changes: they don't [show errors](#feedback).

### Conversion

```java
public interface Converter<T> {                                        // UI
    T parse(String text);
    String format(T value);
    String error();

    static <T> Converter<T> of(Function<String, ? extends T> parse, Function<? super T, String> format, String error);
    static <T> Converter<Optional<T>> optional(Converter<T> converter);
}
```

- `parse` rejects a text by throwing `IllegalArgumentException` or `DateTimeException`, which `NumberFormatException` and `DateTimeParseException` extend. The binding then leaves the property unchanged, and its error is `error()`, a message from typed messages. Any other exception is a bug in the converter, and goes to the `FailureHandler`.
- `optional` converts an empty or blank text to `Optional.empty()`, and any other text with `converter`; it formats `Optional.empty()` as an empty text. Its `error()` is `converter`'s.

### Validation

```java
@FunctionalInterface
public interface Validator<T> {                                        // core
    Optional<String> validate(T value);                               // the error message, if the value is invalid

    static Validator<String> required(String message);                // invalid when blank
}
```

- `Binding.check` adds a validator to the binding and returns the binding. A binding has an error when its converter rejects the field's text, else when one of its validators returns a message: the first one, in the order they were added, gives the error. `error()` is that message, or `Optional.empty()`.
- A binding is validated when it is bound, at each change of its component and at each change of its property.
- `Validator` is in core, so that a domain module can declare the rules of its values without Swing.
- `valid()` is `true` while no binding has an error, whether or not the errors are shown.

### Feedback

A binding's error is shown on its component once the user has changed that component, or once `validate()` has been called, and hidden as soon as the binding has no error. A shown error that changes is shown again with its new message.

`validate()` shows the error of every binding that has one, moves the focus to the first component with an error, in binding order, with `requestFocusInWindow`, and returns `valid().get()`.

Errors are shown by the `ValidationFeedback` bean (UI), on the EDT:

```java
public interface ValidationFeedback {
    void show(JComponent component, Optional<String> error);           // Optional.empty() hides the error
}
```

- `swing-ui` provides a `@Provides @Default` one. It wraps the component's border in a compound border, with a red one-pixel `LineBorder` outside it, sets the component's tool tip text and accessible description to the message, and restores the three when the error is hidden.
- `swing-flatlaf` provides one that replaces it, through inject's `@Default` rule. It sets the component's `JComponent.outline` client property to `"error"`, sets its tool tip text and accessible description to the message, and restores them when the error is hidden.
- For a component in the viewport of a `JScrollPane`, such as a text area, the feedback is shown on the scroll pane, which is what draws the component's border.

### Dirty tracking

- Each binding and each `track`ed observable has a baseline: its property's value, or the observable's, when it is bound or tracked.
- `dirty()` is `true` while the value of at least one of them differs from its baseline (`Objects.equals`). Editing a field back to its original value makes the form clean again. A text a converter rejects leaves the property unchanged, so it doesn't make the form dirty.
- `track` adds an observable that has no component in the form, such as an observable list edited in a table.
- `markClean()` makes every current value the new baseline.

A command declares its enabled state from the form like from any observable: `@Command(enabled = "canSave")` on a field `@Exposed final Observable<Boolean> canSave = form.dirty();`, exposed as [milestone 1](milestone-1.md#exposed-fields) requires of the fields that generated code reads. Dirty state asks nothing at exit in milestone 2: save prompts come with documents, in milestone 3.

---

## Dialogs with forms

### OK, Cancel and Apply

The standard commands gain `apply()`, in dialog views only, like `ok()` and `cancel()`: outside a dialog view's scope, calling it throws `IllegalStateException`. Its text and shortcut come from the framework's resources, in English (`&Apply`) and French (`&Appliquer`). `apply` joins the standard command ids a class may override ([milestone 1](milestone-1.md#overriding-standard-commands)).

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

A `[commands.<id>]` table for a standard command, in the resources of a view class or of one of its superclasses, without a handler of that id, overrides that standard command's resources in that view, key by key: the build already checks such tables ([milestone 1](milestone-1.md#build-time-checks)), and the view's `CommandRegistry` now builds the standard command from the framework's resources merged with the table. A dialog renames its OK button so, and keeps the standard handler that closes it:

```toml
# ContactDialog.toml
[commands.ok]
text = "&Save"
```

---

## About dialog and application icon

The application class's resources take four more plain values, which the processor checks as it checks `name` and `theme`:

| Key | Type | Used for |
|---|---|---|
| `description` | String | A sentence about the application, in the About dialog |
| `copyright` | String | The copyright notice, in the About dialog |
| `website` | String, an absolute `http` or `https` URI | The application's website, in the About dialog |
| `icon` | String, an icon path as for commands | The application's icon, in the About dialog and on the main frame |

Being resources, `description` and `copyright` are translated like any text, and every value may have platform variants. A `website` that doesn't parse as an absolute `http` or `https` URI, or an `icon` that names no resource, is a compile error; the icon's loader is checked at startup with the other icons ([milestone 1](milestone-1.md#icons)).

The About dialog shows, from top to bottom, each one that is present: the icon, the displayed name, `version`, `vendor`, `description`, `copyright` and `website`. The website is a read-only, selectable `JTextField` without border, so the user can copy it; opening it in a browser comes with the OS hooks of milestone 3.

`Window` gains `void setIconImage(Image image)`. Step 5 of the [single-frame startup](milestone-1.md#singleframe), which sets the title, also sets the main frame's icon image from `icon`: for an `ImageIcon`, its image; for any other icon, the icon painted into an ARGB `BufferedImage` of its size. `AppTester`'s headless `Window` records the image.

---

## Window state

Window state keeps the file of [milestone 1](milestone-1.md#window-state), keys and layout included, but is written and read through toml's generated code: its records are `@TomlConfig` records, written with toml's writer, and bound ignoring unknown keys. The hand-written conversion is removed.

- A state file with keys that the current version doesn't know, such as one written by a newer version, is no longer discarded: the unknown keys are ignored, and the rest is restored.
- A file that still doesn't bind, such as one with a value of the wrong type, is discarded with a warning, as in milestone 1.

---

## Changes to milestone 1

These change what milestone 1 specifies, from milestone 2 on:

- **Failures of handlers.** An exception thrown by a command handler or by a dynamic item's action goes to the `FailureHandler`, as a task's failure does. In milestone 1 it went nowhere the user could see. `BoundCommand.execute` returns `true` all the same, since the command ran.
- **Preferences.** `PreferencesHandler.showPreferences()` becomes `void showPreferences(Ui ui)`. A `PreferencesHandler` is a singleton, which can't reach a view's `Ui` in milestone 1, and so can't open a dialog view. The `ui` is the `Ui` of the view whose `preferences()` command runs; on macOS's application menu, the main view's. The handler uses it during the call and must not keep it.
- **Standard command resources** are per view, as described in [Renaming standard commands in a view](#renaming-standard-commands-in-a-view).
- **`apply()`** joins the standard commands, and `commit()` joins `DialogView` (see [OK, Cancel and Apply](#ok-cancel-and-apply)).
- **`Window.setIconImage`** joins `Window` (see [About dialog and application icon](#about-dialog-and-application-icon)).
- **Window state** is written and read through toml's generated code (see [Window state](#window-state)).
- **Generated names.** The names of generated types, `$Outer$Inner$$Kind` for a type and `$$Kind` for a package, come from gaia's `ClassName.generatedName` rather than from the processor's own helper (see [Processor](#processor)). The names of milestone 1 don't change, except that a `$` within a simple name is now written `$0`, as gaia's scheme requires so that no two types get one name.

---

## Build-time checks

Milestone 2 adds these checks to [milestone 1's](milestone-1.md#build-time-checks), with diagnostics attached to the Java element as there:

| Check | Error when |
|---|---|
| Subscribers | A class with a method annotated `@Subscribe`, or with an annotation meta-annotated `@SubscribingAnnotation`, has no scope among `@Singleton`, `@WindowScoped` and `@ViewScoped`, declared or inherited. The rules of the methods themselves are gaia-processor's |
| Settings | A `@Settings` type is not a record, is not annotated `@TomlConfig`, has no public constructor without parameter, or has an invalid id; or two settings records of the compilation have the same id |
| Class properties | A `description`, `copyright`, `website` or `icon` value is not a string; a `website` is not an absolute `http` or `https` URI; an `icon` names no resource. These values are allowed on the application class only |

The commands' check of `[commands.<id>]` tables also accepts `apply`, as a standard command.

---

## Processor

`swing-processor` gains two steps:

| Step | Triggered by | Generates |
|---|---|---|
| Subscribers | Every root element, as the views step | Nothing: it checks the scope of classes with subscribers |
| Settings | `@Settings` | `$X$$Settings`, service entry |

The subscribers' code is generated by gaia-processor, which runs next to swing's, inject's and toml's processors. The settings descriptors are services that core finds with `ServiceLoader`, not beans. On the module path, gaia's `register-services` adds the `provides` clauses of both, `SubscriberIndex` and `SettingsDescriptor`, as for the other services.

Every type swing's processor generates is named through gaia's `ClassName`, from gaia-processing 0.1.2: `ClassName.of(type).generatedName(kind)` for a type (`$Outer$Inner$$Settings`), `ClassName.generatedName(packageName, kind)` for a package (`$$Resources`). The processor's own naming helper of milestone 1 is removed (see [Changes to milestone 1](#changes-to-milestone-1)).

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
| `Builder.settings(Record value)` | Joins milestone 1's [`AppTester.builder(type)`](milestone-1.md#test-harness), next to `locale` and `arguments`: writes a settings record to the fake config directory before startup, with the store's own writer, a later value of the same record replacing an earlier one |
| `<B> B bean(Class<B> type)` | Resolves a bean through the main view's injector, so beans of every scope of the main window are reachable |
| `void run(Runnable action)`, `<R> R call(Supplier<R> action)` | Runs the action on the EDT and waits, then settles as every other call does. Tests change components this way, since the EDT checker fails a test that changes them on its own thread |
| `void publish(Object event)` | Publishes the event on the application's bus, on the EDT, then settles |
| `<E> List<E> published(Class<E> type)` | The events published on the application's bus since startup that are instances of `type`, in order. The tester records them with a subscription to `Object` |

- **Settling.** Before returning, every call already runs the posted updates and waits for the tasks commands start. It now also waits for the tasks of the application scope, which `@OnVirtualThread` deliveries are, and the pending settings saves, wherever they come from. `@OnEdt` deliveries go through the tester's `UiExecutor`, so they run with the posted updates.
- **Dialogs.** In an answer given with `FakeDialogs.answerDialog`, which runs on the EDT, a test changes the dialog's components directly. `OpenDialog` gains `boolean isOpen()`, `false` once `ok()` or `cancel()` closed the dialog, and `<B> B bean(Class<B> type)`, which resolves through the dialog's view injector, the dialog's `Form` included. A dialog left open when the answer returns is cancelled, as in milestone 1.
- **Feedback.** `AppTester` keeps the `ValidationFeedback` of the modules on its path, so tests check the errors as the user sees them: through the tool tip text and accessible description.
- **Directories.** The fake config directory is `app.bean(ApplicationDirectories.class).config()`, the default directories that follow the tester's `FakePlatform`, on the memory file system, so tests read what a save wrote with `Files.readString`.

---

## Upstream changes

Milestone 2 builds on these changes. They have no fallback.

### imgn-dev/toml

Provided from toml 0.2.0-SNAPSHOT.

| Id | Change | Needed for | API swing uses |
|---|---|---|---|
| T1 | **Generated writers.** For each `@TomlConfig` record, and each record it refers to, the processor also generates the inverse of the binder, registered with it. It writes every type the binder reads, as the binder reads it: integers as `TomlInteger`, `double` and `float` as `TomlFloat`, enums in kebab case or by their `@TomlKey`, `Duration` in ISO-8601 (`Duration.toString()`), `Path` and `URI` with `toString()`, lists and sets as arrays, maps and records as tables, `TomlValue`s as they are. A `null` component, which only a `@Nullable` one can be, is left out. Binding what the writer wrote gives an equal record | Saving settings and window state | `Toml.toTable(T value, Class<T> type)`, returning a `TomlTable` |
| T2 | **Binding a table.** Binding a `TomlTable` already in memory, such as one merged over defaults, as `Toml.parse(..., Class<T>)` binds a parsed one | Settings merged over their defaults | `Toml.bind(TomlTable table, Class<T> type)` |
| T3 | **Ignoring unknown keys.** A binding mode in which a key of a table that no component takes is ignored, in the root record and every record it refers to, instead of being an error. The default stays the current, strict mode | Files written by another version of the application | `Toml.bind(TomlTable table, Class<T> type, UnknownKeys unknownKeys)`, with `enum UnknownKeys { REJECT, IGNORE }` |

### imgn-dev/gaia

Provided from gaia 0.1.2-SNAPSHOT, whose `gaia-core` holds the event bus (`be.imgn.gaia.core.event`) and whose `gaia-processor` generates the subscriber indexes.

| Id | Change | Needed for | API swing uses |
|---|---|---|---|
| G1 | **Asking whether a class has subscribers.** Whether `EventBus.register` accepts an instance of a class: whether the class or a class it extends has an index generated by gaia-processor, found as `register` finds it, and cached the same way. Without it, the framework could only call `register` on every instance the construction hook reports and catch its `IllegalArgumentException` | Registering the subscribers of beans | `static boolean EventBus.hasSubscribers(Class<?> type)` |
| G2 | **Virtual threads.** A subscribing annotation of gaia's bus whose executor, `VirtualThreadExecutor`, runs each task on a new virtual thread and needs no shutdown | Delivering events off the publisher's thread, in core and in any domain module | `@OnVirtualThread` (`be.imgn.gaia.core.event`) |

### Existing behavior

Milestone 2 relies on this behavior of the libraries, as they describe it:

- gaia's subscribing annotations: `@SubscribingAnnotation(executor = ...)`, with the processor's rules on the annotation and its executor, and `EventBus.Builder.executor(Class<? extends Annotation> annotation, Executor executor)`, which replaces an annotation's executor for one bus.

- inject's construction hook reports every instance the container builds, singletons and unscoped beans included, not only those of child-local scopes that [I6](milestone-1.md#imgn-devinject) requires. Subscribers of singletons are registered through it.
- inject's `@Provides @Default` beans are replaced by a bean of the same key in another module, as `LookAndFeelInstaller` is: `ValidationFeedback` follows the same rule.

---

## Changes to the vision

These depart from the [vision](vision.md), and are to be applied to it:

- **No topics.** The vision gives `@Subscribe` an optional topic. A topic is a second key next to the event type, a string the build can't check, which a typo silently disconnects. An event record per topic does the same, checked by the compiler: `ContactSelected` and `ContactOpened` rather than a `Contact` published on two topics.
- **The bus is gaia's.** The vision has swing's processor generate subscription registrars, and core define the bus, `@Subscribe`, `@OnEdt` and `@Background`. gaia-core provides the bus, `@Subscribe` and `@OnVirtualThread`, which replaces `@Background`, and gaia-processor the generated code; swing declares `@OnEdt` in `swing-ui`, since core has no EDT. The vision's table of libraries adds the event bus to `gaia-core`'s contents and `gaia-processor` to gaia's row.
- **Reifiable event types.** The vision's registrars have "exact generic event types". Events are matched by their runtime class, which has no type arguments, so a subscriber's parameter can't be a parameterized type, as gaia's bus requires. An event that carries a typed value wraps it in a record.
- **`@Settings`.** Settings records are `@TomlConfig` records, as in the vision, also annotated `@Settings`, which gives the file's id; each record has its own file in the config directory.
- **Roadmap.** Milestone 2 also holds OK/Cancel/Apply forms with validation, and the About dialog with the rest of the application's metadata, which milestone 1 moved there.
- **Upstream changes.** The toml writer and the lenient binding mode, listed in the vision, are T1 and T3; binding a table in memory (T2) and gaia's G1 and G2 are added.

---

## Acceptance

`./mvnw -B verify` succeeds on JDK 21 and on JDK 25, with the snapshots of milestone 1, gaia 0.1.2-SNAPSHOT with its event bus, gaia-processor, G1 and G2, and toml 0.2.0-SNAPSHOT installed in the local Maven repository. Every test of milestone 1 still passes, and `swing-examples` has the following, each with `AppTester` tests of every behavior listed.

### The Address Book, as in Guts

The Address Book port of milestone 1 is reworked to use what milestone 2 provides, which brings it closer to Guts:

- **Contacts.** `AddressBookService.contacts()` returns an `ObservableList<Contact>`. The contacts table is bound with `Ui.bind(JTable, ...)`, with the columns first name, last name and home city, and the hand-written `ContactsTableModel` is removed.
- **Selection as events.** As in Guts, where the contacts list publishes the selected contact through a `Channel` and the detail view, the picture view and `ContactActions` consume it: the table's selection is published as a `ContactSelected(Optional<Contact> contact)` event, and the detail panel, the picture and `ContactActions` update from `@Subscribe` methods. `ContactSelection` is removed.
- **Forms.** The form, tabs and wizard dialogs bind their fields with `Form`. First and last names are required. The birth date is empty or an ISO date, through `Converter.optional`. Their OK button reads `&Save`, as in Guts, through `[commands.ok]` in their resources. The tabs dialog adds Apply, whose `commit()` modifies the contact in the service without closing the dialog.

Its tests check, at least: modifying a contact keeps it selected; the table follows a contact created, modified and removed; `ContactSelected` is published on each selection change and the detail panel shows the contact; an empty first name shows its error and keeps the dialog open on OK; an invalid birth date does too; Apply is enabled only while a value differs from the last applied one, so editing a field and back disables it again.

### Notes

A new example, `be.imgn.swing.examples.notes`, a single-frame take on the vision's [sample application](vision.md#a-sample-application), which has no document support before milestone 3:

- **`NotesApp`**, an `@Application(name = "Notes", vendor = "imgn", qualifier = "be")` single-frame class with a `JTextArea editor` bound with `Form` to the `Property<String>` of a singleton `NoteStore`. A `StartupStep` of `NoteStore` loads it from `notes.txt` in the data directory, off the EDT; it is empty when the file is missing.
- **Saving.** A `save` command, `enabled` while the form is dirty, `block = Block.WINDOW`, returns a `Task` that writes the text atomically to `notes.txt`, then calls `markClean()` and shows `saved(file)` from typed messages in a status label.
- **Settings.** `@Settings("editor") EditorSettings(int fontSize, boolean wordWrap)`, with the defaults 14 and `true`. The editor follows them through `ui.bind`: its font size and its line wrap.
- **Preferences.** A `PreferencesHandler` opens `PreferencesDialog`, a dialog view with a font size field bound through an integer converter and a validator of the range 6 to 72, and a word wrap check box, with OK, Cancel and Apply. `commit()` sets the settings property.
- **About.** The application's resources give `description`, `copyright`, `website` and `icon`, in English and French.

Its tests check, at least: the text is saved and loaded back; `save` is disabled until the text changes, and again after saving; settings given to the builder are applied at startup; Apply changes the font while the dialog stays open; a font size of 5 or a non-number shows the error, keeps the dialog open on OK and leaves the settings unchanged; `editor.toml` holds both values after a change; a file with an unknown key loads, and one that doesn't parse is moved to `editor.toml.invalid`; the About dialog shows the four values.

### Core without Swing

A test in `swing-core`, which has no `java.desktop`, builds an inject root with core's beans and the application `Scope`, as `Swing.launch` binds them, publishes events to subscribers of its singletons, with `@Subscribe` and with `@OnVirtualThread`, and checks the deliveries, the order of the `@Subscribe` ones, that a failing subscriber reaches the `FailureHandler`, and that closing the application scope unsubscribes them. It is the counterpart of Guts' event bus demo, which runs without a UI.

---

## Order of work

1. toml 0.2.0-SNAPSHOT with T1 to T3, and gaia 0.1.2-SNAPSHOT with G1 and G2; until they are released, local snapshots.
2. Core: observable lists and `Validator`.
3. Core, UI and processor: the application's bus, `@OnEdt`, the registration of beans, and the subscribers step.
4. Core and processor: settings, the settings step and its checks; window state on toml's generated code.
5. UI: binding lists and tables, with the delivery of list changes.
6. UI: forms, converters and validation feedback, in `swing-ui` and `swing-flatlaf`.
7. UI: dialogs with forms, renaming standard commands, the changes to milestone 1.
8. UI: the About dialog and the application icon.
9. Test harness, then the acceptance examples.
