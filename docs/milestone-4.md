# Milestone 4: requirements

This is the requirement document of the polish milestone, described in the [vision](vision.md#milestone-4-polish): the command palette, the remappable keymap, hot reload of resources, the inspector overlay and accessibility checks. It also delivers what milestones [1](milestone-1.md#scope) and [2](milestone-2.md#scope) left for it: live locale switching, with the ports of SwingAppFramework's `LocaleExample3` and `LocaleExample4` and the Address Book's language menu, and toolbars that don't float. It takes up the [idea](ideas.md#14-tab-titles) that the ports suggested for it, tab titles. And it schedules three items that no milestone had yet: following the system's light or dark setting while running, with theme tokens; a Window menu of the open frames, which [milestone 3](milestone-3.md#scope) left for later; and the vision's EDT adapters, which [milestone 1](milestone-1.md#changes-to-the-vision) removed from the vision but left to drop or to schedule, and which milestone 4 drops for good. The release is accepted when the [acceptance](#acceptance) examples and their tests pass.

Every part of every sentence in this document is a requirement, and must be followed exactly: names, packages, signatures, behaviors, orders and error cases. Any deviation, whether a part that can't be implemented as written, a part that contradicts another, or a gap, is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text.

Milestone 4 builds on milestones 1 to 3 as implemented. Everything they specify stays, except where a section below changes it; [Changes to milestones 1 to 3](#changes-to-milestones-1-to-3) lists those changes.

---

## Scope

| In milestone 4 | Later |
|---|---|
| Changing the locale while running: resources, messages, commands, menus, titles and component orientation follow; the choice is saved | Texts that code passes as strings, such as the messages of validation rules and the descriptions of `FileType`s, following a change: they keep the locale they were made in |
| Messages in a locale other than the display locale | Per-component locales, as SwingAppFramework's `Locales.setLocale(component, locale)` |
| Following the system's light or dark setting while running, with resources per appearance and FlatLaf tokens in TOML | A theme the user chooses in the application; following the setting without FlatLaf |
| Hot reload of the TOML files in the class output, in development mode | Reloading icon files; reloading classes; watching source directories |
| The command palette | Commands of other windows in the palette; showing a command's menu path |
| A remappable keymap, saved, with its dialog | Key sequences (chords), several shortcuts per command, commands of views that aren't open in the keymap dialog |
| A Window menu of the open frames | Minimize and Zoom commands |
| Accessibility checks of the components of views, in development mode and tests | Checks of contrast and focus order |
| The inspector overlay, in development mode | Editing resources from the overlay |
| Toolbars that don't float; tab titles applied when a tab is added | — |
| The test harness for all of the above | — |

Milestone 4 adds no module. Core gets `Locales`, `Appearance`, `Appearances` and `MessagesDescriptor`, and `Resources` gets the appearance and the locale per call; `swing-ui` gets the refresh of open windows, `Ui.render`, `Ui.addTab`, the palette, the keymap and their dialogs, the Window menu, the accessibility checks and the inspector; `swing-flatlaf` follows the system and applies the tokens; `swing-desktop` follows the locale in its titles; `swing-processor` gets the checks below; `swing-testing` gets the harness additions. Core still doesn't read `java.desktop`.

The libraries are those of milestone 3: the parent 2026-11-01, gaia 0.1.2, inject 0.1.1 and toml 0.1.0, from Maven Central, and FlatLaf 3.7.2. Milestone 4 needs no upstream change (see [Upstream changes](#upstream-changes)).

---

## Refreshing open windows

Changing the locale, changing the appearance and reloading resources all end in one refresh of every open window, so that what resources gave is given again. A refresh runs on the EDT. For every open window, in the order the windows opened, steps 1 and 2 run for each view of the window, then each of steps 3 to 5 runs for every view of the window before the next step, since a window's shortcuts belong to all its views:

1. **Component resources.** The view's component resources are applied again from its new merged table, its variant included, as at the end of its build: every property but `enabled` and `visible`, which the view's code owns once it is built, then the `tab-title` properties. A property that can't be read or set, such as a value of the wrong type in a reloaded file, or a title on a border the code replaced, is logged at `WARNING`, naming the class, the field and the key, and skipped; the refresh goes on.
2. **Title.** While the window's title is still the one its `title` resource, else the displayed name, gave it, it is set again from the new resources. A title the view set itself, through `Window.setTitle` or a binding, stays. A document view's title follows its document as [milestone 3](milestone-3.md#title) describes, from the desktop's messages in the new locale, an untitled document taking its name in that locale. `Window` (UI) gains `String title()`, the title as last set, empty when none was, which the real windows read from their frame or dialog and the test harness's window records.
3. **Commands.** The shortcuts of the window's commands are removed. Then every command of each view's `CommandRegistry` takes its text, mnemonic, tool tip, icon and shortcut from its new resources, its shortcut from the [keymap](#keymap) first; a command whose resources can't be applied is logged at `WARNING` and skipped. The components it created take their accessible name and description again: the command holds them, strongly, until its view's scope closes, or until the scope of the menu section that holds one closes, such as a rebuilt dynamic section's; a name or description that the command gave and its resources no longer give goes back to the component's own, and one that something else gave stays. A toolbar button hides its text again while its command has an icon. Then the shortcuts of the window's commands are registered again, so that two commands that swap shortcuts never collide on the way.
4. **Menus.** Every menu built from a description takes its text and mnemonic again; every dynamic section is rebuilt from its observable's current value; each menu and menu bar is checked for duplicate mnemonics again.
5. **Code.** The headers of table columns given as suppliers are read again (see [Columns](#columns)), and every [`Ui.render`](#rendering-from-messages) update runs.


Then each window's root pane is revalidated and repainted. Windows are not resized.

- A refresh that follows a change of the locale, compared with the locale Swing and the windows took last, at startup or at a refresh, also applies the new locale's `ComponentOrientation` to each window's root pane, with `applyComponentOrientation`, which every new window gets too, once its view is built, from milestone 4 on.
- The framework's own dialogs, the message, confirmation and error dialogs, the file choosers and the save prompt, read their texts when they are shown: one shown after a change is in the new locale.
- Core reaches the UI's refresh through an internal bean, `be.imgn.swing.internal.Refresher`, whose core default does nothing and which the UI module replaces, as it replaces `UiExecutor`.
- `ComponentResources` (UI SPI) gains `static ComponentResources refreshing(TomlTable, Function<String, Icon>, boolean, Class<?> view)`, the resources step 1 applies, which give no `enabled` or `visible` and log a value that can't be read as a warning; `void apply(String field, String key, Runnable setter)`, through which the generated injectors set each property, which lets an exception propagate at the end of a build and logs it as a warning at a refresh; and `static boolean isTab(Component)`, which the generated injectors check before `setTabTitle` (see [Tab titles](#tab-titles)).

### Rendering from messages

A text that code computes from messages, such as a status label's `words(count)`, is computed again only when code computes it again. `Ui` (UI) gains:

```java
public void render(Runnable render, Observable<?>... sources);
```

`render` runs `render` on the EDT at once, then after every change of one of the sources, following the [delivery rule](milestone-1.md#delivery-to-the-edt) as the views' `Ui.bind` targets do, and at every refresh, at step 5. Its subscriptions belong to the view's scope.

```java
ui.render(() -> status.setText(messages.words(words.get())), words);
```

Code that only shows a value, with no text from messages, keeps `Ui.bind`.

### Columns

`Column` (UI) gains a factory whose header is read when the table is bound and again at every refresh:

```java
public static <E, V> Column<E, V> of(
        Supplier<String> header, Class<V> type, Function<? super E, ? extends @Nullable V> value);
```

`Column.of(messages::firstNameHeader, String.class, Contact::firstName)` follows the locale; milestone 2's `Column.of(String, ...)` keeps its header. `editable` keeps the column's header, whichever factory made it.

---

## Live locale switching

### Locales

```java
public interface Locales {                                             // core, singleton
    Observable<Locale> current();
    void set(Locale locale);
    void reset();
    <M> M messages(Class<M> type, Locale locale);
}
```

- `current()` is the display locale. It starts as the [saved choice](#the-saved-choice), else the launch's `DisplayLocale`, which `Swing.launch` and `AppTester` bind as in milestone 1. Nothing in the framework reads `DisplayLocale` but `Locales`, and the state of `Resources`, which starts in it before `Locales` exists.
- `set(locale)` may be called from any thread, and hands the change to the `UiExecutor`, as `Lifecycle.requestExit` does. There, a locale equal to the current one does nothing. Otherwise, in this order: `Resources` switches to the new locale and drops its cached tables; the UI [refreshes](#refreshing-open-windows); `current()` takes the new locale, whose listeners run on that thread and whose EDT deliveries follow; the choice is saved. `set` throws `NullPointerException` for `null`.
- `reset()` does what `set` does with the launch's `DisplayLocale`, and deletes the saved choice instead of saving it.
- `messages(type, locale)` returns an implementation of the `@Messages` interface that reads the files of that locale, whatever the display locale, such as a language menu that names each language in itself. It is cached per type and locale. It throws `IllegalArgumentException` for a type that is not a `@Messages` interface compiled by swing's processor.
- Core provides `Locales` as a singleton, with no UI: its `set` then runs at once on the calling thread, as core's `UiExecutor` does.

`Resources` (core) reads the current locale for each call of `table(type)`, and gains `TomlTable table(Class<?> type, Locale locale)`, which merges the files of the given locale, with the same cache. It also gains `Locale locale()`, the locale that `table(type)` reads now. A change of the locale drops the cached tables at once; within one locale, two calls of `table(type)` return the same instance.

### Messages follow the locale

A `$X$$Messages` formats each call with the patterns of the current locale. At its first call after a change of the locale, or after a [reload](#hot-reload-of-resources), it reads every pattern of the new tables at once, so that no call mixes the patterns of two locales. The framework's own texts, those of `swing-ui` and `swing-desktop`, follow the same rule. `ApplicationInfo.displayedName()` is the `name` resource of the current locale, else the name.

### The saved choice

`set` saves the locale to `locale.toml` in `ApplicationDirectories.config()`, as `locale = "fr-BE"`, its BCP 47 language tag, from the record `SavedLocale(String locale)` through toml's generated writer, atomically, in a task. The writes are ordered as those of [recent files](milestone-3.md#recent-files) are: one that would replace a newer choice writes nothing; a shutdown step of order 330 writes the last choice, since the shutdown cancels the tasks still running, as for recent files. A startup step of order 50, before the resources' step, reads it: a tag that `Locale.forLanguageTag` turns into a locale with a language sets the starting locale; a file that doesn't bind, or a tag with no language, is logged as a warning and ignored. A saved choice so wins over the JVM's display locale, `-Duser.language` included: it is the user's choice, made in the application.

The settings ids `locale` and `keymap` belong to the framework: a `@Settings` record with either is a compile error (see [Build-time checks](#build-time-checks)).

### Swing's own texts

Swing's own components read their texts, such as `JOptionPane`'s Yes and No and `JFileChooser`'s labels, from the default locale when they are created. The UI module sets `JComponent.setDefaultLocale` and `UIManager.getDefaults().setDefaultLocale` to the current locale at startup step 4, before any component is created, and at each change, before the refresh. `AppTester` sets neither, so that tests running in parallel don't share them.

---

## Appearance

### Appearances

```java
public enum Appearance { LIGHT, DARK }                                  // core

public interface Appearances {                                          // core, singleton
    Observable<Appearance> current();
}
```

- Core provides a `@Provides @Default` one that is always `LIGHT`, which applies with the system look and feel. `swing-flatlaf` provides one that replaces it, through inject's `@Default` rule.
- When the appearance changes, in this order, on the EDT: `Resources` switches to the new appearance and drops its cached tables; the look and feel is installed again (see [Following the system](#following-the-system)); the UI [refreshes](#refreshing-open-windows); `current()` takes the new appearance.
- Code that paints with its own colors follows `current()`, through `Ui.render` or `Ui.bind`.

### Resources per appearance

A class's resources gain a dark variant, read only while the appearance is `DARK`. For locale `fr_BE` on macOS, in the dark, these files are read, each one overriding the previous, where milestone 1's order had no step 6:

1. Resources of the superclass, recursively, merged the same way.
2. `NotesView.toml`
3. `NotesView.macos.toml`
4. `NotesView_fr.toml`, then `NotesView_fr.macos.toml`
5. `NotesView_fr_BE.toml`, then `NotesView_fr_BE.macos.toml`
6. `NotesView.dark.toml`

The dark variant holds colors, icons and tokens that read well on a dark background, never texts. It is read last, so that it wins over a language file, which holds every key of the base file, its colors included, as milestone 1's translations check requires. It has no locale or platform variant of its own, and `@Messages` files have none: texts don't change with the appearance.

```toml
# MemoView.toml
[status]
foreground = "#5f6368"

# MemoView.dark.toml
[status]
foreground = "#9aa0a6"
```

### Following the system

With `swing-flatlaf` and the theme `system` (milestone 1's `-Dbe.imgn.swing.theme` property, else the application's `theme` resource):

- **Detection.** A startup step of order 90, before the resources' step, reads the system's setting, as milestone 1 describes, off the EDT, and sets the starting appearance, so that the resources are read once, in the right variant. Startup step 4 installs FlatLaf in that appearance.
- **Following.** The setting is read again every 10 seconds, and when the application is activated, a window of the application becoming the active window while none was, so that a user who changes it in the system's settings sees it on coming back. Each read runs the system's command on a virtual thread, one at a time: a read asked for while one runs is dropped. The schedule, a periodic action of the UI, and the activation's listener belong to the application scope. Under `AppTester`, whose own `Appearances` replaces this one, nothing is read or scheduled. A result that differs from the current appearance changes it, on the EDT.
- **Installing.** Changing the appearance installs `FlatDarkLaf` or `FlatLightLaf` with `FlatLaf.setup`, with the [tokens](#flatlaf-tokens) of the new appearance, then calls `FlatLaf.updateUI()`, which updates every window's components, and gives Swing the display locale again, since the new look and feel's defaults start in the JVM's locale, before the refresh.

With the theme `light` or `dark`, the appearance is fixed, and nothing is read. The appearance is `DARK` with the theme `dark`, as with a dark system.

### FlatLaf tokens

The application class's resources may hold a `[flatlaf]` table of FlatLaf's UI defaults and variables, which FlatLaf's properties files would otherwise hold, and their dark values in its dark variant:

```toml
# MemoApp.toml
[flatlaf]
"@accentColor" = "#1a73e8"
"Component.arc" = 8

# MemoApp.dark.toml
[flatlaf]
"@accentColor" = "#8ab4f8"
```

- Each value is a string, an integer or a boolean, given to FlatLaf as the string FlatLaf's properties files would hold, `"8"` for `8`. FlatLaf parses it: colors, numbers, references to other keys and functions such as `lighten(@background,5%)`.
- The installer passes the table of the current appearance to `FlatLaf.setGlobalExtraDefaults` before each `FlatLaf.setup`, at startup and at each change. `FlatLafInstaller` (`swing-flatlaf`) gains `void install(Appearance appearance)`, which installs FlatLaf in that appearance with its tokens, and `Map<String, String> tokens()`, the table as those strings; its `install()` installs it in the appearance its theme selects, as in milestone 1. The `LookAndFeelInstaller` bean that `swing-flatlaf` provides installs it in the appearance of the `Appearances` bean.
- The processor checks the table's form only, since FlatLaf's keys are its own and its variables are the application's (see [Build-time checks](#build-time-checks)): a misspelled key changes nothing, and the build can't tell.
- The table tunes FlatLaf; code doesn't read it. A component's own colors per appearance go in a dark variant of its view's resources, which the build checks, so that code never looks a resource up by string.

---

## Hot reload of resources

In development mode (`-Dbe.imgn.swing.dev=true`), the framework reloads a TOML file when its copy in the class output changes, such as when the IDE or Maven copies an edited file to `target/classes`, and refreshes the open windows: edit a label, build, and see it in the running application.

- **Where.** `ResourceOpener` (core) gains `Optional<Path> directory()`: the package's directory in the class output, when the package's classes are loaded from a directory on the default file system, else, as from a JAR, empty. It is a default method that returns empty, so that a hand-written opener, such as the framework's own, needs no change, and its files are not watched. The generated `$$Resources` returns the parent of the `file:` URL of its own class file, `$$Resources.class.getResource("$$Resources.class")`. Packages whose opener gives no directory are not watched.
- **Watching.** A startup step of order 110, after the resources' step, starts a virtual thread that, every 500 ms, reads the names, last-modified times and sizes of the `.toml` files directly in each watched directory. A file that is added, removed or changed counts once two reads in a row agree on it, so that a copy in progress is not read. Polling behaves the same on every system, where `WatchService` polls on macOS. The thread belongs to the application scope.
- **Reloading.** The changed files are parsed. One that doesn't parse is logged at `WARNING`, with its file, line and column, and nothing is reloaded until it parses. It is logged once per version of it: it is parsed again only once it changes. A directory or a file that can't be read, such as while a clean build removes the class output, keeps the state the last read gave it, so that nothing reloads empty. Otherwise `Resources` drops its cached tables, the messages read their patterns again at their next call, and the UI [refreshes](#refreshing-open-windows), on the EDT; the reload is logged at `INFO`, naming the files.
- **What it doesn't do.** The processor's checks don't run: a reloaded file may break a rule that the next build reports. A new field, command or messages method needs a build and a restart. Icon files are not watched: an `icon` that names another path loads it, but a changed image isn't read again, since `IconCache` keeps it.
- **Tests.** Hot reload never runs under `AppTester`, which starts no watcher whatever the system properties say.

---

## Command palette

The command palette finds a command by typing part of its text, and runs it.

```java
@ViewScoped
public final class CommandPalette {                                     // UI
    public List<PaletteEntry> entries();
    public List<PaletteEntry> search(String query);
    public static List<PaletteEntry> search(List<PaletteEntry> entries, String query);
    public void show();
}

public record PaletteEntry(BoundCommand command, String text, @Nullable KeyStroke shortcut) {}   // UI
```

- **Command.** `StandardCommands` gains `commandPalette()`, which calls its view's `CommandPalette.show()`. Its text is `Command Pal&ette…` and its shortcut `shortcut shift P`, in English, and `Pa&lette de commandes…` in French, mnemonics that the standard commands of an Edit menu leave free, overridable as any standard command (`[commands.commandPalette]`). The framework asks for `commandPalette()` at the end of the build of every frame view, so that its shortcut works in every frame whether or not a menu shows it.
- **Entries.** `entries()` lists the commands that the views of the view's window created, enabled at the time of the call, but `commandPalette()` itself, each with its text without its mnemonic marker and the shortcut it has in the window: its [effective shortcut](#keymap), unless another command took it there. On macOS, the system actions are listed too, since the palette is not a menu.
- **Search.** `search(query)` compares the query and each entry's text after normalizing both: decomposed with `Normalizer.Form.NFD`, combining marks removed, lower-cased with `Locale.ROOT`, stripped. An entry matches when the query's characters appear in its text in order. Matches are ranked: the text starts with the query, then a word of it does, a word being a run of letters and digits, then the text contains the query, then the characters match only in order; within a rank, the commands run from the palette most recently first, then by text, with the `Collator` of the current locale. An empty query lists every entry, ranked from the second criterion on. The static `search(entries, query)` ranks a given list of entries, in the order of `entries()`, as `search(query)` does: what the palette's dialog shows as the user types, and what the test harness's answers choose from.
- **Showing.** `show()` asks `Dialogs.chooseCommand(entries())`; a chosen entry's command runs once the palette has closed and the focus has returned to the component that had it, in a later `invokeLater`, with `execute(CommandContext.of(new Trigger.Palette()))`, so that `copy()` copies from that component. Its command is then the most recent in the ranking, for the rest of the run.

`Trigger` gains `record Palette() implements Trigger {}`, which milestone 1 announced: an exhaustive `switch` over `Trigger` needs a new case.

`Dialogs` gains:

```java
Optional<PaletteEntry> chooseCommand(List<PaletteEntry> entries);
```

The real one shows an undecorated modal dialog owned by its window, horizontally centered on it, its top 48 pixels below the top of the window's content, as wide as the window less 48 pixels but no wider than 480. It holds a text field, empty, with the placeholder `typeCommand` ("Type a command"), which is also its accessible name, and a list of the entries that `search` returns for the field's text, up to 12 rows visible, each row the entry's text with its shortcut at its end, the first one selected. Typing filters; Up and Down move the selection; Enter chooses the selected entry; Escape, and the dialog losing the focus, cancel. With no match, the list shows `noCommand` ("No matching command"), and Enter does nothing. The texts come from the UI's messages, in English and French.

---

## Keymap

A user assigns, removes and resets shortcuts; the keymap saves them and applies them to every open window.

```java
@Singleton
public final class Keymap {                                             // UI
    public Optional<KeyStroke> shortcut(BoundCommand command);
    public Optional<KeyStroke> defaultShortcut(BoundCommand command);
    public void assign(BoundCommand command, KeyStroke shortcut);
    public void remove(BoundCommand command);
    public void reset(BoundCommand command);
    public void resetAll();
}
```

- **Identity.** The keymap knows a command by its owner's binary name and its id, `be.imgn.swing.desktop.DocumentCommands#save`, the standard commands' owner being `StandardCommands`. A mapping applies to that command in every view and every window.
- **Effective shortcut.** `shortcut(command)` is the keymap's mapping when it has one, an empty result for a removed shortcut, else `defaultShortcut(command)`, the shortcut of the command's resources. A command's `Action`, its menu items' accelerators and its window's input map follow the effective shortcut.
- **Changes.** `assign` maps the command to the shortcut, and removes the mapping of any other command mapped to the same key stroke, so that two mappings never share one. `remove` maps the command to no shortcut. `reset` removes its mapping, and `resetAll` every mapping. Each applies at once to every open window: accelerators, and shortcuts registered again. They are called on the EDT, and throw `NullPointerException` for a `null` argument.
- **Collisions.** In a window, a command's mapped shortcut wins over another command's default shortcut: the other command has no shortcut in that window, and nothing is reported, since the user chose. Two mappings that share a key stroke, which only a file edited by hand can hold, are logged as a warning when the file is read, and the first in the file wins.
- **Saving.** The mappings are saved to `keymap.toml` in `ApplicationDirectories.config()`, from the record `SavedKeymap(Map<String, String> shortcuts)` through toml's generated writer, atomically, in a task, ordered as the [saved locale](#the-saved-choice) is, with a shutdown step of order 340 that writes the last keymap; a removed shortcut is saved as `""`. A startup step of order 260 reads it. A mapping of a command that no view has created yet is kept as it is. A value that doesn't parse is logged as a warning and dropped; a file that doesn't bind is logged as a warning, and the keymap starts empty.
- **Format.** A shortcut is written in the resources' syntax, so that a keymap moves between systems: its modifiers in the order `shortcut`, `ctrl`, `meta`, `alt`, `shift`, the platform's menu shortcut modifier written `shortcut`, then the key's name, the `KeyEvent` constant's without `VK_`: `shortcut shift S`.

```toml
# keymap.toml
[shortcuts]
"be.imgn.swing.desktop.DocumentCommands#save" = "shortcut shift S"
"be.imgn.swing.ui.StandardCommands#selectAll" = ""
```

### Shortcuts of standard commands

A standard command's shortcut that comes from the framework's own resources yields to another command's shortcut in the same window: the standard command has no shortcut in that window, and nothing is reported, so that a shortcut the framework adds to a standard command, such as the palette's, never breaks an application that already uses it. A shortcut an application gives a standard command in its own resources, `[commands.copy]`, is reported on a collision, as in milestone 1.

### The keymap dialog

`StandardCommands` gains `keymap()`, which shows `KeymapDialog`, a public dialog view of `swing-ui`, over its view's window. Its text is `&Keyboard Shortcuts…`, and `Raccourcis cla&vier…` in French; it has no shortcut. It is created only when asked for, as a menu places it.

`KeymapDialog` implements `DialogView<Void>`, titled `keyboardShortcuts` ("Keyboard Shortcuts"):

- **Commands.** A filter field labelled `filter` ("&Filter:"), which filters the table as the palette's `search` does, above a table whose accessible name is the dialog's title, of two columns, `command` ("Command") and `shortcut` ("Shortcut"), listing the commands that the views of the opener's window created, enabled or not, but the system actions on macOS, which the application menu holds, sorted by text with the current locale's `Collator`. The shortcut column shows the effective shortcut as menu items show accelerators.
- **Editing.** Below the table, a field labelled `shortcutLabel` ("&Shortcut:") records the next key press, while it has the focus and a row is selected, as the selected command's shortcut: a press of a key that is not a modifier, Tab or Escape, with the Shift, Ctrl, Meta and Alt modifiers held, and that the resources' syntax names, so that it survives a restart; the field shows the selected row's shortcut. A command is listed once, whatever the number of the window's views that created it. When another listed command has that shortcut, `alsoUsedBy(command)` ("Also used by {command}.") is shown below the field. Buttons `remove` ("&Remove"), `reset` ("R&eset") and `resetAll` ("Reset &All") act on the selected row, or on every row the filter shows.
- **Applying.** The edits are held in the dialog until OK, which applies them through `Keymap`, in the order they were made, the last edit of a row replacing its earlier ones, with one save and one pass over the windows, `assign` taking the shortcut from the command that also had it; Cancel drops them. `commit()` applies; the dialog has no Apply.
- **Texts.** The quoted texts come from the UI's messages, in English and French, with no mnemonic used twice in the dialog.

---

## Window menu

`StandardCommands` gains:

```java
public MenuEntry openWindows();
```

It returns a [dynamic entry](milestone-1.md#dynamic-entries) of the open frames, in opening order, as `Frames.views()` lists them, numbered with `DynamicEntry.numbered`, each named by its frame's title, an `&` written `&&` since a title is data, and bringing its frame to the front with `Frames.toFront` when selected. It is rebuilt when a frame opens or closes, and when a frame's title changes through `Window.setTitle`. Dialogs are not listed. A Window menu holds it: `menus.window().with(commands.openWindows())`.

---

## Accessibility checks

Accessible names and descriptions come from resources, as milestone 1 has them. Milestone 4 checks that the components that need one have one, when they are built, since the build can't see component trees built in code.

When a view's build ends, after its commands are attached, the framework checks the components the build placed in its window's content pane, the toolbar and a dialog's button row included, that are visible, as are their ancestors up to the root pane, and focusable:

- **Names.** A `JTextComponent`, `JComboBox`, `JList`, `JTable`, `JTree`, `JSpinner`, `JSlider` or `AbstractButton` must have an accessible name that is not blank: `getAccessibleContext().getAccessibleName()`, which is its own text for a button, else its `accessible-name` resource, else the text of the label whose `labelFor` it is, else the title of its titled border. The check doesn't look inside a component of these kinds, such as an editable combo box's editor.
- **Labels.** A `JLabel` with a displayed mnemonic must have a `labelFor`, since its mnemonic otherwise focuses nothing.

The checks run only in development mode and in tests, where a failure is an exception, reported through the UI mode as duplicate mnemonics are. Otherwise they don't run at all, unlike the mnemonic checks of menus, which warn: a user can't act on a missing accessible name, and walking every view's components costs time at every build. A failure's message names the view class and the component: the path of its `@Exposed` field when it is one, `home.streetLabel`, else its type and its index path from the content pane, `JTextField at 0.2.1`. The test harness checks every dialog it builds as well, as it checks their mnemonics.

Toolbar buttons that hide their text, since their command has an icon, take the command's text, without its mnemonic marker, as their accessible name, unless the command has an `accessible-name` resource: a toolbar never fails the check. Nor do the framework's own views: the About dialog's read-only website field is named `website` ("Website", "Site web" in French), from the UI's messages.

To name the components of a view, the generated `ComponentInjector` (UI SPI) gains `void components(V view, BiConsumer<String, Component> component)`, which gives each `@Exposed` component field that holds a component, with its path, nested fields included, as `home.streetLabel`. A field whose type its enclosing fields already have is not followed further, so that a type that holds itself ends, but the paths that the resources name are all given. The accessibility checks, the [inspector](#inspector-overlay) and [`Ui.addTab`](#tab-titles) use it.

---

## Inspector overlay

In development mode, `shortcut alt shift I` toggles the inspector overlay of the window that has the focus. It is a key binding of every window's root pane, registered only in development mode; it is not a command, so neither the palette nor the keymap lists it, and a command with that shortcut is reported as a duplicate.

- **Overlay.** A component that covers the root pane's layered pane, in `JLayeredPane.DRAG_LAYER`, transparent, whose `contains` is always `false`, so that the mouse and the keyboard reach the window as usual.
- **Hovering.** While the overlay is shown, an `AWTEventListener` of mouse motion finds the deepest visible component under the mouse in that window. The overlay outlines it with a one-pixel red rectangle and shows a box beside it, inside the window, with one line each: the component's class; `field` and the `@Exposed` field's path in its view, which is also its table in the view's resources, `[home.streetLabel]`, or `none`; for a button or menu item of a command, `command` and the command's identity, `be.imgn.swing.desktop.DocumentCommands#save`, its table being `[commands.save]`; `view` and the view's class; `window` and `main frame`, `frame` or `dialog`.
- **Ownership.** The listener is removed when the overlay is hidden and when its window closes.

---

## Toolbars and tabs

### Toolbars

`Ui.toolBar` returns a toolbar that doesn't float (`setFloatable(false)`), as every port set by hand. Code that wants one that floats calls `setFloatable(true)`.

### Tab titles

`Ui` gains:

```java
public void addTab(JTabbedPane tabs, Component component);
```

It adds the component as the last tab of `tabs` and applies its `tab-title` at once, from the resources of the view's `@Exposed` field that holds it, nested ones included, so that tabs need no empty title and get theirs whenever they are added, during the build or later. It throws `IllegalArgumentException` when no `@Exposed` component field of the view holds the component, and `IllegalStateException` before the view exists, as in its constructor, and is called on the EDT. A refresh applies `tab-title` again, as every property.

So that a tab can be added after the build, applying `tab-title` at the end of the build, and at a refresh, skips a component that is not a tab yet, where milestone 1's injector threw `IllegalStateException`. `ComponentResources.setTabTitle` still throws when called on such a component: the generated injector checks first.

---

## EDT adapters

The vision's processor generated EDT adapters for `@EdtListener` interfaces. Milestone 1 removed the row from the vision, and left them to drop or to schedule with the inspector. They are dropped: `@OnEdt` ([milestone 2](milestone-2.md#subscribing-annotations)) delivers events on the EDT, and `Ui.bind`, `Ui.render` and the delivery rule deliver state, so nothing is left for them. The vision needs no change.

---

## Changes to milestones 1 to 3

These change what milestones 1 to 3 specify, from milestone 4 on:

- **The locale** changes at runtime through `Locales`, and is saved; `DisplayLocale` is the launch's locale, which only `Locales` reads; messages, the framework's texts and `ApplicationInfo.displayedName()` follow the current locale (see [Live locale switching](#live-locale-switching)).
- **Resources** have a dark variant, read while the appearance is `DARK`, and `Resources` gains `table(Class, Locale)`; `ResourceOpener` gains `directory()`; `flatlaf` joins `commands` and `menus` as a top-level table that names no component field, on the application class only (see [Resources per appearance](#resources-per-appearance), [FlatLaf tokens](#flatlaf-tokens) and [Hot reload](#hot-reload-of-resources)).
- **Tab titles** are skipped at the end of the build for a component that is not a tab yet, where milestone 1 threw (see [Tab titles](#tab-titles)).
- **Dark mode** is detected by a startup step of order 90, off the EDT, rather than by the installer on the EDT, and followed while running with the theme `system` (see [Following the system](#following-the-system)).
- **Windows** get the component orientation of the current locale; the UI sets Swing's default locale (see [Refreshing open windows](#refreshing-open-windows) and [Swing's own texts](#swings-own-texts)).
- **Shortcuts** follow the keymap, and a standard command's default shortcut yields to another command's (see [Keymap](#keymap)).
- **`Trigger`** gains `Palette`.
- **`StandardCommands`** gains `commandPalette()`, `keymap()` and `openWindows()`; **`Dialogs`** gains `chooseCommand`.
- **`Ui`** gains `render` and `addTab`; **`Column`** gains `of(Supplier<String>, ...)`; **`Ui.toolBar`** returns a toolbar that doesn't float, whose buttons that hide their text take their command's text as accessible name.
- **Views** are checked for accessible names when they are built, in development mode and tests (see [Accessibility checks](#accessibility-checks)).
- **`ComponentInjector`** gains `components`; **`ComponentResources`** gains `refreshing` and `isTab`.
- **`Window`** gains `title()`.
- **Settings ids** `locale` and `keymap` are the framework's.

---

## Build-time checks

Milestone 4 adds these checks to those of milestones [1](milestone-1.md#build-time-checks), [2](milestone-2.md#build-time-checks) and [3](milestone-3.md#build-time-checks), with diagnostics attached to the Java element as there:

| Check | Error when |
|---|---|
| Dark variants | A dark variant holds a key its base file lacks, or breaks a rule of the base file; a `@Messages` interface has a dark variant; a dark variant has a locale or platform part, as `X_fr.dark.toml` or `X.macos.dark.toml` |
| FlatLaf tokens | A `[flatlaf]` table is in the resources of a class other than the `@Application` class; one of its keys is blank; one of its values is not a string, an integer or a boolean |
| Settings ids | A `@Settings` record's id is `locale` or `keymap` |

The commands' check of `[commands.<id>]` tables also accepts `commandPalette` and `keymap`, as standard commands. The component-fields check accepts `flatlaf` as a top-level table of the application class, as it accepts `commands` and `menus`. The translations check treats a dark variant as a platform variant: it may hold a few keys of the base file only.

---

## Processor

- **Messages.** `$X$$Messages` takes `Resources` alone, follows the current locale as [Messages follow the locale](#messages-follow-the-locale) describes, and has a second constructor for a fixed locale. It holds its patterns through `MessageSource` (`be.imgn.swing.spi`, for generated code), built with `following(resources, type, read)` or `fixed(resources, type, locale, read)`, whose `get()` reads them again, all at once, when the table or the locale it reads differs from its last read. For each `@Messages` interface, the processor generates `$X$$MessagesDescriptor implements MessagesDescriptor` (`be.imgn.swing.spi`), with `Class<?> type()` and `Object create(Resources resources, Locale locale)`, which builds the implementation for a fixed locale, and registers it through `ServiceFiles`; `Locales.messages` finds it through `ServiceLoader`, as `Swing.launch` finds application descriptors. On the module path, gaia's `register-services` adds its `provides` clause.
- **Views.** `$X$$Injector` implements `components`.
- **Resources.** `$$Resources` implements `directory()`. The resources step reads and checks dark variants and `[flatlaf]` tables.
- **Settings.** The settings step rejects the framework's ids.

---

## Test harness

```java
@Test
void switchesTheMenusToFrench() {
    try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
        app.commands(GeneralActionsCommands.class).french().execute();

        assertThat(app.component(JMenuBar.class).getMenu(0).getText()).isEqualTo("Fichier");
        assertThat(app.component(JTable.class).getColumnName(0)).isEqualTo("Prénom");
    }
}
```

`AppTester` gains:

| Method | Does |
|---|---|
| `void locale(Locale locale)` | Calls `Locales.set` on the EDT, then settles: the refresh is done and the choice saved |
| `void appearance(Appearance appearance)` | Changes the appearance as the system would, through the tester's own `Appearances`, which starts `LIGHT`, then settles. The look and feel isn't installed: the harness installs none |
| `Inspection inspect(Component component)` | What the [inspector](#inspector-overlay) shows for a component of an open window: `record Inspection(Class<? extends Component> type, Class<?> view, @Nullable String field, @Nullable String command)` (testing), the command as its identity, `null` for a component that is no field or no command's. `IllegalArgumentException` for a component of no open window |

`FakeDialogs` gains:

| Method | Does |
|---|---|
| `answerCommand(String query)` | Queues the answer of the next `chooseCommand`: the first entry that `CommandPalette.search(query)` ranks, or an empty result when none matches |
| `cancelCommand()` | Queues a cancel of the next `chooseCommand`; a palette with no answer queued is cancelled |
| `List<String> commandQueries()` | The queries of the palettes answered, in order, `""` for a cancel |

- **Settling** also waits for the saves of the locale and of the keymap, as for settings saves.
- **Checks.** Accessibility failures are recorded as mnemonic failures are, and fail the test at its next call.
- **Hot reload** never runs under the harness (see [Hot reload](#hot-reload-of-resources)).
- **The keymap dialog** is answered with `answerDialog(KeymapDialog.class, ...)`, as any dialog view; tests record a shortcut by sending a `KeyEvent` to its field.

---

## Upstream changes

Milestone 4 needs none. It relies on this existing behavior:

- FlatLaf 3.7.2's `FlatLaf.setGlobalExtraDefaults(Map<String, String>)`, read by each `FlatLaf.setup`, and `FlatLaf.updateUI()`, which updates the component tree of every window.
- toml's generated writers and its binding that ignores unknown keys, for the saved locale and the keymap, as for the recent files of milestone 3.
- gaia's `ApplicationDirectories.config()`, per user and per application.

---

## Changes to the vision

These depart from the [vision](vision.md), and are to be applied to it:

- **Live locale switching** re-applies resources and refreshes commands, as the vision says, and menus, which it doesn't name, but for `enabled` and `visible`, which the view's code owns once it is built. "Refreshes every message" holds for messages that are formatted again: texts computed by code follow through `Ui.render` and suppliers, while texts code holds as strings, such as the messages of validation rules and the descriptions of `FileType`s, keep their locale. The user's choice is saved. The vision has no messages in another locale; `Locales.messages` adds them.
- **Modules.** The command palette and the keymap are in `swing-ui`, where the vision lists them under desktop integration: they read the command registry, and the palette's `Trigger.Palette` must be in the module of the sealed `Trigger`. Dark resources and `Appearance` are in core, where the vision has dark mode in `swing-flatlaf`: resources are merged in core. `swing-flatlaf` keeps detecting and following the system, and the tokens. The appearance is a UI concept, which the fifth principle keeps out of core; it is the one exception, since core's resources depend on it, and it holds no Swing type: core still doesn't read `java.desktop`.
- **Standard commands.** `commandPalette()` and `keymap()` join the vision's table of standard commands, and `openWindows()`, a menu entry, joins `StandardCommands`.
- **Hot reload** reads the files in the class output, "when it changes on disk" meaning when the build copies it there, and polls rather than watches. A reloaded file is not checked as the build checks it, and a value that can't be applied is a warning: an exception to the vision's first principle, in development mode only, which the next build makes good.
- **The processor's table** gains a row, `$X$$MessagesDescriptor` services, from `@Messages` interfaces, which replace looking up messages by locale at runtime; and the resource opener of each package also gives its directory in the class output.
- **Theme tokens** are FlatLaf's UI defaults and variables, in a `[flatlaf]` table of the application's resources, and a component's colors per appearance are a dark variant of its resources. The look and feel follows the system's setting while running. The build checks the table's form but not its keys, an exception to the vision's first principle that FlatLaf's open set of keys and variables forces.
- **Inspector.** The vision's "name, resource key and scope" are the `@Exposed` field's path, which names the component in its view and is its table in the view's resources, the command's identity and table for a command's component, and the view and window that hold it.
- **Accessibility.** The checks of accessible names run when views are built, in development mode and tests only, since the build can't see component trees; the vision's "accessible-name checks" are those.
- **Command palette and keymap.** The vision's "every command is registered" holds for the commands the views have created: the palette and the keymap dialog list those of the active window's views, and a standard command that no view asked for is in neither. The vision's "one command registry" is the registry of each view: the palette and the keymap dialog read those of a window's views, and `Keymap` holds the user's mappings apart from them, since they outlive every view. The keymap knows a command by its owner and id, and has a dialog. The palette runs commands with a new `Trigger`, `Palette`. Its shortcut, the vision's Ctrl+Shift+P, is `shortcut shift P`: ⌘⇧P on macOS.
- **Roadmap.** Milestone 4 also holds live locale switching, with the component orientation of the locale and Swing's default locale, which the vision doesn't name, following the system's appearance, theme tokens, the Window menu, toolbars that don't float and tab titles.

---

## Acceptance

`./mvnw -B verify` succeeds on JDK 21 and on JDK 25, with the libraries of milestone 3. Every test of milestones 1 to 3 still passes, except those of the behavior milestone 4 changes, which milestone 4's tests replace. Every example passes the accessibility checks: the examples give accessible names, labels or `accessible-name` resources, wherever the checks report one missing. `swing-examples` has the following, each with `AppTester` tests of every behavior listed.

### The locale examples, as in SwingAppFramework

- **`saf/LocaleExample3`**, a single-frame application showing three panes, one per locale, English, French and Spanish, each a label and a button, as the original's `LocalePane`. Their texts come from `LocalePaneMessages` in the pane's locale, through `Locales.messages`: the original's `text`, `texte` and `texto`, and `What ?`, `Quoi ?` and `Qué ?`. Each button runs `exit()`, whose veto asks `stop()` ("Stop ?") with `Dialogs.confirm`. The original's title, `ExitListener Demo`, and its texts are kept.
- **`saf/LocaleExample4`**, a single-frame application with a global combo box of the locales, the starting locale, English, French and Spanish, which sets the display locale with `Locales.set`, above a pane that follows it, through `Ui.render`; then a second combo box of the same locales, which only changes the pane below it, through `Locales.messages`.

Their tests check, at least: each pane of `LocaleExample3` shows its locale's texts whatever the display locale; choosing French in `LocaleExample4`'s global combo box changes the global pane, and not the second one; choosing Spanish in the second combo box changes only the second pane; the global choice survives a restart on the same file system.

### The Address Book's language menu, as in Guts

- **Language menu.** A `[menus.language]` menu in `AddressBookApp`'s resources, `&Language` and `&Langage` in French, as in Guts, between the Contacts and Task Tests menus, with two commands of `GeneralActions`, in Guts' order, `french` then `english`, with Guts' texts, French and English, Francais and Anglais, which set the display locale.
- **Columns.** The contacts table's columns take their headers from messages through `Column.of(Supplier<String>, ...)`.

Its tests check, at least: `french()` changes the menu bar's texts, the table's headers and the detail panel's labels to French, and `english()` changes them back; the window's title follows; a contact dialog opened after the change is in French; the choice survives a restart on the same file system.

### Memo

Memo, milestone 3's multi-frame example, gets the rest of milestone 4:

- **Menus.** The Edit menu gains a separator, `commandPalette` and `keymap`. A Window menu holds `openWindows`.
- **Appearance.** Its status label, a private field in milestone 3, becomes an `@Exposed` package-private field `status`, so that its resources reach it, and has the foreground `#5f6368`, and `#9aa0a6` in `MemoView.dark.toml`. `MemoApp.toml` has a `[flatlaf]` table with `"@accentColor" = "#1a73e8"`, and `"#8ab4f8"` in `MemoApp.dark.toml`.
- **Status.** The status label shows `words(count)` through `Ui.render`, so that it follows the locale.

Its tests check, at least: the palette finds `save` from `sav` and runs it, the note's file chosen with `answerSaveFile`; the search ranks a prefix match before a word match before a substring before a subsequence; an empty query lists the last command run first; disabled commands are not listed; `commandPalette()` has `shortcut shift P` in every frame; assigning `shortcut shift S` to `save` through the keymap dialog makes it the save item's accelerator in every open frame, takes it from `saveAs`, and survives a restart; removing and resetting a shortcut; the Window menu lists the open notes by title, follows a title change after a save, and brings the chosen one to the front; `appearance(DARK)` changes the status label's foreground and `appearance(LIGHT)` restores it; switching to French changes the status label's text; `inspect` gives the editor's field and the save item's command. Memo's commands are the framework's, which don't show their trigger, and its menus show the palette: a test application of its own checks that a command run from the palette gets the `Palette` trigger, and that `commandPalette()` has its shortcut in a frame whose menus don't show it.

### The other ports

- **Toolbars.** `saf/SingleFrameExample4`, `saf/SingleFrameExample5` and `saf/BlockingExample1` no longer call `setFloatable(false)`.
- **Tabs.** `saf/Tabbed`, `saf/SessionStorageExample1` and `addressbook/ContactTabsDialog` add their tabs with `ui.addTab`, where they called `addTab("", ...)`.
- **The palette's shortcut.** `saf/BlockingExample1` keeps its own `shortcut shift P`, which wins over the palette's in its frame (see [Shortcuts of standard commands](#shortcuts-of-standard-commands)).

Their tests check, at least: the toolbars don't float; each tab has its title from its resources; `BlockingExample1`'s command keeps `shortcut shift P` and the palette has none in its frame.

### Without the examples

`swing-core`'s own tests check the hot reload's polling and reloading against a temporary directory and a hand-written `ResourceOpener`, a file that doesn't parse included, and the saved locale's file. `swing-ui`'s own tests check the palette's search and ranking, the keymap's format and collisions, the yielding of standard shortcuts, the accessibility checks on components built for them, a refresh's handling of a property that can't be applied, and the real `chooseCommand` dialog's filtering with constructed key events. `swing-flatlaf`'s own tests check the following of the system's setting against a fake command runner and a fake schedule, the tokens given to FlatLaf for each appearance, on the class path with resources of their own, and that a read asked for while one runs is dropped. The processor's tests check every new build-time check.

---

## Order of work

Each step adds the harness methods its own tests need.

1. Core and processor: `Locales`, `Resources` per locale, messages that follow the locale, `MessagesDescriptor`, the saved locale, and the reserved settings ids.
2. UI: the refresh, `Ui.render`, `Column.of(Supplier, ...)`, the component orientation and Swing's default locale; desktop's titles; the harness's `locale`.
3. Core, FlatLaf and processor: `Appearances`, dark variants, following the system, `[flatlaf]` tokens, with their checks; the harness's `appearance`.
4. Core and UI: hot reload, with `ResourceOpener.directory()`.
5. UI: the keymap, its file, the yielding of standard shortcuts, and `KeymapDialog`.
6. UI: `CommandPalette`, `Trigger.Palette` and `chooseCommand`; the harness's palette answers.
7. UI and processor: the Window menu, toolbars that don't float, and `Ui.addTab`, with `ComponentInjector.components`, which it needs.
8. UI: the accessibility checks, and the inspector; the harness's `inspect`.
9. The acceptance examples.
