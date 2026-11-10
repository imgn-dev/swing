# Building applications with swing: a guide for coding agents

This document is written for LLM coding agents (and humans in a hurry) that build desktop applications **with** the swing framework. It describes everything an application can use, the rules the build enforces, and the idioms that work. It is self-contained: an agent that follows it should produce code that compiles, passes the framework's checks and behaves as intended on the first try.

It covers what is implemented: milestones 1 to 4. `swing-wizard` and `swing-docking` ([milestone 5](milestone-5.md)) are specified but **not available yet**: don't depend on them.

How to use it:

- Read [Rules that break the build](#1-rules-that-break-the-build) and [Mental model](#3-mental-model) before writing code.
- Start a new application from the [complete example](#20-complete-example), which compiles and passes its tests as written.
- Look up APIs in the sections that follow. The examples in `swing-examples/src/main/java/be/imgn/swing/examples` (`counter`, `notes`, `addressbook`, `password`, `memo`, `saf`), their tests, and the test-only applications in `swing-examples/src/test/java/be/imgn/swing/examples` (`frames`, `refresh`) are the reference for anything this document doesn't show.
- When the build fails, look the message up in [Build errors](#19-build-errors-and-their-fixes).

---

## Contents

1. [Rules that break the build](#1-rules-that-break-the-build)
2. [Project setup](#2-project-setup)
3. [Mental model](#3-mental-model)
4. [The application class and launching](#4-the-application-class-and-launching)
5. [Views, frames and dialogs](#5-views-frames-and-dialogs)
6. [Resources in TOML](#6-resources-in-toml)
7. [Messages and locales](#7-messages-and-locales)
8. [Commands](#8-commands)
9. [Menus, toolbars and popups](#9-menus-toolbars-and-popups)
10. [Observable state and binding](#10-observable-state-and-binding)
11. [Lists and tables](#11-lists-and-tables)
12. [Forms and validation](#12-forms-and-validation)
13. [Tasks and concurrency](#13-tasks-and-concurrency)
14. [Events](#14-events)
15. [Dependency injection, scopes and lifecycle](#15-dependency-injection-scopes-and-lifecycle)
16. [Settings, files and window state](#16-settings-files-and-window-state)
17. [Desktop integration and FlatLaf](#17-desktop-integration-and-flatlaf)
18. [Testing](#18-testing)
19. [Build errors and their fixes](#19-build-errors-and-their-fixes)
20. [Complete example](#20-complete-example)

---

## 1. Rules that break the build

These are the mistakes agents make most often. Each one is a compile error, a failing check or a runtime exception.

1. **No reflection, ever.** The framework generates code at compile time; it never scans classes. Never use `Class.forName`, reflection or runtime classpath scanning in an application either.
2. **Fields that generated code reads are package-private and annotated `@Exposed`** (`be.imgn.swing.ui.Exposed`): every component named in TOML, every component whose window state is saved, every component passed to `Ui.addTab`, and every `Observable<Boolean>` or `Property<Boolean>` field named by `@Command(enabled = …)` or `@Command(selected = …)`. Not `private`, not `static`. A field annotated `@Command` (a toggle) is package-private too, but needs no `@Exposed`. All other fields stay `private`.
3. **Create components in field initializers or the constructor, never in `content()`.** Component resources are applied after the constructor and before `content()`.
4. **Every component a user can focus needs an accessible name**: a text component (field or area), combo box, list, table, tree, spinner, slider or button. Give it a label with `labelFor` (`FormPanel.row` does this), an `accessible-name` resource, or button text. A `JLabel` with a mnemonic needs a `labelFor`. Tests fail otherwise.
5. **Every command you declare needs `[commands.<id>] text = "…"` in the base TOML file** of its class.
6. **Every language file (`X_fr.toml`) repeats every key of the base file (`X.toml`)**, non-text keys included: `shortcut`, `icon`, `font`, colors. A locale file or a dark file may not add a key the base file lacks.
7. **Classes with `@Command` or event subscribers must have a scope**: `@Singleton`, `@WindowScoped` or `@ViewScoped`, declared or inherited. Views are `@ViewScoped` through `View`. A `@Singleton` with subscribers must also be `@Eager`.
8. **A wider scope never injects a narrower one directly**: a `@Singleton` can't inject `Ui`, `<X>Commands`, `Form` or any `@WindowScoped`/`@ViewScoped` bean. Inject `Provider<X>` instead.
9. **All Swing work happens on the EDT.** Command handlers, view methods, `Ui` and `Dialogs` already run there. Work off the EDT goes in a `Task`, whose hooks come back to the EDT. Tests touch components only inside `app.run(...)`, `app.call(...)` or a `FakeDialogs.answerDialog` action, which all run on the EDT.
10. **Texts that code builds come from `@Messages` interfaces**, never from string literals, and are rendered with `ui.render(...)` so they follow a locale change.
11. **Message placeholders name parameters**: `{count}`, never `{0}`. An apostrophe is written `''`.
12. **The generated `<X>Commands` method is named after the handler method**, not the command id: `@Command(id = "save") void doSave()` gives `commands.doSave()` and the table `[commands.save]`.
13. **`ok()`, `cancel()` and `apply()` work only in dialog views**: elsewhere they throw `IllegalStateException`. There is no input dialog: to ask for a value, write a `DialogView<T>`.
14. **`Property` never holds `null`.** Use `Property<Optional<T>>` for an absent value.
15. **The build runs with `-Werror`**, Error Prone and NullAway: every warning fails it, including Error Prone's Javadoc checks such as `MissingSummary` (write "Returns the header." as the first sentence, not only an `@return`).

---

## 2. Project setup

### Modules

| Artifact (`be.imgn.swing:…`) | Use | Module name |
|---|---|---|
| `swing-core` | Lifecycle, scopes, observables, tasks, settings, resources, messages, locales, appearance, validation. No Swing. | `be.imgn.swing` |
| `swing-ui` | Launching, views, windows, dialogs, commands, menus, binding, forms, palette, keymap | `be.imgn.swing.ui` |
| `swing-desktop` | Documents, recent files, single instance, OS open-file events, notifications | `be.imgn.swing.desktop` |
| `swing-flatlaf` | FlatLaf look and feel, dark mode, theme tokens, SVG icons | `be.imgn.swing.flatlaf` |
| `swing-testing` | `AppTester`, the headless test harness | `be.imgn.swing.testing` |
| `swing-processor` | The annotation processor (processor path only) | — |

Java: the framework is compiled for Java 21 and runs on 21 or later.

### Maven

Dependencies, as `swing-examples/pom.xml` declares them (the parent's `dependency:analyze` requires every used compile dependency to be declared). Give every `be.imgn.*` dependency its version from the properties below (`${swing.version}`, `${inject.version}`, `${toml.version}`, `${gaia.version}`); the parent manages the versions of the others.

| Dependency | Scope |
|---|---|
| `be.imgn.swing:swing-ui`, `be.imgn.swing:swing-core` | compile |
| `be.imgn.swing:swing-desktop` | compile, only when used |
| `be.imgn.swing:swing-flatlaf` | **runtime**: no code references it; without it `.svg` icons fail at startup |
| `be.imgn.inject:inject`, `be.imgn.toml:toml`, `be.imgn.gaia:gaia-core`, `org.jspecify:jspecify` | compile |
| `be.imgn.swing:swing-testing`, `be.imgn.gaia:gaia-core-testing`, `be.imgn.gaia:gaia-memfs`, `org.junit.jupiter:junit-jupiter`, `org.assertj:assertj-core` | test |

The build configuration of an application whose POM inherits `be.imgn.parent:parent` directly. The versions are those the framework is built with: other versions of inject, toml or gaia fail the enforcer's dependency convergence. The parent's enforcer requires a version on every plugin, and the parent binds neither the `register-factories` nor the `register-services` goal, so both plugins are declared in full.

```xml
<properties>
  <swing.version>0.1.0-SNAPSHOT</swing.version>
  <inject.version>0.1.1</inject.version>
  <toml.version>0.1.0</toml.version>
  <gaia.version>0.1.2</gaia.version>
  <!-- Checkstyle otherwise rejects the package-private @Exposed and @Command fields. -->
  <checkstyle.visibility.ignoredAnnotations>be.imgn.swing.ui.Exposed, be.imgn.swing.ui.Command</checkstyle.visibility.ignoredAnnotations>
  <!-- Only when the application's root package isn't under be.imgn. -->
  <nullaway.annotated.packages>com.example</nullaway.annotated.packages>
</properties>

<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <!-- Append to the parent's settings, never replace them, or Error Prone and NullAway disappear.
             -processing: the processors claim no annotation; -exports: inject's public factories name
             package-private beans. -->
        <compilerArgs combine.children="append">
          <arg>-Xlint:-processing</arg>
          <arg>-Xlint:-exports</arg>
        </compilerArgs>
        <annotationProcessorPaths combine.children="append">
          <path><groupId>be.imgn.inject</groupId><artifactId>inject-processor</artifactId><version>${inject.version}</version></path>
          <path><groupId>be.imgn.swing</groupId><artifactId>swing-processor</artifactId><version>${swing.version}</version></path>
          <path><groupId>be.imgn.toml</groupId><artifactId>toml-processor</artifactId><version>${toml.version}</version></path>
          <path><groupId>be.imgn.gaia</groupId><artifactId>gaia-processor</artifactId><version>${gaia.version}</version></path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
    <plugin>
      <artifactId>maven-surefire-plugin</artifactId>
      <configuration>
        <!-- Without it, a test JVM may hang connecting to a window server. -->
        <systemPropertyVariables>
          <java.awt.headless>true</java.awt.headless>
        </systemPropertyVariables>
      </configuration>
    </plugin>
    <!-- On the module path, both are mandatory: they add the generated services to module-info.class.
         Without them, Swing.launch throws IllegalArgumentException ("No application descriptor for …"). -->
    <plugin>
      <groupId>be.imgn.inject</groupId>
      <artifactId>inject-maven-plugin</artifactId>
      <version>${inject.version}</version>
      <executions>
        <execution>
          <goals>
            <goal>register-factories</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
    <plugin>
      <groupId>be.imgn.gaia</groupId>
      <artifactId>gaia-maven-plugin</artifactId>
      <version>${gaia.version}</version>
      <executions>
        <execution>
          <goals>
            <goal>register-services</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Tests that declare beans, subscribers or `@Settings` records **in test sources** must run on the class path (a second surefire execution with `<useModulePath>false</useModulePath>`, as `swing-examples/pom.xml` does with its `fixture-tests` execution): on the module path, the main module's descriptor doesn't provide their services.

The parent's other checks:

- Checkstyle:
  - fields ordered by visibility, so package-private `@Exposed` fields come before `private` ones;
  - no trailing comments;
  - no magic numbers outside tests: name them as constants (`private static final int MAX_SIZE = 72;`);
  - at most 7 parameters per method or constructor;
  - methods of at most 60 lines;
  - at most 4 `return`s, 2 in a `void` method;
  - lines of at most 120 characters;
  - no catching `Exception`, `Throwable`, `RuntimeException` or `Error`;
  - Javadoc isn't required, but Javadoc that is written must be complete (every `@param`, `@return`, `@throws`).
- Error Prone: `SystemOut` is an error, so no `System.out`.
- Spotless reformats the sources with Palantir Java Format on every build.

### module-info

```java
module com.example.todo {
    requires be.imgn.swing.desktop;
    requires static org.jspecify;
}
```

Require `be.imgn.swing.desktop`, or `be.imgn.swing.ui` without swing-desktop; both bring core, inject, toml, gaia and java.desktop. No `opens`, no hand-written `provides`: the plugins add the services, and resources are read by generated code. Annotate each package `@NullMarked` in its `package-info.java`.

### Resource files

Each class's TOML files sit next to it, under `src/main/resources/<package path>/`:

| File | Read |
|---|---|
| `X.toml` | always: the base file |
| `X.windows.toml`, `X.macos.toml`, `X.linux.toml` | on that platform |
| `X_fr.toml`, `X_fr_BE.toml` (and their platform variants) | in that locale |
| `X.dark.toml` | while the appearance is dark (no locale or platform part allowed) |

A nested class uses its own simple name: `CounterApp.CounterMessages` reads `CounterMessages.toml`. Merge order, later wins, table by table and key by key: the superclass's resources, `X`, `X.<platform>`, `X_fr`, `X_fr.<platform>`, `X_fr_BE`, `X_fr_BE.<platform>`, then `X.dark` when dark. `${key.path}` references are resolved after merging. Icons and other files in the package are referenced by paths relative to the package.

IDEs that compile without copying resources to `target/classes` need the processor option `-Abe.imgn.swing.resources=<resources dir>`.

---

## 3. Mental model

- **One application class** annotated `@Application`, launched by `Swing.launch(App.class, args)`. It is either a `SingleFrame` (it *is* the main window's view) or a `MultiFrame` (a singleton that opens frames).
- **Windows hold views.** A view (`View`) supplies `content()`. A `FrameView` adds `menuBar()` and `toolBar()`; a `DialogView<R>` adds `result()`, `buttons(...)` and `commit()`.
- **Three injection scopes**: the application (`@Singleton`), one scope per window (`@WindowScoped`), one per view (`@ViewScoped`). Closing a window closes its scopes, which releases every subscription, binding and task they own.
- **Structure in Java, text and tuning in TOML.** Java builds the component tree and the behaviour; TOML gives texts, mnemonics, shortcuts, icons, fonts, colors and accessible names, per locale, platform and appearance. The build checks the TOML against the code.
- **Generated code you use**: `<X>Commands` (the commands of class `X`), `<X>Menus` (its custom menus), `<X>Factory` (dialog and frame views with `@Assisted` constructor parameters), and the implementations of `@Messages` interfaces, which you inject by the interface. Generated classes land in the class's package; never write a class with the same name.
- **State is observable.** `Property<T>`, `ObservableList<E>` and `Observable<T>` hold state; `Ui.bind`, `Ui.render`, `Form.bind` and command `enabled` attributes keep components in step with it.
- **Long work runs in tasks** on virtual threads, returned by command handlers; the framework blocks input as asked and brings the results back on the EDT.

---

## 4. The application class and launching

```java
@Application(name = "Counter", vendor = "example", qualifier = "com", version = "1.0")
public final class CounterApp implements SingleFrame, ExitVeto {
    private final Property<Integer> count = Property.of(0);
    private final CounterMessages messages;
    private final CounterAppCommands commands;
    private final Ui ui;

    @Inject
    CounterApp(CounterMessages messages, CounterAppCommands commands, Ui ui) { … }

    public static void main(String[] args) {
        Swing.launch(CounterApp.class, args);
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
        return ui.menuBar(ui.menus().file().with(commands.exit()));
    }

    @Override
    public boolean canExit() {
        return ui.dialogs().confirm(messages.confirmExit());
    }

    @Command
    void increment() {
        count.set(count.get() + 1);
    }
}
```

(`counter/CounterApp.java`; the label's text is built from a message without `ui.render` because it depends on `count` only and the example doesn't switch locales.)

**`@Application`** (`be.imgn.swing`): `name`, `vendor`, `qualifier`, `version`, all optional strings. `name` defaults to the class's simple name; `qualifier` requires `vendor`. `name`, `vendor` and `qualifier` choose the application's directories (config, data, state, cache), so they must be valid file names. The class implements exactly one of `SingleFrame` and `MultiFrame` and isn't abstract.

**`Swing.launch(Class<?>, String[])`** (`be.imgn.swing.ui`):

1. builds the root injector, binding `ApplicationInfo`, `Arguments` and `DisplayLocale`;
2. runs the startup steps on the main thread;
3. installs the look and feel on the EDT, then shows the `SingleFrame`'s window, or calls `MultiFrame.start(Frames, Arguments)`;
4. calls `ready()` on the application class, then every `ReadyListener`, once the window is shown.

A startup failure shows an error dialog and exits with status 1. A class with no registered descriptor makes `Swing.launch` throw `IllegalArgumentException` instead (see [Build errors](#19-build-errors-and-their-fixes)).

```java
public interface SingleFrame extends FrameView { default void ready() {} }      // EDT, once the window is shown
@Singleton public interface MultiFrame {
    void start(Frames frames, Arguments arguments);   // EDT; must leave at least one frame open
    default void ready() {}
}
```

A `MultiFrame` application exits when its last frame closes; a `SingleFrame` one when its main window closes. A `MultiFrame` class is a singleton, not a view: it has no `Ui` and no window, and may not be `@WindowScoped` or `@ViewScoped`. A `MultiFrame` class that implements `ExitVeto` is asked at exit, with no `@IntoSet`.

**Application resources** (`App.toml`), allowed only on the application class:

| Key | Value |
|---|---|
| `name` | Displayed name (translatable) |
| `theme` | `"system"` (default), `"light"` or `"dark"`; the system property `be.imgn.swing.theme` wins |
| `description`, `copyright` | Shown in the About dialog |
| `website` | Absolute `http`/`https` URI, shown in the About dialog |
| `icon` | Path relative to the package; the icon of the About dialog, every window and the taskbar; must exist |
| `[flatlaf]` | FlatLaf tokens (see [FlatLaf](#flatlaf)) |

`ApplicationInfo` (injectable): `type()`, `name()`, `displayedName()`, `vendor()`, `qualifier()` and `version()` (each an `Optional<String>`), `identifier()`, `platform()`, `directories()`. `Arguments` is `record Arguments(List<String> values)`. gaia's `ApplicationDirectories` (injectable) gives `config()`, `data()`, `state()`, `cache()`.

Development mode: `-Dbe.imgn.swing.dev=true` reloads TOML files from the class output while the application runs, turns framework mistakes into exceptions (duplicate mnemonics, shortcut collisions, accessibility failures) and enables the inspector (`shortcut alt shift I`). Tests always run in this strict mode.

---

## 5. Views, frames and dialogs

### Interfaces

```java
@ViewScoped public interface View { JComponent content(); }

public interface FrameView extends View {
    default @Nullable JMenuBar menuBar() { return null; }
    default @Nullable JToolBar toolBar() { return null; }    // placed at BorderLayout.NORTH
    default void configureFrame(JFrame frame) {}             // before pack() and before the saved bounds are restored
    default boolean restoreBounds() { return true; }
}

public interface DialogView<R> extends View {
    Optional<R> result();                                                     // read after ok()
    default List<BoundCommand> buttons(StandardCommands commands) { return List.of(commands.ok(), commands.cancel()); }
    default void commit() {}
}
```

A view class is not `private`. Build order of a frame: create the frame; set its title (the `title` resource, else the displayed name) and icon; create the window and view scopes; construct the view; **apply the component resources**; call `content()`, `menuBar()`, `toolBar()`; apply `tab-title`s; attach the commands; run the accessibility checks (strict mode); `configureFrame`; `pack()`; restore the saved bounds or center; show. A title the view sets in `content()` with `ui.window().setTitle(…)` wins.

### `Ui`: the view's toolbox

`Ui` is `@ViewScoped`: inject it in a view or a view-scoped bean. Every method runs on the EDT; everything it registers belongs to the view's scope.

| Method | Does |
|---|---|
| `Window window()` | The view's window: `setTitle`, `title()`, `setIconImage`, `focus(Component)`, `toFront()`, `rootPane()` |
| `Dialogs dialogs()` | The window's standard dialogs |
| `StandardMenus menus()` | `file()`, `edit()`, `view()`, `window()`, `help()` |
| `bind(Consumer<? super T> target, Observable<T> source)` | Applies the value now and at each change, on the EDT, coalesced |
| `bind(JList, ObservableList, Function text[, Property<Optional<E>> selection[, Function key]])` | See [Lists and tables](#11-lists-and-tables) |
| `bind(JTable, ObservableList, List<Column<E,?>>[, selection[, key]])` | See [Lists and tables](#11-lists-and-tables) |
| `render(Runnable render, Observable<?>... sources)` | Runs now, at each change of a source, and at each refresh (locale, appearance) |
| `addTab(JTabbedPane tabs, Component component)` | Adds a tab titled by the component's `tab-title` resource; the component must be in an `@Exposed` field; not in the constructor |
| `menuBar(MenuSpec...)`, `popupMenu(JComponent invoker, MenuEntry...)`, `toolBar(ToolBarEntry...)`, `buttonBar(BoundCommand...)` | See [Menus](#9-menus-toolbars-and-popups) |
| `run(Task<?> task, Block block)` | Runs a task outside a command; `Block.NONE`, `WINDOW` or `APPLICATION` only |
| `every(Duration period, Runnable action)` | Runs the action on the EDT every period; returns a `Subscription` |
| `showDialog(Class<? extends DialogView<R>> type)` → `Optional<R>` | Modal; blocks until closed; the result on OK, empty on cancel |
| `showDialog(type, E variant)` | A [variant](#variants) of the dialog |
| `showDialog(Class<F> factory, Function<? super F, ? extends DialogView<R>> create)` | A dialog whose constructor has `@Assisted` parameters |

### Dialogs

A dialog view gets a window scope and a view scope of its own, under the opening view's scope: its `Form`, `Dialogs`, `UndoStack` and commands are its own. Its title is its `title` resource, else the application's displayed name. Its buttons are `buttons(commands)`, using the `StandardCommands` passed in. Enter activates `ok()`, Escape and the close box `cancel()`.

| Command | Enabled | Does |
|---|---|---|
| `ok()` | always | Validates the dialog's `Form`; if invalid, stays open showing the errors. Otherwise calls `commit()` **only if the form is dirty**, then closes; `showDialog` returns `result()` |
| `apply()` | while the form is dirty | Validates; if valid, calls `commit()` and marks the form clean; stays open |
| `cancel()` | always | Closes; `showDialog` returns empty |

Work that must happen on every OK belongs in the caller, which reads the result. Rename a button in the dialog's TOML: `[commands.ok] text = "&Save"`.

A dialog with arguments takes them as `@Assisted` (`be.imgn.inject.Assisted`) parameters of its `@Inject` constructor; the processor generates `<Dialog>Factory` (for dialog and frame views only):

```java
public final class ContactDialog implements DialogView<Contact> {
    @Inject
    ContactDialog(@Assisted Contact contact, Form form, AddressBookMessages messages) { … }
    …
}
// caller:
ui.showDialog(ContactDialogFactory.class, factory -> factory.create(contact)).ifPresent(service::modifyContact);
```

### `Dialogs` (`@WindowScoped`; `ui.dialogs()`)

```java
boolean confirm(String message);
void inform(String message);
void error(String message, Throwable cause);
Optional<Path> openFile(List<FileType> types);                          // empty list: all files
Optional<Path> saveFile(List<FileType> types, String suggestedName);    // adds the extension; confirms before replacing
SaveChoice askSave(String documentName);                                // SAVE, DISCARD or CANCEL
```

`FileType` and `SaveChoice` are top-level types of `be.imgn.swing.ui`, not members of `Dialogs`. `record FileType(String description, List<String> extensions)` takes at least one extension, each without the dot: `List.of("txt")`. `SaveChoice` is `SAVE`, `DISCARD` or `CANCEL`.

### Frames

`Frames` (`@Singleton`, EDT) opens more frames, each with its own window and view scopes under the application:

```java
<V extends FrameView> V open(Class<V> type);
<F, V extends FrameView> V open(Class<F> factory, Function<? super F, ? extends V> create);
<V extends FrameView, E extends Enum<E>> V open(Class<V> type, E variant);
List<FrameView> views();  Optional<FrameView> active();  boolean close(FrameView view);  void toFront(FrameView view);
```

Closing a frame (close box, `closeWindow()`, `Frames.close`) asks the exit vetoes of its view and window scopes, saves its window state, disposes it and closes its scopes, which runs `@PreDestroy`. Closing the main window of a `SingleFrame` application, or the last frame of a `MultiFrame` one, requests exit instead: every veto is asked, and `Frames.close` returns `false`.

### Variants

One enum nested in a dialog or frame view, annotated `@Variants` (`be.imgn.swing.ui.Variants`), gives the view several resource sets. Every constant needs a `[variants.<constant-in-kebab-case>]` table in the base file, laid out like the base table, merged over it. A variant table holds class properties and component tables only: no `commands`, `menus`, `variants` or `flatlaf`, which stay in the base table for every variant. A `[variants]` table without a `@Variants` enum is an error. Open a variant with `frames.open(type, constant)` or `ui.showDialog(type, constant)`; each variant saves its own window state.

```java
public final class SecondaryWindow implements FrameView {
    @Exposed final JLabel label = new JLabel("", SwingConstants.CENTER);
    @Variants public enum Name { ZERO, ONE, TWO }
    …
}
// frames.open(SecondaryWindow.class, SecondaryWindow.Name.ONE);
```

```toml
[label]
font = "Lucida-PLAIN-48"

# every constant needs its table
[variants.zero]
title = "Window Zero"

[variants.zero.label]
text = "Label Zero"

[variants.one]
title = "Window One"

[variants.one.label]
text = "Label One"

[variants.two]
title = "Window Two"

[variants.two.label]
text = "Label Two"
```

### Exit vetoes

A view or any window- or view-scoped bean that implements `ExitVeto` (`boolean canExit()`, on the EDT) is asked when its frame closes (closing a dialog asks no veto) and when the application exits; `false` cancels. A `@Singleton` veto must be contributed with `@IntoSet(ExitVeto.class)`; a plain singleton that implements `ExitVeto` is never asked, except the `MultiFrame` application class.

---

## 6. Resources in TOML

### What a class's file may hold

```toml
title = "Contact"                 # class properties: plain values at the root

[commands.save]                   # the class's commands, and standard commands to rename
text = "&Save"
shortcut = "shortcut S"
icon = "icons/save.svg"
tool-tip-text = "Save the note"

[menus.contacts]                  # custom menus (application or view classes)
text = "&Contacts"

[firstNameLabel]                  # any other table: an @Exposed component field
text = "&First Name"

[home.streetLabel]                # nested: an @Exposed field of the field `home`'s class
text = "S&treet"
```

Allowed root keys: class properties (`title` on frame and dialog views, but not document views; `name`, `theme`, `description`, `copyright`, `website`, `icon` on the application class only), and the tables `commands`, `menus`, `variants` (views with `@Variants`) and `flatlaf` (application class only). Any other table must name a reachable `@Exposed` component field; anything else is a compile error.

### Component properties

| Key | Type | Applies to |
|---|---|---|
| `text` | string | Anything with `setText(String)`. On buttons and labels, `&` marks the mnemonic (`&&` is a literal `&`); at most one `&` |
| `tool-tip-text` | string | `JComponent` |
| `icon` | string path | Anything with `setIcon(Icon)`; the file must exist. `.png` (with `@2x` variants) or `.svg` (needs `swing-flatlaf`) |
| `font` | string | Any component; `Font.decode` syntax: `"Monospaced-14"`, `"SansSerif-BOLD-12"` |
| `foreground`, `background` | string | Any component; `"#rrggbb"` or `"#rrggbbaa"` |
| `enabled`, `visible` | boolean | Any component; applied once, not at a refresh |
| `accessible-name`, `accessible-description` | string | Any component |
| `columns`, `rows` | integer | Anything with `setColumns(int)` / `setRows(int)` |
| `title` | string | `JComponent`: the title of its `TitledBorder` (created if it has no border; any other border throws `IllegalStateException`) |
| `tab-title` | string | The component's tab: applied at the end of the build if the component is already a tab, and by `ui.addTab` when it adds it |

There is no `mnemonic` key: the mnemonic comes from `&` in `text`. Mnemonics are not set on macOS.

### Translations and variants

- `X_fr.toml` must hold **every key** of `X.toml`, non-text keys included.
- `X_fr_BE.toml` (country) and `X.dark.toml` may hold a subset.
- No locale file and no dark file may hold a key the base file lacks.
- `X.dark.toml` holds colors, icons and FlatLaf tokens, never texts; it has no locale or platform part.
- A TOML file named after a class the processor generates nothing for (not the application class, a view, a `@Messages` interface or a class with commands) gives a warning, which `-Werror` turns into an error. A file whose name matches no class is ignored silently: a misspelled file name is not reported, its resources just don't apply.

---

## 7. Messages and locales

### `@Messages`

```java
@Messages
public interface TodoMessages {
    /**
     * Returns the status line.
     *
     * @param count the number of tasks
     * @return the status line
     */
    String count(int count);
}
```

```toml
# TodoMessages.toml
count = "{count,choice,0#No tasks|1#One task|1<{count,number,integer} tasks}"
```

- An interface, not private, top-level or nested. Every abstract method is a message and returns `String`; no generic methods, no two methods with one name. `default` methods are allowed and are not messages.
- `TodoMessages.toml` (flat: method name = pattern) is required and has every method; every language file has every key; a key that names no method is an error. No dark variant.
- Patterns are `MessageFormat` syntax, checked at compile time; placeholders **name parameters**: `{count}`, `{count,number,integer}`, `{count,choice,0#none|1#one|1<{count,number,integer} items}`. `{0}` is an error. Write an apostrophe as `''`.
- Inject the interface. The generated implementation is a singleton that follows the current locale: call it again to get the text in the new locale.

### Locales

`Locales` (singleton):

```java
Observable<Locale> current();
void set(Locale locale);    // any thread; switches resources, refreshes every open window, saves the choice
void reset();               // back to the launch locale, forgetting the saved choice
<M> M messages(Class<M> type, Locale locale);   // a messages implementation fixed to one locale
```

The saved choice wins over the JVM's locale on the next launch. On a change, every open window's component resources, commands, menus, `Column` header suppliers and `ui.render` blocks are applied again, and the window titles that came from resources follow. What does **not** follow: a text set once with `setText(messages.x())`, a `Column` header given as a `String`, and validation requirements captured in rules. Use `ui.render(() -> label.setText(messages.x(...)), sources...)` for texts that code builds.

```java
@Command
void french() {
    locales.set(Locale.FRENCH);
}
```

### Appearance

`Appearances.current()` is an `Observable<Appearance>` (`LIGHT` or `DARK`), read-only. With `swing-flatlaf` it follows the `theme` resource of the application class (or the `be.imgn.swing.theme` system property); under `system` it rereads the system's appearance every 10 seconds and when the application is activated; a change applies the `.dark.toml` variants, reinstalls the look and feel and refreshes the windows. Code that paints with its own colors renders through `ui.render(…, appearances.current())`.

---

## 8. Commands

### Declaring

A command is a method (or a `Property<Boolean>` field) annotated `@Command` (`be.imgn.swing.ui`), in a view or a scoped bean.

```java
@ViewScoped
public class ContactActions {
    private final Property<Optional<Contact>> selected = Property.of(Optional.empty());
    private final AddressBookService service;
    private final Ui ui;
    private final AddressBookMessages messages;

    @Inject
    ContactActions(AddressBookService service, Ui ui, AddressBookMessages messages) { … }

    Observable<Boolean> hasSelection() {               // an enabled target: a no-arg method needs no @Exposed
        return selected.map(Optional::isPresent);
    }

    @Command(enabled = "hasSelection", block = Block.COMMAND)
    Task<Void> deleteContact() {
        Contact contact = selected.get().orElseThrow();
        if (!ui.dialogs().confirm(messages.confirmDelete(contact.firstName(), contact.lastName()))) {
            return Task.none();
        }
        return Task.of(() -> service.removeContact(contact)).onSuccess(done -> ui.dialogs().inform(messages.deleteDone()));
    }
}
```

| Attribute | Meaning |
|---|---|
| `id` | Defaults to the method or field name; the TOML key `[commands.<id>]` |
| `enabled` | Name of an `@Exposed` `Observable<Boolean>` field, or of a non-private no-arg method returning one |
| `selected` | Same, with a `Property<Boolean>`: a toggle (check box menu item, toggle button); activating it flips the property, then calls the handler |
| `block` | `Block.NONE` (default), `COMMAND`, `COMPONENT`, `WINDOW`, `APPLICATION`: what to block while the returned task runs (handler must return a `Task`) |
| `concurrency` | `PARALLEL` (default), `RESTART`, `SERIAL`: what a new activation does while a task runs. Other than `PARALLEL`, the handler must return a `Task`, and `block` may not be `COMMAND` or `COMPONENT` |
| `cancels` | Id of a command of the same class (declared or inherited) whose handler returns a `Task`: this command is its stop command, enabled only while that command runs; activating it cancels those tasks, then calls this handler. A stop command returns `void` and takes no `enabled`, `selected`, `block` or `concurrency` |

Handler rules: not private, not static; returns `void` or `Task<?>`; takes no parameter or one `CommandContext`. A handler runs on the EDT. Return `Task.none()` when there's nothing to run.

A toggle with no handler:

```java
@Command(id = "wordWrap")
final Property<Boolean> wordWrap = Property.of(true);   // no @Exposed needed
```

A stop command:

```java
@Command(concurrency = Concurrency.RESTART)
Task<BufferedImage> load() { … }

@Command(cancels = "load")
void stopLoading() { showMessage(messages.loadingCancelled()); }   // runs after load's tasks are cancelled
```

### Using: `<X>Commands` and `BoundCommand`

For each class with commands, the processor generates `<X>Commands` (`@ViewScoped`, extending `StandardCommands`, or the superclass's `<Super>Commands` when the superclass declares commands), with one `BoundCommand <handlerName>()` per command. Inject it in a view or view-scoped bean; a nested class `Outer.Inner` gives `OuterInnerCommands`.

```java
String id();
JButton createButton();  JMenuItem createMenuItem();
JToggleButton createToggleButton();  JCheckBoxMenuItem createCheckBoxMenuItem();   // toggles only
Action asAction();
boolean execute();                      // false when disabled; any thread
boolean execute(CommandContext context);
Observable<Boolean> running();          // true while its tasks run
void cancel();                          // cancels its running tasks
```

A command's shortcut works in its window, even when the command is in no menu, once its `<X>Commands` has been injected in a view of that window (directly or through a view-scoped bean). `CommandContext.of(Trigger, Modifier...)` builds a context; `context.trigger()` is a sealed `Trigger`: `MenuSelection`, `PopupMenuSelection`, `ButtonClick`, `Shortcut`, `OperatingSystem`, `Programmatic`, `Palette` (switch over it exhaustively); `context.modifiers()` holds `SHIFT`, `CONTROL`, `ALT`, `META`, `SHORTCUT`.

### Command resources

`[commands.<id>]` keys, all strings: `text` (required for your own commands, in the base file of the class or of a superclass; `&` for the mnemonic), `shortcut`, `icon`, `tool-tip-text`, `accessible-name`, `accessible-description`.

`shortcut` uses `KeyStroke.getKeyStroke` syntax where `shortcut` means Ctrl, or ⌘ on macOS: `"shortcut S"`, `"shortcut shift S"`, `"alt F4"`, `"DELETE"`, `"F5"`. Put platform-specific shortcuts in `X.macos.toml` etc. Two commands of one class with the same shortcut are a compile error; two in one window, from different classes, fail in strict mode. The exception is a standard command's default shortcut (`shortcut Q`, `W`, `X`, `C`, `V`, `A`, `Z`, `Y`, `shortcut shift P`…): your command takes it silently, and the standard command has no shortcut in that window. Users can remap shortcuts (see [Keymap](#keymap-and-command-palette)).

### Standard commands

`StandardCommands` (`@ViewScoped`; every `<X>Commands` extends it) provides:

| Method | Does | Default shortcut |
|---|---|---|
| `exit()` | Asks the vetoes, exits | `shortcut Q` (`alt F4` on Windows) |
| `about()` | Shows the About dialog | — |
| `preferences()` | Calls the `PreferencesHandler` bean; disabled without one | `shortcut COMMA` on macOS |
| `cut()`, `copy()`, `paste()`, `selectAll()` | Act on the focused component | `shortcut X`, `C`, `V`, `A` |
| `undo()`, `redo()` | Act on the view's `UndoStack` | `shortcut Z`, `shortcut Y` (`shortcut shift Z` on macOS) |
| `closeWindow()` | Closes the frame after its vetoes (main window of a `SingleFrame` application: same as `exit()`; in a dialog: cancels) | `shortcut W` |
| `ok()`, `cancel()`, `apply()` | Dialog views only | — |
| `commandPalette()` | Shows the command palette (attached to every frame automatically) | `shortcut shift P` |
| `keymap()` | Shows the keyboard shortcuts dialog | — |
| `MenuEntry openWindows()` | The Window menu's list of open frames | — |

Rename a standard command for one view in that view's TOML (`[commands.ok] text = "&Save"`); the behaviour stays. A `[commands.exit]` table in a `MultiFrame` application belongs in each frame view's file, since the application class is not a view. Declaring your own `@Command` with a standard id warns in any class (an error under `-Werror`); in a view it overrides the standard command for that view, elsewhere it applies nowhere. A handler named like a standard command (`copy()`, `undo()`, `exit()`…) with another `id` is a compile error. Use other ids and handler names.

`PreferencesHandler` enables `preferences()`:

```java
@Singleton
public final class NotesPreferences implements PreferencesHandler {
    @Inject
    NotesPreferences() {}

    @Override
    public void showPreferences(Ui ui) {        // EDT; use ui only during the call
        ui.showDialog(PreferencesDialog.class);
    }
}
```

---

## 9. Menus, toolbars and popups

In `menuBar()`, `commands` is `DocumentCommands` (from `memo/MemoView`); in `toolBar()`, it is the view's own `<X>Commands` (from `saf/SingleFrameExample5`). A document view that declares commands injects both.

```java
@Override
public JMenuBar menuBar() {
    return ui.menuBar(
            ui.menus().file().with(commands.newDocument(), commands.open(), commands.recentFiles(), MenuEntry.SEPARATOR,
                    commands.save(), commands.saveAs(), MenuEntry.SEPARATOR, commands.closeWindow(), commands.exit()),
            ui.menus().edit().with(commands.undo(), commands.redo(), MenuEntry.SEPARATOR, commands.cut(),
                    commands.copy(), commands.paste(), commands.selectAll(), MenuEntry.SEPARATOR,
                    commands.commandPalette(), commands.keymap()),
            ui.menus().window().with(commands.openWindows()),
            ui.menus().help().with(commands.about()));
}

@Override
public JToolBar toolBar() {
    return ui.toolBar(commands.previousImage(), commands.nextImage(), ToolBarEntry.SEPARATOR, commands.stopLoading());
}
```

- Entries are sealed types: `MenuEntry` (`BoundCommand`, `MenuEntry.SEPARATOR`, a submenu `MenuSpec`, `MenuEntry.dynamic(...)`), `ToolBarEntry` (`BoundCommand`, `ToolBarEntry.SEPARATOR`), `DynamicEntry` (`BoundCommand`, `DynamicEntry.SEPARATOR`, a `MenuSpec`, or a plain `DynamicEntry.item(String text, Runnable action)`). `DynamicEntry.numbered(items)` prefixes `&1 ` to `&9 ` to the first nine items' texts.
- **Custom menus**: a `[menus.<id>]` table with a `text` in the class's base TOML generates `<X>Menus extends StandardMenus` with `BoundMenu <lowerCamelId>()`: `[menus.task-tests]` gives `taskTests()`. Inject `<X>Menus` where you build the bar. A menu table holds only `text`; an id that converts to `menu` or to a standard menu's method (other than that menu's own id) is a compile error.
- Rename a standard menu (`file`, `edit`, `view`, `window`, `help`) for the whole application with `[menus.file] text = "…"` in the application class's TOML; a view's own `[menus.file]` wins for that view.
- **Dynamic sections** rebuild when an observable changes:

  ```java
  MenuEntry.dynamic(recentFiles.map(files -> DynamicEntry.numbered(files.stream()
          .map(file -> DynamicEntry.item(file.getFileName().toString(), () -> open(file)))
          .toList())))
  ```

- `ui.popupMenu(table, commands.copy(), commands.paste())` installs a popup on the component.
- `ui.buttonBar(commands...)` builds a centered row of buttons (toggle buttons for toggles).
- In menus and popup menus (not toolbars), the framework removes leading, trailing and doubled separators, and, on macOS, drops `exit()`, `about()` and `preferences()` (the application menu holds them). Don't compensate by hand.
- Duplicate mnemonics within a menu or across the bar fail in strict mode.

---

## 10. Observable state and binding

Core types (`be.imgn.swing`), all thread-safe:

```java
Observable<T>: T get(); Subscription subscribe(Scope scope, Consumer<? super T> listener);   // on change only, not with the current value
               <R> Observable<R> map(Function<? super T, ? extends R> f);
               static <A, B, R> Observable<R> combine(Observable<A> a, Observable<B> b, BiFunction<…> f);
Property<T> extends Observable<T>: static <T> Property<T> of(T initial); void set(T value);   // never null
```

- Listeners run synchronously on the thread that called `set`, only when the value changes (`Objects.equals`). `map` and `combine` functions may run several times on any thread: keep them cheap and pure.
- Core has no EDT. To touch components from an observable, use `ui.bind(target, source)` or `ui.render(...)`, which run on the EDT.
- Subscriptions belong to a `Scope` (inject `Scope` to get the current bean's); closing the scope ends them.
- `Observable`, `Property`, `ObservableList` and `Task` are sealed: never implement them. Create them with `Property.of`, `ObservableList.of`/`copyOf`, `map`, `Observable.combine` and the `Task` factories.

```java
ui.bind(label::setText, count.map(messages::count));
ui.bind(editor::setFont, settings.map(s -> new Font(Font.MONOSPACED, Font.PLAIN, s.fontSize())));
ui.render(() -> status.setText(messages.saved(file.get())), file);
```

`Observable<Boolean>` values drive command `enabled` and `selected` states; `Form.dirty()` and `Form.valid()` are such values.

---

## 11. Lists and tables

`ObservableList<E>` (core): `ObservableList.of(E...)`, `ObservableList.copyOf(Collection)`; `size`, `get(int)`, `add`, `add(int, E)`, `addAll`, `set`, `remove(int)`, `remove(E)`, `setAll`, `clear`; `get()` returns an immutable snapshot; `subscribeChanges(Scope, Consumer<ListChange<E>>)` (sealed: `Added`, `Removed`, `Replaced`). It may be modified from any thread, for example from a task; bound components update on the EDT. Elements are never `null`. Listeners must not modify any observable list.

```java
ui.bind(table, service.contacts(),
        List.of(Column.of(messages::firstNameHeader, String.class, Contact::firstName),
                Column.of(messages::lastNameHeader, String.class, Contact::lastName)),
        selected,          // Property<Optional<Contact>>
        Contact::id);      // key: a modified or reloaded row stays selected
```

- `Column.of(Supplier<String> header, Class<V> type, Function<? super E, ? extends V> value)`: pass a messages method reference as the header so it follows the locale. The `Column.of(String …)` form freezes the header.
- `column.editable(BiFunction<E, V, E> with)` makes a column editable (`String`, `Boolean`, `Integer`, `Long`, `Double`; any other type throws `IllegalArgumentException`); a committed edit replaces the row with `with.apply(row, value)`.
- Binding installs a new model and no row sorter: don't add one. With a selection property, it also sets single selection.
- With a selection property, the user's selection sets it (`Optional.empty()` for none) and setting it selects the row.
- `JList` bindings take a `Function<? super E, String>` for the text instead of columns.
- A component can be bound once; binding it again throws `IllegalStateException`.
- A table needs an accessible name: `[table] accessible-name = "Contacts"`. Put it in a `JScrollPane` yourself.

---

## 12. Forms and validation

Each view has one `Form` (`@ViewScoped`); inject it.

```java
Binding<String>  bind(JTextComponent field, Property<String> property);
<T> Binding<T>   bind(JTextComponent field, Property<T> property, Converter<T> converter);
Binding<Boolean> bind(AbstractButton button, Property<Boolean> property);          // check boxes, toggles, radios
<E> Binding<Optional<E>> bind(JComboBox<E> combo, ObservableList<E> items, Function<? super E, String> text,
                              Property<Optional<E>> selection);
void track(Observable<?> value);      // dirty-tracks state with no component, such as a list edited in a table
Observable<Boolean> valid();
Observable<Boolean> dirty();          // a value differs from its baseline; typing the original back makes it clean
boolean validate();                   // shows every error, focuses the first (selecting its tab)
void markClean();                     // the current values become the baselines
```

`Binding<T>`: `check(Rule<? super T>)`, `check(Validator<? super T>)`, `revalidateOn(Observable<?>)` (revalidate when another value changes, such as a confirmation field), `validation()`, `shown()`, `validated()`.

Validation: `Rule` and `Validator` are in core (`be.imgn.swing`), `Converter` in `be.imgn.swing.ui`:

```java
Rule.of(String requirement, Predicate<? super T> test);
Rule.required(String requirement);                         // a String that isn't blank
Validator.of(Rule<? super T>... rules);
Converter.of(Function<String, T> parse, Function<T, String> format, String requirement);
Converter.optional(Converter<T> converter);                 // blank text <-> Optional.empty()
```

- A requirement says what the value **must be** ("At least 8 characters"), from `@Messages`. Build validators in a factory method that takes the messages, not as constants.
- Rules run on the EDT at each keystroke: cheap and synchronous only.
- An error shows after the user's first change of the field, or after `validate()` (OK). It appears as the field's tool tip and accessible description, with a red border (FlatLaf: a red outline).
- A converter's `parse` rejects with `IllegalArgumentException` or `DateTimeException`.
- Binding a text component to a property makes it undoable: user edits feed the view's `UndoStack`. Check-box and combo-box bindings don't.

```java
private static final int MIN_FONT_SIZE = 6;
private static final int MAX_FONT_SIZE = 72;
…

@Override
public JComponent content() {
    form.bind(fontSizeField, fontSize, Converter.of(Integer::valueOf, String::valueOf, messages.wholeNumber()))
            .check(Rule.of(
                    messages.fontSizeRange(MIN_FONT_SIZE, MAX_FONT_SIZE),
                    (Integer size) -> size >= MIN_FONT_SIZE && size <= MAX_FONT_SIZE));
    // A check box carries its own text ([wordWrapBox] text = "&Wrap lines"), so it takes a wide row.
    form.bind(wordWrapBox, wordWrap);
    return FormPanel.create().row(fontSizeLabel, fontSizeField).wide(wordWrapBox).build();
}
```

`FormPanel`: `create()`, `row(JLabel label, JComponent field)` (sets `labelFor`, which gives the field its accessible name), `wide(JComponent)`, `section(JLabel title)`, `build()` (once).

Enable a command from the form: `@Exposed final Observable<Boolean> canSave;` assigned `form.dirty()` in the constructor, and `@Command(enabled = "canSave")`.

---

## 13. Tasks and concurrency

```java
Task.of(Callable<T>)  Task.of(Runnable)  Task.of(TaskWork<T>)            // TaskWork: T run(TaskContext context) throws Exception
Task.of(PublishingWork<T, P> work, Consumer<? super List<P>> onPublish)  // partial results, delivered on the EDT in batches
                                                                          // PublishingWork: T run(TaskContext context, Consumer<? super P> publish) throws Exception
Task.none()
task.onSuccess(Consumer<T>).onFailure(Consumer<Throwable>).onCancel(Runnable).onEnd(Runnable)
task.then(Function<T, Task<R>> next)                                      // a chain
```

- The work runs on a **virtual thread**; hooks, progress and published batches run on the EDT. Read component state on the EDT before creating the task, and pass values in.
- `TaskContext`: `progress(double)` (0–1, or -1 for indeterminate), `message(String)`, `isCancelled()`. Cancelling interrupts the thread; a cancelled task runs `onCancel` and `onEnd`, never `onSuccess` or `onFailure`.
- A failure without `onFailure` goes to the `FailureHandler` (an error dialog). Closing the task's scope cancels it. A task runs once.

```java
@Command(enabled = "canSave", block = Block.WINDOW)
Task<Path> save() {
    String text = store.text().get();                      // read on the EDT
    return Task.of(() -> store.save(text))
            .onSuccess(file -> {
                form.markClean();
                status.setText(messages.saved(file));
            });
}
```

| `Block` | While the task runs |
|---|---|
| `NONE` | nothing is blocked |
| `COMMAND` | the command is disabled |
| `COMPONENT` | the component it was activated from is disabled (the button, the popup menu's invoker, the focus owner for a shortcut); for other triggers, the command |
| `WINDOW` | the window is covered; after 300 ms, a progress indicator with Cancel |
| `APPLICATION` | every window is covered; the taskbar shows the progress |

| `Concurrency` | A new activation while a task runs |
|---|---|
| `PARALLEL` | runs at once |
| `RESTART` | cancels the running task, then runs |
| `SERIAL` | waits for the running tasks |

`RESTART` and `SERIAL` exclude `block = Block.COMMAND` and `Block.COMPONENT` (a compile error): a command that disables itself is never activated twice.

Outside commands: `ui.run(task, Block.WINDOW)` in a view; in a bean with no `Ui`, inject `TaskService` and `Scope` and call `taskService.run(task, scope)`, which blocks nothing (closing the scope cancels the task). Periodic work: `ui.every(Duration.ofSeconds(1), action)`. To reach the EDT from core code, inject `UiExecutor` (`execute(Runnable)`, `isUiThread()`).

---

## 14. Events

The application's event bus is gaia's `EventBus` (`be.imgn.gaia.core.event`), injectable. Events are records; a subscriber of a supertype receives its subtypes.

```java
bus.publish(new ContactSelected(contact));                // any thread

@Subscribe                                                // be.imgn.gaia.core.event: on the publisher's thread
void showDetail(ContactSelected event) { … }

@OnEdt                                                    // be.imgn.swing.ui: on the EDT, in publish order
void onImportProgress(ImportProgress event) { … }

@OnVirtualThread                                          // be.imgn.gaia.core.event: one virtual thread per delivery
void onCounted(Counted event) { … }
```

`@OnVirtualThread` deliveries run in no particular order, and the same method may run concurrently: its bean must be thread-safe.

- Subscriber methods: not private or static, one parameter of a plain class type, return `void`, no checked exception, one subscribing annotation.
- The declaring class is scoped (`@ViewScoped`, `@WindowScoped`, or `@Singleton` **and** `@Eager`). The framework registers beans automatically when it builds them; never register them yourself. Subscriptions end with the bean's scope.
- In code: `scope.onClose(bus.subscribe(ContactSelected.class, this::show))`. `bus.subscribe` returns gaia's `be.imgn.gaia.core.event.Subscription`, not `be.imgn.swing.Subscription`.
- An exception in a subscriber goes to the `FailureHandler`; the other subscribers still receive the event.

---

## 15. Dependency injection, scopes and lifecycle

Injection is `be.imgn.inject`: compile-time, no reflection.

| Annotation | Use |
|---|---|
| `@Inject` | On the constructor (package-private is fine). Write `@Inject Foo() {}` even with no parameters |
| `@Singleton` | Application scope |
| `@WindowScoped`, `@ViewScoped` (`be.imgn.swing.ui`) | Per window, per view. `View` is `@ViewScoped`, so views need no annotation |
| `@Eager` | Built at startup on the main thread; required on singletons with subscribers; builds no component |
| `@Assisted` | Caller-supplied constructor parameter. For dialog and frame views, the processor generates `<View>Factory` (method `create(...)`); for any other bean, write an `@AssistedFactory` interface yourself |
| `@IntoSet(X.class)` | Contributes the bean to `Set<X>` **instead of** binding it under its own type |
| `@Provides` (+ `@Singleton`, `@Default`) | `static` factory methods in a `final class XBeans { private XBeans() {} … }` |
| `@ExposeAs(X.class)` | Narrows the types a bean is bound under (by default its type and all its supertypes) |
| `@PostConstruct`, `@PreDestroy` | Lifecycle methods: non-private, non-static, no parameter, `void`, at most one of each per class; `@PreDestroy` only on a scoped bean (never a prototype), run when its scope closes |
| `Provider<T>` | Lazy access; the only way for a wider scope to reach a narrower one |

Unannotated classes are prototypes. Everywhere injectable: `ApplicationInfo`, `Arguments`, `DisplayLocale`, `Scope`, `EventBus`, `Lifecycle`, `TaskService`, `SettingsStore`, `Resources`, `Locales`, `Appearances`, `UiExecutor`, `FailureHandler`, `Frames`, `Keymap`, `SystemShell`, `TaskbarStatus`, gaia's `Platform` and `ApplicationDirectories`, and with `swing-desktop` `Documents`, `RecentFiles` and `Notifications`. In windows and views: `Window`, `Dialogs`, and in views: `Ui`, `Form`, `UndoStack`, `StandardCommands`, `StandardMenus`, `CommandPalette`, the generated `<X>Commands` and `<X>Menus`.

Lifecycle hooks, contributed with `@IntoSet`:

| Interface | When |
|---|---|
| `StartupStep` (`int order(); void run();`) | Main thread, off the EDT, before any window; no Swing. Framework steps: single instance 10 (desktop), locale 50, appearance 90 (FlatLaf), resources 100, settings 150, window state 200, recent files 250 (desktop), keymap 260, OS open-file events 1000 (desktop). Equal orders run by class name. A step that reads settings uses an order above 150 |
| `ReadyListener` (`void ready();`) | EDT, once the first window is shown |
| `ExitVeto` (`boolean canExit();`) | EDT, before exit; also asked automatically for window- and view-scoped beans |
| `ShutdownStep` (`int order(); void run();`) | After the vetoes pass and tasks end |

```java
@Singleton
@IntoSet(StartupStep.class)
static final class Loading implements StartupStep {
    private static final int ORDER = 300;

    private final NoteStore store;

    @Inject
    Loading(NoteStore store) { this.store = store; }

    @Override
    public int order() { return ORDER; }

    @Override
    public void run() { store.load(); }      // off the EDT
}
```

Because `@IntoSet` replaces the bean's own binding, a contributed step is its own small class (as above), not the service itself. `Lifecycle.requestExit()` exits from any thread, asking the vetoes.

---

## 16. Settings, files and window state

### Settings

```java
@Settings("editor")
@TomlConfig
public record EditorSettings(int fontSize, boolean wordWrap) {
    private static final int DEFAULT_FONT_SIZE = 14;

    /** Creates the defaults. */
    public EditorSettings() {
        this(DEFAULT_FONT_SIZE, true);
    }
}

Property<EditorSettings> settings = settingsStore.get(EditorSettings.class);
settings.set(new EditorSettings(16, false));    // saved in the background
```

- A record, annotated `@Settings("<kebab-case-id>")` (`be.imgn.swing`) and `@TomlConfig` (`be.imgn.toml`), with a public no-argument constructor giving the defaults. `locale` and `keymap` are reserved ids.
- Saved as `<config dir>/<id>.toml`, keys in kebab case (`font-size`). Missing keys take their defaults; an invalid file gives the defaults and is moved to `<id>.toml.invalid`.
- `SettingsStore.get` throws `IllegalStateException` until the settings step (order 150) has run: a startup step that reads settings uses an order above 150.

### Application files

Write data under gaia's `ApplicationDirectories` (`config()`, `data()`, `state()`, `cache()`), atomically with gaia's `AtomicFiles`, in a task or a startup step, never on the EDT.

### Window state

Saved automatically in `window-state.toml` in the state directory: each window's bounds, each frame's maximized state, the last directory of its file choosers, and, for **`@Exposed`** fields only, the divider location of a `JSplitPane`, the selected tab of a `JTabbedPane` and the column order and widths of a `JTable`. A component in a private field keeps no state.

---

## 17. Desktop integration and FlatLaf

### Documents (`swing-desktop`)

A frame view that edits a file implements `DocumentView<T>`:

```java
public final class MemoView implements FrameView, DocumentView<String> {
    @Exposed final JTextArea editor = new JTextArea();     // [editor] accessible-name = "Note text"
    private final Property<String> text = Property.of("");

    @Override
    public JComponent content() {
        form.bind(editor, text);
        …
    }

    @Override
    public DocumentFormat<String> format() {
        return DocumentFormat.text(new FileType(messages.textFiles(), List.of("txt")));
    }

    @Override
    public String toSave() { return text.get(); }              // EDT, before writing

    @Override
    public void loaded(String contents) { text.set(contents); } // EDT, after reading

    @Override
    public void saved(Path file) { … }                          // optional
}
```

- **Modified means `Form.dirty()`**: bind all content through the view's `Form` (or `form.track(...)`), or edits never mark the document modified.
- `DocumentFormat<T>`: `fileType()`, `empty()`, `read(InputStream)`, `write(T, OutputStream)` (on a task thread, touching no component); `DocumentFormat.text(FileType)` for UTF-8 text.
- Inject `DocumentCommands` (it extends `StandardCommands`) for `newDocument()`, `open()`, `save()`, `saveAs()` and `recentFiles()` (a menu entry); texts and shortcuts are provided.
- `Document` (view-scoped): `file()`, `name()` ("Untitled", "Untitled 2"…), `modified()`. The frame title is "{document} – {application}", preceded by `*` while the document is modified (on macOS, the window's modified dot instead); **a document view may not have a `title` resource**.
- A `SingleFrame` application class may implement `DocumentView<T>` too: `Documents.create`/`open` then reuse its main frame, after the save prompt when it is modified.
- `Documents` (singleton): `create(Class<V>)`, `open(Class<V>, Path)`. Reading and writing run in `Block.WINDOW` tasks; failures show an error dialog. Closing or exiting with a modified document asks to save.
- `RecentFiles` (singleton): `files()` (an `ObservableList<Path>`, most recent first, at most 10), `add`, `remove`, `clear`; fed by `Documents`; saved in `recent-files.toml` in the state directory.

```java
@Application(name = "Memo", vendor = "example", qualifier = "com", version = "1.0")
public final class MemoApp implements MultiFrame {
    private final Documents documents;
    private final FileSystem fileSystem;

    @Inject
    MemoApp(Documents documents, ApplicationDirectories directories) {
        this.documents = documents;
        this.fileSystem = directories.data().getFileSystem();
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
```

Resolve paths on the application directories' file system, never with `Path.of`: under `AppTester`, it is in memory.

### Other desktop services

- **Single instance**: a `@Singleton` bean implementing `SecondInstanceHandler` (`void launched(Arguments arguments)`, on the EDT) makes the application single-instance; a second launch forwards its arguments and exits.
- **Files opened from the OS** (Finder, Dock): a `@Singleton` `OpenFilesHandler` (`void open(List<Path> files)`, on the EDT).
- **Notifications**: inject `Notifications` (`be.imgn.swing.desktop`): `notify(String caption, String text)`, any thread; `isSupported()` (without a system tray, a notification is only logged).
- In `swing-ui`: `SystemShell` (`browse(URI)`, `open(Path)`, `canBrowse()`, `canOpen()`), `TaskbarStatus` (`progress(int percent)` from 0 to 100, `clearProgress()`, `badge(String)`, `clearBadge()`, `requestAttention()`, any thread), `UndoStack` (EDT only; `add(UndoableEdit)`, `canUndo()` and `canRedo()` as `Observable<Boolean>`, `undo()`, `redo()`, `clear()`).

### FlatLaf

Add `swing-flatlaf` as a runtime dependency; there is no code to write. It installs FlatLaf, follows the system's dark mode (`theme = "system"`), shows validation errors as outlines and loads `.svg` icons.

```toml
# App.toml (application class only)
theme = "system"

[flatlaf]
"@accentColor" = "#1a73e8"

# App.dark.toml
[flatlaf]
"@accentColor" = "#8ab4f8"
```

Token keys are FlatLaf's (`@accentColor`, `Component.arc`…); values are strings, integers or booleans. Keys aren't checked: a misspelled key does nothing.

### Keymap and command palette

- The command palette (`shortcut shift P`) lists the enabled commands of the window with their shortcuts, searched by text. Commands run from it get `Trigger.Palette`.
- The keymap dialog (`StandardCommands.keymap()`) lets the user change shortcuts, saved in `keymap.toml` in the config directory. `Keymap` (singleton): `shortcut(cmd)`, `defaultShortcut(cmd)`, `assign(cmd, KeyStroke)`, `remove(cmd)`, `reset(cmd)`, `resetAll()`.

---

## 18. Testing

`AppTester` (`be.imgn.swing.testing`) runs the real application headless, on an in-memory file system, with fake dialogs. Always use try-with-resources. The platform is a fake Linux: `shortcut` means Ctrl, mnemonics are set, and `X.linux.toml` files are read. The appearance starts `LIGHT`, whatever the `theme`.

```java
@Test
void addsATaskThroughTheDialog() {
    try (AppTester<TodoApp> app = AppTester.start(TodoApp.class)) {
        app.dialogs().answerDialog(ItemDialog.class, dialog -> {          // runs on the EDT when the dialog shows
            dialog.component(JTextField.class, "Title").setText("Write the docs");
            dialog.commands(StandardCommands.class).ok().execute();
        });

        app.commands(TodoAppCommands.class).add().execute();

        JTable table = app.component(JTable.class);
        assertThat(app.call(() -> table.getValueAt(0, 0))).isEqualTo("Write the docs");
    }
}
```

### Starting

| Call | Notes |
|---|---|
| `AppTester.start(App.class)` | `Locale.ROOT`: reads the base TOML files and their `.linux.toml` variants |
| `AppTester.start(App.class, Locale.FRENCH)` | |
| `AppTester.builder(App.class)` | `.locale(…)`, `.arguments(…)`, `.settings(record)` (pre-saved settings), `.file(dirs -> dirs.data().resolve("notes.txt"), "content")`, `.fileSystem(fs)` (reuse a file system to test a restart; the tester doesn't close it, the test does), `.start()` |

### Acting and reading

| Method | Notes |
|---|---|
| `run(Runnable)`, `call(Supplier<R>)` | Run on the EDT, then settle. The only way to touch components outside a dialog's answer. `call`'s action must not return `null` |
| `commands(Class<C>)`, `bean(Class<B>)` | Beans of the main view's scope (in a multi-frame app, the active frame's) |
| `application()` | The main view (or the `MultiFrame` singleton) |
| `info()` | The `ApplicationInfo`; `info().directories()` are the fake directories, on the in-memory file system |
| `component(Class<C>)`, `component(Class<C>, String accessibleName)`, `components(Class<C>)` | Find components in the main window; prefer the accessible name |
| `title()`, `focused()`, `iconImage()` | `focused()` is the last component passed to `Window.focus` (an `Optional<Component>`), not the focus owner; `iconImage()` is an `Optional<Image>` |
| `frames()`, `frame(Class<V>)` → `OpenFrame<V>` | `view()`, `commands`, `bean`, `component`, `components`, `title()`, `iconImage()`, `focused()`, `toFrontCount()`, `close()` (→ `boolean`), `isOpen()` |
| `locale(Locale)`, `appearance(Appearance)` | Switch, refresh, settle |
| `publish(Object)`, `published(Class<E>)` | Publish an event; read the events published so far |
| `activate(BoundCommand)` | Runs a command without waiting for its task (use a latch to observe the running state) |
| `ticker().advance(Duration)` | Moves the fake clock (gaia's `FakeTicker`). Due `ui.every` actions run at the next tester call, at most once each: advance one period at a time, with a call such as `app.run(() -> {})` after each |
| `inspect(Component)` | `Inspection(type, view, field, command)` |
| `isRunning()`, `exitStatus()` | `exit()` doesn't stop the JVM |
| `launchAgain(String...)`, `openFiles(Path...)` | `swing-desktop` handlers |

`BoundCommand.execute()` from the test thread runs the command on the EDT, then **settles**: it waits for the tasks of commands, the `@OnVirtualThread` deliveries and the pending settings saves. Tasks started outside commands (`ui.run`, `TaskService`) are not awaited. Inside an `answerDialog` action, which runs on the EDT, `execute()` runs at once without settling: call `dialog.settle()` before reading state that a task or a posted update changes.

### Fakes

- `app.dialogs()` (`FakeDialogs`):
  - `answerConfirm(boolean)` sets the answer of every later confirmation (default yes).
  - `answerDialog(Class<V>, Consumer<OpenDialog<V>>)`: an unanswered or left-open dialog is cancelled.
  - Each of these queues one answer: `answerOpenFile(Path)` and `answerSaveFile(Path)` (one shared queue; a file chooser with nothing queued returns empty; a save to an existing file asks a confirmation), `cancelFileChooser()`, `answerSave(SaveChoice)` (default `DISCARD`), `answerCommand(String query)`, `cancelCommand()`.
  - Recorded: `confirmed()`, `informed()`, `errors()`, `shown()`, `fileChoices()`, `savePrompts()`, `commandQueries()`.
- `OpenDialog<V>`, inside an answer (on the EDT): `view()`, `commands(Class)`, `bean(Class)`, `component(...)`, `components(Class)`, `isOpen()`, `focused()`, `settle()` (run the posted updates before reading state that depends on them).
- `app.shell()` (`browsed()`, `opened()`, `supportBrowsing(boolean)`), `app.taskbar()` (`progress()` → `OptionalInt`, `badge()` → `Optional<String>`, `attentionRequests()`), `app.notifications()` (`notified()` → `List<Notification>`, a record of `caption` and `text`; needs `swing-desktop`).

### What fails a test automatically

The tester's next call throws `AssertionError` when any of these happened: a component repainted or invalidated off the EDT; an exception reaching the `FailureHandler` or uncaught on any thread (a handler exception, a task failure with no `onFailure`); a strict-mode mistake (duplicate mnemonics, shortcut collisions, **a focusable component with no accessible name**, a mnemonic label with no `labelFor`); duplicate mnemonics in a dialog; a command task or a settings save that doesn't end within 30 seconds of a settling call. A startup failure makes `start` throw; a class with no registered descriptor makes it throw `IllegalArgumentException`.

---

## 19. Build errors and their fixes

Resource messages are prefixed `File.toml:line:col:` and attached to the Java element.

| Message (gist) | Fix |
|---|---|
| `must implement exactly one of …SingleFrame and …MultiFrame` | Implement exactly one |
| `@Application must annotate a class that is not abstract` / `A qualifier is only given with a vendor` / `An application class that implements …MultiFrame is a singleton: remove @WindowScoped` | Fix the annotation or the class |
| `A view class must not be private` | Make it package-private or public |
| `X declares commands but has no scope` / `declares subscribers but has no scope` | Annotate `@ViewScoped`, `@WindowScoped` or `@Singleton` |
| `X is a @Singleton that declares subscribers but is not @Eager` | Add `@Eager` on the class |
| `@Singleton X depends directly on @ViewScoped Y` | Inject `Provider<Y>` |
| `The handler/field of a command must be neither private nor static` | Make it package-private |
| `The handler of a command returns void or Task<?>` / `takes no parameter, or a single CommandContext` | Fix the signature |
| `block = … applies only to a handler that returns a Task` (same for `concurrency`) | Return a `Task` or drop the attribute |
| `concurrency = Concurrency.RESTART excludes block = Block.COMMAND` (or `SERIAL`, or `COMPONENT`) | Use `PARALLEL`, or block `NONE`, `WINDOW` or `APPLICATION` |
| `cancels = "x" names no command of X` / `names the command x, whose handler returns void` / `The handler of a stop command returns void` / `A stop command takes no … attribute` | A stop command is a `void` handler with only `cancels`, naming a `Task`-returning command of the same class |
| `A command on a field makes it a toggle, so the field must be a Property<Boolean>` / `it takes no selected attribute` / `it takes no block attribute` | Declare a `Property<Boolean>` field with only `id` or `enabled`, or use a handler method |
| `The handler h is named like the standard command h but its id is x` | Rename the handler |
| ``Command `x` of `X` overrides the standard command `x` `` (warning, an error under `-Werror`) | Use a different id |
| `enabled = "x" names no field or no-argument method` / `names a field that is not @Exposed` / `must be of type Observable<Boolean>` | Add an `@Exposed` field or a non-private no-arg method of the right type |
| `enabled = "x" names a private member` / `… is in another package, so it must be public` (same for `selected`) | Make the target package-private (same package) or public |
| `The command x has no text in X.toml` | Add `[commands.x] text = "…"` |
| `[commands.x] names no command of …` | Fix the id or declare the command |
| `k is not a property of a command` | Use `text`, `icon`, `shortcut`, `tool-tip-text`, `accessible-name`, `accessible-description` |
| `The shortcut "…" does not parse as a key stroke` | `KeyStroke` syntax: `"shortcut shift S"` |
| `The commands a and b have the same shortcut` | Change one |
| `The text "…" has more than one & mnemonic` | One `&`; `&&` for a literal ampersand |
| `The icon p names no resource of the package` | Add the file or fix the path |
| `[f] names no @Exposed component field of X` | Add `@Exposed` to the field (package-private) |
| `An @Exposed field must be neither private nor static` / `must be a java.awt.Component or an Observable<Boolean>` | Fix the field |
| `k is not a supported property of T` / `must be a boolean/integer/string` / `The color "…" is not "#rrggbb"` | See [component properties](#component-properties) |
| `k is not a property of X: name, theme, … apply to the application class, title to frame views and dialog views` | Move the key to the right class |
| `The theme "…" is none of system, light or dark` / `The website "…" is not an absolute http or https URI` | Fix the value |
| `[variants] needs an enum annotated @Variants` / `[variants.x] names no constant` / `The variant x has no table [variants.x]` / `A variant holds no commands` (or `menus`, `variants`, `flatlaf`) / `A view has one @Variants enum` | One table per constant in the base file, holding class properties and component tables only |
| `X.toml belongs to no class the processor generates for` (warning, an error under `-Werror`) | Rename or delete the file |
| `X_fr.toml lacks the key a.b` | Copy every base key into the language file |
| `The key a.b is not in the base file X.toml` | Add it to the base file or remove it |
| `X_fr.dark.toml is a dark variant with a locale or a platform part` | Only `X.dark.toml` |
| `The placeholder {p} names no parameter` / `does not parse as a MessageFormat` | Name a parameter; quote apostrophes with `''` |
| `@Messages … have no file X.toml next to them` / `The message m has no pattern` / `The key k names no method` | Align the TOML keys with the methods |
| `A message method returns String` | Only `String` methods |
| `@Messages must annotate an interface` / `A @Messages interface must not be private` / `A message method has no type parameter` / `Two message methods are named m` / `X.dark.toml is a dark variant of messages` | Fix the interface; delete the dark file |
| `@Settings must annotate a record` / `must also be annotated @TomlConfig` / `must have a public constructor with no parameter` / `is not lower-case kebab case` | Fix the settings record |
| `The settings id "locale" belongs to the framework` (same for `keymap`) / `The settings records A and B have the same id x` | Pick another id |
| `The menu id x does not convert to the name of a method` | Rename the `[menus.x]` id |
| `The menu x has no text in the base file` | Add `[menus.x] text = "…"` |
| `A [flatlaf] table is allowed in the resources of the @Application class only` | Move it |
| `A document view is shown in a frame of its own: implement FrameView` | Implement `FrameView` too |
| `Another class of the package also generates p.XCommands` / `…already exists` | Rename your class |
| `warnings found and -Werror specified` | Read the warnings above it: Javadoc (`MissingSummary`), lint, or a processor warning |
| Runtime: `IllegalArgumentException: No application descriptor for X: annotate it with @Application, compile it with swing's processor, and on the module path, register the services with gaia's register-services goal` (thrown by `Swing.launch` and `AppTester.start`) | Add the `inject-maven-plugin` and `gaia-maven-plugin` executions, or the processors |
| Runtime: `… has no accessible name: give it a label whose labelFor it is, or an accessible-name resource` | Add a label with `labelFor` or an `accessible-name` resource |

---

## 20. Complete example

A to-do list: a table of tasks with an editable "done" column, a dialog with a validated field, an Add and a Remove command, a status line that follows the locale, English and French. It compiles under every check of the build and its tests pass, as written. Package `com.example.todo`, with a `package-info.java` annotated `@NullMarked`.

`Item.java`:

```java
package com.example.todo;

import java.util.UUID;

/**
 * A task of the list.
 *
 * @param id its identity, kept across edits
 * @param title what to do
 * @param done whether it is done
 */
public record Item(UUID id, String title, boolean done) {

    Item withDone(boolean value) {
        return new Item(id, title, value);
    }
}
```

`TodoMessages.java`:

```java
package com.example.todo;

import be.imgn.swing.Messages;

/** The texts the code builds. */
@Messages
public interface TodoMessages {

    /**
     * Returns the header of the title column.
     *
     * @return the header
     */
    String titleHeader();

    /**
     * Returns the header of the done column.
     *
     * @return the header
     */
    String doneHeader();

    /**
     * Returns the requirement of the title field.
     *
     * @return the requirement
     */
    String titleRequired();

    /**
     * Returns the status line.
     *
     * @param count the number of tasks
     * @return the status line
     */
    String count(int count);
}
```

`ItemDialog.java`:

```java
package com.example.todo;

import be.imgn.inject.Inject;
import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Asks for the title of a new task. */
public final class ItemDialog implements DialogView<String> {

    @Exposed
    final JLabel titleLabel = new JLabel();

    private final JTextField titleField = new JTextField(20);
    private final Property<String> title = Property.of("");
    private final Form form;
    private final TodoMessages messages;

    @Inject
    ItemDialog(Form form, TodoMessages messages) {
        this.form = form;
        this.messages = messages;
    }

    @Override
    public JComponent content() {
        form.bind(titleField, title).check(Rule.required(messages.titleRequired()));
        return FormPanel.create().row(titleLabel, titleField).build();
    }

    @Override
    public Optional<String> result() {
        return Optional.of(title.get().strip());
    }
}
```

`TodoApp.java`:

```java
package com.example.todo;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Observable;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.ui.Column;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/** A to-do list: a table of tasks, a dialog to add one, a command to remove the selected one. */
@Application(name = "Todo", vendor = "example", qualifier = "com", version = "1.0")
public final class TodoApp implements SingleFrame {

    @Exposed
    final JTable table = new JTable();

    private final JLabel status = new JLabel();
    private final ObservableList<Item> items = ObservableList.of();
    private final Property<Optional<Item>> selected = Property.of(Optional.empty());
    private final TodoMessages messages;
    private final TodoAppCommands commands;
    private final Ui ui;

    @Inject
    TodoApp(TodoMessages messages, TodoAppCommands commands, Ui ui) {
        this.messages = messages;
        this.commands = commands;
        this.ui = ui;
    }

    /**
     * Launches the application.
     *
     * @param args the command line
     */
    public static void main(String[] args) {
        Swing.launch(TodoApp.class, args);
    }

    @Override
    public JComponent content() {
        ui.bind(
                table,
                items,
                List.of(
                        Column.of(messages::titleHeader, String.class, Item::title),
                        Column.of(messages::doneHeader, Boolean.class, Item::done)
                                .editable(Item::withDone)),
                selected,
                Item::id);
        ui.render(() -> status.setText(messages.count(items.size())), items);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(
                ui.menus().file().with(commands.exit()),
                ui.menus().edit().with(commands.add(), commands.remove()));
    }

    Observable<Boolean> hasSelection() {
        return selected.map(Optional::isPresent);
    }

    @Command
    void add() {
        ui.showDialog(ItemDialog.class).ifPresent(title -> items.add(new Item(UUID.randomUUID(), title, false)));
    }

    @Command(enabled = "hasSelection")
    void remove() {
        selected.get().ifPresent(items::remove);
    }
}
```

Resources, in `src/main/resources/com/example/todo/`:

```toml
# TodoApp.toml
title = "To Do"

[commands.add]
text = "&Add…"
shortcut = "shortcut N"

[commands.remove]
text = "&Remove"
shortcut = "DELETE"

[table]
accessible-name = "Tasks"
```

```toml
# TodoApp_fr.toml: every key of the base file, shortcuts included
title = "À faire"

[commands.add]
text = "&Ajouter…"
shortcut = "shortcut N"

[commands.remove]
text = "&Supprimer"
shortcut = "DELETE"

[table]
accessible-name = "Tâches"
```

```toml
# TodoMessages.toml
titleHeader = "Title"
doneHeader = "Done"
titleRequired = "A title is required"
count = "{count,choice,0#No tasks|1#One task|1<{count,number,integer} tasks}"
```

```toml
# TodoMessages_fr.toml
titleHeader = "Titre"
doneHeader = "Fait"
titleRequired = "Un titre est requis"
count = "{count,choice,0#Aucune tâche|1#Une tâche|1<{count,number,integer} tâches}"
```

```toml
# ItemDialog.toml
title = "New Task"

[titleLabel]
text = "&Title"
```

```toml
# ItemDialog_fr.toml
title = "Nouvelle tâche"

[titleLabel]
text = "&Titre"
```

`TodoAppTest.java`, in `src/test/java/com/example/todo/`:

```java
package com.example.todo;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class TodoAppTest {

    @Test
    void addsATaskThroughTheDialog() {
        try (AppTester<TodoApp> app = AppTester.start(TodoApp.class)) {
            app.dialogs().answerDialog(ItemDialog.class, dialog -> {
                dialog.component(JTextField.class, "Title").setText("Write the docs");
                dialog.commands(StandardCommands.class).ok().execute();
            });

            app.commands(TodoAppCommands.class).add().execute();

            JTable table = app.component(JTable.class);
            assertThat(app.call(() -> table.getValueAt(0, 0))).isEqualTo("Write the docs");
            assertThat(app.call(() -> app.application().table.getRowCount())).isEqualTo(1);
        }
    }

    @Test
    void anEmptyTitleKeepsTheDialogOpen() {
        try (AppTester<TodoApp> app = AppTester.start(TodoApp.class)) {
            app.dialogs().answerDialog(ItemDialog.class, dialog -> {
                JTextField title = dialog.component(JTextField.class, "Title");
                dialog.commands(StandardCommands.class).ok().execute();
                assertThat(dialog.isOpen()).isTrue();
                assertThat(title.getToolTipText()).isEqualTo("A title is required");
            });

            app.commands(TodoAppCommands.class).add().execute();

            assertThat(app.dialogs().shown()).containsExactly(ItemDialog.class);
        }
    }

    @Test
    void removeIsDisabledWithoutASelection() {
        try (AppTester<TodoApp> app = AppTester.start(TodoApp.class)) {
            assertThat(app.commands(TodoAppCommands.class).remove().execute()).isFalse();
        }
    }

    @Test
    void followsTheLocale() {
        try (AppTester<TodoApp> app = AppTester.start(TodoApp.class, Locale.ENGLISH)) {
            app.locale(Locale.FRENCH);

            assertThat(app.title()).isEqualTo("À faire");
            JLabel status = app.components(JLabel.class).getLast();
            assertThat(app.call(status::getText)).isEqualTo("Aucune tâche");
        }
    }
}
```

What the example shows: the title column's header and the status line follow the locale (a `Supplier` header and `ui.render`); the table has an accessible name and the dialog's field gets its name from its label (`FormPanel.row`); `remove` is enabled by a method returning `Observable<Boolean>`; the dialog's `ok()` validates the form and keeps the dialog open on an empty title; every TOML key of a base file is repeated in its French file.
