# Ideas

Code that the ports in `swing-examples` still write by hand, as of `7571bf4` on `milestone-1`, and that the framework could take over. Each idea names the examples it comes from and the milestone of the [vision's roadmap](vision.md#roadmap) it belongs to: the one that already plans it, or, for an idea the roadmap doesn't have yet, a suggested one, marked **new**.

Paths are relative to `swing-examples/src/main/java/be/imgn/swing/examples`.

## Summary

| Idea | Examples | Roadmap |
|---|---|---|
| [Running, cancelling and restarting a command's task](#1-running-cancelling-and-restarting-a-commands-task) | `saf/SingleFrameExample5`, `saf/ActionExample4` | Milestone 2, **new** |
| [Partial results from a task](#2-partial-results-from-a-task) | `saf/ActionExample4` | Milestone 2, **new** |
| [Chained, serial and periodic tasks](#3-chained-serial-and-periodic-tasks) | `addressbook/TaskTestActions`, `saf/PeriodicTaskExample` | Milestone 2, **new** |
| [File choosers](#4-file-choosers) | `saf/SingleFrameExample4`, `saf/DocumentExample` | Milestone 3, **new** |
| [Resources of reusable panels](#5-resources-of-reusable-panels) | `addressbook/AddressPanel` | Milestone 2, **new** |
| [The application's icon](#6-the-applications-icon) | `addressbook/AddressBookApp` | Milestone 3, **new** |
| [Default application name](#7-default-application-name) | every application | Milestone 3, **new** |
| [Table and list binding](#8-table-and-list-binding) | `addressbook/AddressBookApp`, `addressbook/ContactsTableModel`, `saf/SessionStorageExample1` | Milestone 2 |
| [Form binding, validation and layout](#9-form-binding-validation-and-layout) | the three contact dialogs, `addressbook/Forms`, `saf/ActionMapExample` | Milestone 2; layout **new** |
| [Text changes and documents](#10-text-changes-and-documents) | `saf/DocumentExample` | Milestones 2 and 3 |
| [Wizard](#11-wizard) | `addressbook/ContactWizardDialog` | Milestone 5 |
| [Local storage](#12-local-storage) | `saf/LocalStorageExample1` | Milestone 2 |
| [Toolbars and button rows](#13-toolbars-and-button-rows) | several | Milestone 2 and 4, **new** |
| [Tab titles](#14-tab-titles) | `saf/SessionStorageExample1`, `saf/Tabbed`, `addressbook/ContactTabsDialog` | Milestone 4, **new** |
| [Cleanups of the examples](#15-cleanups-of-the-examples) | `saf/DocumentExample`, the contact dialogs | Done |

## Not on the roadmap

### 1. Running, cancelling and restarting a command's task

`saf/SingleFrameExample5` and `saf/ActionExample4` build the same machinery by hand:

- A generation counter, so that a newer run, or the stop command, makes the older run ignore its results.
- A `stopEnabled` property, and in `SingleFrameExample5` a count of the loads in flight.
- The same `done()` or `loaded()` call in `onSuccess`, `onFailure` and `onCancel`.

Their stop commands don't cancel the task: no handle reaches it, so the work checks the generation itself, as `SingleFrameExample5`'s image reader listener does with `current.get() != generation`.

Proposal:

- `BoundCommand.running()`, an `Observable<Boolean>` that is true while a task the command started runs, and `BoundCommand.cancel()`, which cancels them.
- A way to enable a command only while another runs, such as `@Command(cancels = "go")`, which declares a stop command enabled while `go` runs and cancelling it when activated.
- A restart policy, such as `@Command(restart = true)`: a new run cancels the one still running, so its results never arrive.
- `Task.onEnd(Runnable)`, called after any of success, failure and cancellation.

With these, both examples lose their generation, their enabled properties and their counters.

**Roadmap:** milestone 2. `running()` is an observable, and the milestone is about state; the tasks of milestone 1 are otherwise complete. **Integrated** into [milestone 2](milestone-2.md#tasks-and-commands), with `restart` and the serial option as one `concurrency` attribute.

### 2. Partial results from a task

`saf/ActionExample4` lists files in a task and shows them as it finds them. Its `publish` rebuilds SwingWorker's `publish` and `process`: it buffers ten files, hands a copy to `EventQueue.invokeLater`, and checks the generation there so that a stale listing adds nothing. `TaskContext` has `message` and `progress`, but nothing for results.

Proposal: `TaskContext.publish(T)` and `Task.onPublish(Consumer<List<T>>)`, delivered on the EDT in batches, and dropped once the task is cancelled. Delivering into an observable list would follow.

**Roadmap:** milestone 2, beside observable lists. **Integrated** into [milestone 2](milestone-2.md#partial-results).

### 3. Chained, serial and periodic tasks

- `addressbook/TaskTestActions` chains two tasks with `onSuccess(result -> ui.run(second, Block.APPLICATION))`, and runs its serial task under a `Semaphore`, which stands for Guts' serial executor.
- `saf/PeriodicTaskExample` runs a `while (!context.isCancelled())` loop with `Thread.sleep` to update a label every half second.

Proposal: `Task.then(Function<T, Task<R>>)`, which blocks once for the whole chain; a serial option that queues a command's runs instead of running them together; and `ui.every(Duration, Runnable)`, a periodic action owned by the view's scope.

**Roadmap:** milestone 2, with the other task additions. The periodic action could wait for milestone 3, as a desktop concern, if milestone 2 grows too large. **Integrated** into [milestone 2](milestone-2.md#hooks-and-chains), the periodic action included, and the serial option as `Concurrency.SERIAL`.

### 4. File choosers

`saf/SingleFrameExample4` and `saf/DocumentExample` create a `JFileChooser`, find its owner with `SwingUtilities.getWindowAncestor(ui.window().rootPane())`, and compare the result to `APPROVE_OPTION`.

Proposal: `Dialogs.openFile(...)` and `Dialogs.saveFile(...)`, with filters whose descriptions are messages, returning `Optional<Path>` and remembering the last directory in the window state. Two gains besides the lines saved:

- Tests answer them as they answer `confirm`. Today `DocumentExample.file(Path)` exists because tests "choose no file", and its open command is tested through `load` instead.
- `Window` no longer needs to expose its `JRootPane` for code to find the owner of a dialog.

**Roadmap:** milestone 3, with documents, which open and save files.

### 5. Resources of reusable panels

`addressbook/AddressPanel` takes `AddressBookMessages` to set its labels, because resources apply only to the fields of a view: the dialogs that hold it set its title, but not the texts inside it. Any composite component reused across views meets the same limit.

Proposal: inject resources into the `@Exposed` fields of a component that is itself an `@Exposed` field of a view, under nested keys such as `home.street`, with the processor checking them like the view's own.

**Roadmap:** milestone 2. Forms are where reusable panels appear, and the processor's checks extend naturally. **Integrated** into [milestone 2](milestone-2.md#resources-of-components-in-components).

### 6. The application's icon

`addressbook/AddressBookApp.configureFrame` loads `icons/main.png` with `getResource` and `ImageIcon` to set the frame's icon.

Proposal: an `icon` key in the application's resources, loaded through `IconLoader` for every window of the application, and checked by the processor.

**Roadmap:** milestone 3, with the `Taskbar` and the other OS hooks, which show the same icon.

### 7. Default application name

Most applications repeat their class name in `@Application(name = ...)`, beside a one-line `main` and, often, an empty `@Inject` constructor.

Proposal: `name` defaults to the class's simple name. The empty constructors come from inject's rules, so removing them is an upstream change to discuss there.

**Roadmap:** milestone 3, when `@Application` becomes required.

## On the roadmap

What the examples show about the shape of features already planned.

### 8. Table and list binding

- `addressbook/AddressBookApp` listens to the table's selection to set a `Property<Optional<Contact>>`, and after each change of the contacts finds the selected contact's new row by its id to select it again.
- `addressbook/ContactsTableModel` writes the model, its headers from messages, and `rowOf(id)`.
- `saf/SessionStorageExample1` sets its columns' headers from messages by index.

What the binding needs: an `Observable<List<T>>` as the rows; columns given as a header message and a function of the row; a key function so that the selection survives changes of the list; and the selection as a `Property<Optional<T>>`, or a list of them.

**Roadmap:** milestone 2, "observable lists, binding to editable components". **Integrated:** [milestone 2](milestone-2.md#binding-lists-and-tables) already had the binding; the key function is added.

### 9. Form binding, validation and layout

- The three contact dialogs repeat the same fields, the same `setText` calls from the contact, and the same `result()` that reads them back.
- `addressbook/Forms.date` turns a date that doesn't parse into no date, without telling the user.
- `addressbook/Forms` lays out rows of a label and its field with `GridBagLayout`, setting `setLabelFor`; `saf/ActionMapExample` writes its own `GridBagConstraints` for three panels.

What the binding needs: two-way binding of a field to a property, converters such as one for `LocalDate`, and validation that shows on the field and disables OK.

The roadmap doesn't plan a layout helper. A small form builder (a row of a label and a field, a full-width row, a section with a title) would sit well beside the binding, in milestone 2, **new**.

**Integrated:** [milestone 2](milestone-2.md#forms) already had the binding, validation and converters; the builder is [`FormPanel`](milestone-2.md#form-panels).

### 10. Text changes and documents

`saf/DocumentExample` implements `DocumentListener`, all three methods setting `modified`; binds the window's title to the file and the application's name by hand; and its exit veto asks only while modified.

- An observable of a text component's changes belongs to milestone 2, "binding to editable components", and removes the listener.
- Dirty tracking, save prompts and the title that follows the document are milestone 3, "documents".

**Integrated:** [milestone 2](milestone-2.md#the-other-ports-of-milestone-1) binds the text area with `Form`, whose dirty state replaces the listener; the rest stays in milestone 3.

### 11. Wizard

`addressbook/ContactWizardDialog` holds the step in a property, shows it with a `CardLayout`, derives `hasPrevious` and `hasNext`, and writes the step's heading.

**Roadmap:** milestone 5, `swing-wizard`. This dialog is its first test case.

### 12. Local storage

`saf/LocalStorageExample1` writes and reads a TOML table in the data directory: `createDirectories`, `Toml.writeAtomically`, and a loop over the entries that keeps the strings.

**Roadmap:** milestone 2, "settings records": a service that saves and loads a record, in a task, would replace it. **Integrated:** [milestone 2](milestone-2.md#the-other-ports-of-milestone-1) makes its entries a settings record.

## Smaller polish

### 13. Toolbars and button rows

- Every toolbar calls `setFloatable(false)`: `saf/SingleFrameExample4`, `saf/SingleFrameExample5`, `saf/BlockingExample1`. Not floating could be the default of `Ui.toolBar`. **Roadmap:** milestone 4, **new**, or at once, as it is a one-line change.
- `saf/ActionExample2`, `saf/ActionExample4`, `saf/BlockingExample1` and `saf/LocalStorageExample1` add `commands.x().createButton()` to a `FlowLayout` panel, button by button. `Ui.buttonBar(BoundCommand...)` would build the row. **Roadmap:** milestone 2, **new**, with the form builder. **Integrated** into [milestone 2](milestone-2.md#button-rows).
- `saf/SingleFrameExample5`, `saf/BlockingExample1` and `saf/DocumentExample` list the same commands in a menu and a toolbar. Whether a toolbar should derive from a menu is open; the examples alone don't settle it.

### 14. Tab titles

`saf/SessionStorageExample1`, `saf/Tabbed` and `addressbook/ContactTabsDialog` call `addTab("", component)`, and the component's field gives the title through its `tab-title` resource. Applying the title when a tab is added, or a `Ui` method that adds a tab without a title, would remove the empty string.

**Roadmap:** milestone 4, **new**.

### 15. Cleanups of the examples

Done in `87bd3c6`, with no framework change:

- `saf/DocumentExample.open()` returned `Task.of(() -> textArea.getText())` when no file is chosen; it returns `Task.none()`.
- The three contact dialogs share one `ContactForm` for their fields and their result, until form binding makes it shorter still.
