# Milestone 1: audit

An audit of the `milestone-1` branch, at `3757ae4`, against the [milestone 1 requirements](milestone-1.md) and the [vision](vision.md). It records what conforms, what doesn't, and where the two documents and the code disagree, so that the branch can be brought to acceptance and the documents corrected where they, not the code, are wrong.

## Method

- The requirements document was split into eight areas: application model; scopes and observables; commands; menus and toolbars; resources, messages and build-time checks; tasks and input blocking; windows, dialogs, window state and look and feel; test harness, processor, modules and acceptance. Each area was checked sentence by sentence against the full source of the files that implement it, with the generated sources under `swing-examples/target/generated-sources` as ground truth for the processor.
- The vision was checked in two ways: its principles against the code directly (reflection, discovery, ownership of every listener and subscription, text and tuning in Java, module descriptors, the out-of-scope list, the processor table), and its feature list and roadmap against the requirements document, to find where milestone 1 departed from the vision without saying so.
- Every finding cites code as `path:line` on `3757ae4`. A quoted requirement links to the section it comes from, as `([source](milestone-1.md#startup))`. Each headline finding was confirmed by hand.
- Build: `./mvnw -B verify` passes on JDK 21 and on JDK 25, with gaia `main` at `6256bea` (the event bus in `gaia-core`, `gaia-processor`, the standard generated names) and toml `main` at `60678e0` installed locally: 100 tests, 26 in `swing-core`, 36 in `swing-processor`, 38 in `swing-examples`. `swing-ui`, `swing-flatlaf` and `swing-testing` have no tests of their own.

## Verdict

The branch implements milestone 1 faithfully in structure and in API. Every module, type, method, signature, generated class shape, resource file, TOML layout, startup and shutdown step, build-time check and error case that the requirements name exists and, with the exceptions below, behaves as written. The counter example's `CounterAppCommands` and `$CounterApp$$Application` match the document's listings verbatim; the two harness tests of the [test harness section](milestone-1.md#test-harness) compile and pass as written; the vision's seven principles hold in the code, with two violations and a few tensions.

It is not yet acceptable: six behaviors contradict the requirements ([A](#a-behaviors-that-contradict-the-requirements)), four required things are missing ([B](#b-required-and-missing)), the vision's principles are broken in three places ([C](#c-vision-principles-broken-in-the-code)), and the requirements were departed from in eight places without the document being updated first, as its own rule demands ([D](#d-code-that-departs-from-the-requirements-without-the-document-being-updated)): "Any deviation […] is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text" ([source](milestone-1.md#milestone-1-requirements)). Section [F](#f-where-milestone-1-departs-from-the-vision-without-recording-it) lists the places where milestone 1 departs from the vision without recording it, a documentation debt rather than a code one.

Severity, throughout:

- **DEVIATION**: the code behaves or is named otherwise than the document says.
- **GAP**: required and missing.
- **DOC**: the code is defensible and the document must change.
- **NOTE**: an edge the documents don't settle, recorded for later.

## A. Behaviors that contradict the requirements

### A1. A vetoed OS Quit is never cancelled — DEVIATION

- Requirement: "The first one that returns `false` cancels; for the OS Quit, the framework then calls `QuitResponse.cancelQuit()`" ([source](milestone-1.md#shutdown)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/MainWindowFlow.java:106-112`.
- The quit handler runs the `exit` command, which posts the shutdown with `invokeLater`, then posts a second runnable that calls `cancelQuit()` only if `!lifecycle.isShuttingDown()`. A veto that shows a modal dialog, as `CounterApp.canExit()` does through `Dialogs.confirm`, pumps a nested event loop: the check runs while the state is `ASKING`, sees a shutdown in progress, and does nothing. When the veto then returns `false`, the `QuitResponse` is left unanswered.
- `DefaultLifecycle.onCancel(Runnable)` (`swing-core/src/main/java/be/imgn/swing/internal/DefaultLifecycle.java:74-77`) exists for exactly this and has no caller outside tests.

### A2. The error dialog is never owned by its window — DEVIATION

- Requirement: the `FailureHandler` of the UI module "Shows the error dialog, owned by the focused window, else by the main window" ([source](milestone-1.md#shutdown)); `Dialogs` "shows standard dialogs, owned by its window" ([source](milestone-1.md#dialogs)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/internal/SwingDialogs.java:24-26,41`, `swing-ui/src/main/java/be/imgn/swing/ui/internal/ErrorDialog.java:77,103`.
- `SwingDialogs` passes the AWT `JFrame` or `JDialog` itself as the owner component, and `ErrorDialog` resolves the owner with `SwingUtilities.getWindowAncestor(owner)`, which starts at `getParent()`: `null` for a `JFrame`, the owner frame for a `JDialog`. The error dialog is created with no owner, and `setLocationRelativeTo(null)` centers it on the screen.
- `confirm` and `inform` are unaffected: `JOptionPane` special-cases a `Window` argument.

### A3. The French standard menus `view` and `help` share a mnemonic — DEVIATION

- Requirement: `StandardMenus` provides `file()`, `edit()`, `view()`, `window()` and `help()` "with text and mnemonics from the framework's resources" ([source](milestone-1.md#menus-and-toolbars)), and the framework "checks for duplicate mnemonics in each menu and among the bar's menus when it builds them: an exception in development mode and in tests, a warning otherwise" ([source](milestone-1.md#menus-and-toolbars)).
- Code: `swing-ui/src/main/resources/be/imgn/swing/ui/StandardMenus_fr.toml:7-8,13-14`.
- `[menus.view] text = "&Affichage"` and `[menus.help] text = "&Aide"` both mark `A`. A French menu bar that holds `view()` and `help()` fails the framework's own check (`MenuBuilder.java:137-151`) in tests and in development mode, and warns otherwise.

### A4. Standard-command overrides are ignored in dialogs built from a factory — DEVIATION

- Requirement: "In a view whose class, or one of its superclasses, declares the override, every `StandardCommands` instance of the view returns the user-written command. The framework registers the view class's overrides in the view's `CommandRegistry` before it resolves the view" ([source](milestone-1.md#overriding-standard-commands)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/Ui.java:131-135`, `swing-ui/src/main/java/be/imgn/swing/ui/ViewBuilder.java:71-78`, `swing-ui/src/main/java/be/imgn/swing/ui/CommandRegistry.java:90`.
- `showDialog(factory, create)` passes the type as `Optional.empty()`, so `ViewBuilder` skips `overrides(known, injector)` and `view.type()` is `Object.class` while the dialog's generated commands class is constructed; `CommandRegistry.bind` records an override only if `owner.isAssignableFrom(view.type())`. A dialog built through an assisted factory that overrides `ok`, `cancel` or any other standard command gets the standard one. Views resolved by type conform.
- As written, the requirement can't be met for the factory form, since the type is unknown before `create` runs. Either the document says so, or the registry re-scans its commands once the type is known.

### A5. A `Programmatic` context reached through the `Action` carries modifiers — DEVIATION

- Requirement: "From `OperatingSystem` and `Programmatic` triggers, the set is empty" ([source](milestone-1.md#declaration)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/CommandAction.java:35-37,56-66`.
- An `ActionEvent` whose source is neither a `JMenuItem` nor an `AbstractButton` maps to `Trigger.Programmatic`, but the event's modifiers are still read into the context, so a `Programmatic` context can carry `SHIFT`.

### A6. Translation diagnostics contain NUL characters — DEVIATION

- Requirement: "Diagnostics are attached to the Java element […], with the file, line and column in the message" ([source](milestone-1.md#build-time-checks)).
- Code: `swing-processor/src/main/java/be/imgn/swing/processor/ResourceChecks.java:487,552,567`.
- The key paths of the Translations check are joined with `"\u0000"` and printed as such: `The key commands<NUL>increment<NUL>tool-tip-text is not in the base file`. `ResourcesStepTest.java:109` asserts the prefix only.

## B. Required and missing

### B1. The `ActionEvent` to `CommandContext` test doesn't exist — GAP

- Requirement: "a test sends `new ActionEvent(item, ActionEvent.ACTION_PERFORMED, id, when, ActionEvent.SHIFT_MASK)` to the command's `Action`, for a `JMenuItem` in a menu bar, in a popup menu and for a `JButton`, and checks the context the handler receives" ([source](milestone-1.md#declaration)).
- No such test exists. `swing-ui` has no `src/test` directory; the only `ActionEvent` built in a test (`swing-examples/src/test/java/be/imgn/swing/examples/saf/TaskExamplesTest.java:54`) uses `this` as its source and never inspects the context. The package-private `CommandAction.contextOf(ActionEvent, boolean)` was written for this test.
- More broadly, `swing-ui`, `swing-flatlaf` (an empty `src/test`) and `swing-testing` have no tests: the harness and all UI behavior are exercised through `swing-examples` only.

### B2. The acceptance ports are incomplete, and their reductions unrecorded — GAP

- Requirement: "The release is accepted when SwingAppFramework's examples and Guts' Address Book demo are ported" ([source](milestone-1.md#milestone-1-requirements)); the vision's acceptance line says the same ([source](vision.md#milestone-1-first-useful-release)).
- Of the 24 originals in `ogregoire/swingappframework/src/test/java/examples`, three are not ported: `LocaleExample3`, `LocaleExample4` and `Tabbed`.
- The Guts port drops the locale menu (`swing-examples/src/main/java/be/imgn/swing/examples/addressbook/GeneralActions.java:9` says so, in code) and folds `AddressBookMainView`, `ContactsListView`, `ContactDetailView` and `ContactPictureView` into the application class (`AddressBookApp.java:31-33`). `SingleFrameExample6` shows its secondary frames as modal dialog views (`SingleFrameExample6.java:17-19`).
- Each reduction follows the "Later" column of the [Scope table](milestone-1.md#scope): live locale switching, views in other windows, opening frames from code. But the acceptance sentence is unqualified, and neither document records what the ports leave out.

### B3. The tester doesn't itself recheck menus after a dynamic change — GAP

- Requirement: "The tester also checks every dialog it builds, and every menu after a dynamic section changes, for duplicate mnemonics and duplicate shortcuts" ([source](milestone-1.md#test-harness)).
- Code: `swing-testing/src/main/java/be/imgn/swing/testing/AppTester.java:287` is the only call of `MnemonicChecker.check`, for dialogs.
- Menus are rechecked after a dynamic rebuild by the framework (`MenuContent.rebuild` → `built` → `MenuBuilder.check`), which throws in the tester's strict `UiMode`. The tester adds nothing for menus and sees that throw only through the default uncaught-exception handler it installs.

### B4. Not every `AppTester` call runs on the EDT — GAP

- Requirement: "Every call on `AppTester` and on the commands it returns runs on the EDT and waits" ([source](milestone-1.md#test-harness)).
- Code: `swing-testing/src/main/java/be/imgn/swing/testing/AppTester.java:232` (`dialogs()`), `:241` (`ticker()`), `:259` (`isRunning()`), `:271` (`exitStatus()`).
- None goes through `call()`. The last two settle the executor; the first two neither settle nor wait, and the `FakeDialogs` methods run on the caller's thread.

## C. Vision principles broken in the code

### C1. The focus tracker's listener belongs to no scope — DEVIATION

- Principle: "Everything has an owner. Every subscription, listener, binding and task belongs to a scope (application, window or view). Closing the scope releases them" ([source](vision.md#principles)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/internal/FocusTracker.java:37-45`.
- `start()` adds a `PropertyChangeListener` for `permanentFocusOwner` to the JVM-global `KeyboardFocusManager`, once per application start (`Launch.java:53`), owned by no scope: there is no `stop()`, no `@PreDestroy`, no `removePropertyChangeListener`.
- In production the JVM exits with it. `AppTester` starts many applications in one JVM, and its `close()` (`AppTester.java:298-318`) closes the windows and the root injector only, so every test leaks a listener holding the tracker, its `Property<Focus>` and the last focused component. The application `DisposalScope` (`Launch.java:39`, closed by `DefaultLifecycle.java:109-118`) could own it.

### C2. The startup error dialog hard-codes its texts — DEVIATION

- Principle: "Text, icons, shortcuts, mnemonics, accessible names, colors and timings go in resources, where translators and designers can edit them" ([source](vision.md#principles)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/Swing.java:60`.
- The startup failure path calls `ErrorDialog.show(null, "Error", …, "Details", "Copy", "Close")` with four hard-coded English strings, even when the injector was built and `UiTexts`, whose `UiTexts_fr.toml` holds exactly these keys, is available. The requirement says only that a failure "shows the error dialog if the look and feel is installed" ([source](milestone-1.md#startup)).

### C3. The acceptance ports keep text, fonts and colors in Java — DEVIATION

- Principle: the same ([source](vision.md#principles)). The ports are the acceptance criterion, so they should show the framework's way.
- Code, in `swing-examples/src/main/java/be/imgn/swing/examples/`:
  - `saf/ExitExample1.java:40`: the label `" Close the Window to Exit "`; no `ExitExample1.toml`.
  - `saf/ApplicationExample1.java:35-36`: `"Hello World"` and `new Font("LucidaSans", Font.PLAIN, 32)`; `font` is a supported property.
  - `saf/SessionStorageExample1.java:66,70,73,82-83,90,93`: column headers, the tab title `"all colors"`, an explanatory label, the `.darker()` labels, a font and `Color.WHITE`; `tab-title`, `font` and `foreground` are supported properties.
  - `saf/PeriodicTaskExample.java:40`: the initial status `"Ready..."`, while `ExampleMessages` is injected on the same lines.
  - `saf/BaseScene.java:21`, `saf/DerivedSceneA.java:17`, `saf/DerivedSceneB.java:17`, with `saf/ScenePanel.java:36-37`: titled-border titles passed as strings, and icons loaded with `getResource` instead of the `icon` property; `title` is a supported `JComponent` property.
  - `addressbook/TaskTestActions.java:134,138`: `"Sleep chunk #…"` and `"Task done!"`, shown by the blocking glass pane, while `AddressBookMessages` exists.

### What holds

Everything else the principles ask for holds:

- No reflective member access anywhere; all runtime lookups are by `Class` through inject or `ServiceLoader` (`Launcher.java:29`, `ResourceTables.java:58`, `ViewSupports.java:22,25`).
- No weak or soft reference in any module.
- Every observable subscription, EDT delivery, dynamic menu section, command state, shortcut, task, window and view registration and exit veto is owned by a `DisposalScope` and released when it closes; `map` and `combine` disconnect from their sources on the last unsubscribe; input blocking is undone when the task ends even if its scope closed first.
- `swing-core` and the three libraries it requires are free of `java.desktop`.
- `META-INF/services` entries and `module-info` `provides` and `uses` agree in every module; the examples module gets its `provides` from `register-factories` and `register-services`, as its compiled descriptor shows.
- None of the items of the [out-of-scope table](vision.md#out-of-scope) is present: component trees in resources, navigation by name, `%{}` templates, weak consumers, a module system, task groups, a widget library.

## D. Code that departs from the requirements without the document being updated

The requirements ask that a deviation be reported and the document updated before the implementation follows the new text ([source](milestone-1.md#milestone-1-requirements)). These are the places where the code is defensible and the document is wrong, incomplete or inconsistent.

### D1. The inherited-member rule is unsatisfiable — DOC

- Requirement: "An inherited handler, `enabled` target or `selected` target in another package must be `protected` or `public`; otherwise it is a compile error" ([source](milestone-1.md#inheritance-and-naming)).
- Code: `swing-processor/src/main/java/be/imgn/swing/processor/CommandsStep.java:118-122,182-186` require `public`.
- `q.SubCommands` is not a subclass of `p.Base`, so it can't call a `protected` member of `p.Base`. The code is right; the text is wrong.

### D2. The document disagrees with itself on which classes get `XMenus` and checks — DOC

- Requirement, one way: "For each class whose resources have `[menus.<id>]` tables, the processor generates a `<Class>Menus` class" ([source](milestone-1.md#menus-and-toolbars)); "For every class with commands, menus, component fields with resources, or a `@Messages` interface, the processor reads the class's TOML files" ([source](milestone-1.md#build-time-checks)).
- Requirement, the other way: the Menus step is triggered by "`[menus.*]` tables in the resources of any of the above", the application, command, message and view classes ([source](milestone-1.md#processor)).
- Code: `MenusStep.java:50-54,61-78` visits the application class, `@Command` classes and views only; `ResourceChecks` reads the files of those classes only.
- A plain class with only `[menus.*]` tables, or with component-field tables, gets no generated class, no check and no diagnostic. The processor table is the behavior to keep.

### D3. The task timeout isn't configurable — DOC

- Requirement: "Cancel running tasks and wait for them, up to a timeout (2 s by default)" ([source](milestone-1.md#shutdown)).
- Code: `DefaultLifecycle.java:23` hard-codes `TASK_TIMEOUT = 2 s`.
- "By default" implies a knob that doesn't exist. Either name the bean or option that sets it, or drop "by default".

### D4. The scope widening check skips records, enums and inherited members — DOC

- Requirement: "Every class the processor compiles is checked", at every "injection point (constructor parameter, `@Inject` field or method parameter)" ([source](milestone-1.md#build-time-checks)).
- Code: `ViewsStep.java:65-70,86-101`.
- Records and enums are skipped (`ElementKind.CLASS` only), and only the class's own `@Inject` members are examined, not those inherited from an unscoped superclass.

### D5. Only language files are checked for missing keys — DOC

- Requirement: an error when "A locale file lacks a key of the base file, or has a key the base file lacks" ([source](milestone-1.md#build-time-checks)).
- Code: `ResourceChecks.java:456` and `MessagesStep.java:115` check language files (`X_fr.toml`) only.
- Country files (`X_fr_BE.toml`) and platform variants are not checked for missing keys.

### D6. Duplicate shortcuts are checked per file — DOC

- Requirement: "Two commands with the same shortcut in one class's resources fail the build" ([source](milestone-1.md#keyboard)); an error when "two commands of the class share one" ([source](milestone-1.md#build-time-checks)).
- Code: `ResourceChecks.java:112-158`; the map of shortcuts is local to one file.
- `save = "shortcut S"` in `X.toml` and `open = "shortcut S"` in `X_fr.toml` pass, while `"shortcut S"` and `"ctrl S"` are reported as one shortcut although they differ on macOS.

### D7. The views step never holds an element back — DOC

- Requirement: "An element it holds back is given back as its top-level type, since a step only gets root elements and elements with its annotations" ([source](milestone-1.md#processor)).
- Code: `ViewsStep.java:49-56` always returns `Set.of()`.
- The mechanism described is never used. Only `MenusStep.heldBack` has a notion of holding back, done by re-finding classes through their resource files.

### D8. `maximized` is written and restored otherwise than described — DOC

- Requirement: the example file has `maximized = false` under `[windows.main]`, and the table lists `maximized` as state separate from `bounds`; when saved bounds are rejected, "the rest of its state is still restored" ([source](milestone-1.md#window-state)).
- Code: `WindowStateFile.java:168-170` writes `maximized` only when true; `WindowStateSupport.java:85-89` applies `maximized` only with accepted bounds.
- Round-trips fine; the file and the sentence about rejected bounds differ from what is written and restored.

## E. Edges the documents don't settle

Recorded so that milestone 2 or a later audit can decide them; none counts against conformance.

### Lifecycle

- Two `requestExit` calls queued before the EDT drains both run; if the first is vetoed, the user gets a second veto prompt (`DefaultLifecycle.java:53-57,80-85`).
- `VetoRegistrar.java:26-27` silently drops an instance whose injector is not yet in `Hosts`; `ViewBuilder` registers a host after `build()`, so anything inject constructs eagerly during `build()` is lost as a veto.
- The startup exit falls back to `System::exit` when the injector itself failed (`Swing.java:76`), since no `Exit` bean exists then.
- `Launch.java:53` starts `FocusTracker` inside startup step 4, which the [startup steps](milestone-1.md#startup) don't list.
- A `ShutdownStep` that throws is logged and skipped (`DefaultLifecycle.java:100-106`); unspecified.
- `DefaultLifecycle.onCancel` is public API with no caller (`DefaultLifecycle.java:74-77`).

### Scopes and observables

- A view's framework `Scope` is closed as a resource of the window's `Scope`, after the window injector's `@PreDestroy` methods, not together with the view injector (`ViewBuilder.java:116-122`, `DisposeWindowsStep.java:37-41`).
- `Derived.get()` recomputes rather than returning the kept value when that value is `null` (`Derived.java:37-40`); `Property.of(null)` and `set(null)` throw `NullPointerException` (`AtomicProperty.java:26,36`).
- `EdtDelivery.java:33-35` clears the pending flag before `get()`, so a concurrent `set` can deliver an equal value twice; coalescing before the update runs holds.
- `BlockingGlassPane.java:175-176` subscribes to progress and message without `EdtDelivery`; safe, since those properties are only set through the `UiExecutor`.
- Subscribing or registering into a closed scope throws `IllegalStateException` (`Listeners.java:26-28`, `DisposalScope.java:77-80`), so `Ui.bind` on a closed view throws rather than doing nothing.
- `FocusTracker.java:56-66` keeps a strong reference to the last focused `JTextComponent` until focus moves again, across view scopes.
- `AppTester.close()` never closes the application `Scope` bound in the root (`Launch.java:39-40`), so tasks and subscriptions an application registers in it are not released between tests.

### Commands and menus

- An override of a standard command declared in a `@Singleton` or `@WindowScoped` helper, not in the view class, leaves the standard command attached with the same inherited shortcut, so `ShortcutBindings` reports a duplicate and throws in tests and development mode (`CommandAttacher.java:30-37`, `CommandRegistry.java:110-125`).
- The platform's menu shortcut modifier is derived from `Platform.isMacOS()` everywhere; `Toolkit.getMenuShortcutKeyMaskEx()` is never consulted.
- `execute()`'s "enabled" folds in the `COMMAND` block state (`BoundCommand.java:218-220`), which the [`BoundCommand` table](milestone-1.md#boundcommand) doesn't mention.
- `DynamicEntry.numbered` prefixes `"&1 "` with a trailing space (`DynamicEntry.java:37`); the [dynamic entries section](milestone-1.md#dynamic-entries) says `&1`.
- Dropped separators are hidden (`setVisible(false)`), not removed (`MenuContent.java:75-91`); a submenu inside a dynamic section re-subscribes on every rebuild and never closes the previous subscription (`MenuBuilder.java:113`, `MenuContent.java:46`).
- `MenuBuilder.check` also reports duplicate shortcuts within a menu, so a cross-class duplicate is reported twice, at attachment and at build.
- A `[menus.file]` override is looked up in the current view class's resources (`MenuBuilder.java:38-42`), so one in the application class's file isn't seen from a dialog view, while the text says "A `[menus.file]` table in the application's resources overrides their text" ([source](milestone-1.md#menus-and-toolbars)).
- `CommandRegistry.constructing` is a single slot (`CommandRegistry.java:34,86-89`); nested construction of two commands classes would mis-attribute commands.
- The check that "Two classes in one package that would produce the same name are a compile error" ([source](milestone-1.md#inheritance-and-naming)) is per compilation (`Shared.java:122-124`).

### Tasks and blocking

- `Block.APPLICATION` snapshots the window list when the task starts (`TaskBlocking.java:46,57-62`); a window opened while it runs is not covered.
- Key blocking relies on the glass pane owning focus (`BlockingGlassPane.java:132-133`); if `requestFocusInWindow` fails, `WHEN_IN_FOCUSED_WINDOW` shortcuts still fire. Only wheel events are literally consumed.
- `COMPONENT` blocking uses `setEnabled(false)` on a button bound to the command's `Action`; an `enabled` change during the task re-enables it (`InputBlocker.java:34-54`).
- The 300 ms indicator delay is polled by a 50 ms `javax.swing.Timer` (`BlockingGlassPane.java:35,38,159-166`); with several tasks on one window, Cancel cancels the last one only.
- `progress()` outside `[0, 1]` and `-1` throws (`RunningTask.java:72-77`); starting a task twice or in a closed scope throws (`TaskRunner.java:60-64`, `BackgroundTask.java:57-61`).

### Windows, dialogs, state, look and feel

- Enter and Escape are `WHEN_IN_FOCUSED_WINDOW` bindings on the dialog's root pane (`DialogFlow.java:106-125`), not `setDefaultButton`: a focused component that consumes Enter wins, and there is no default-button visual.
- `closeWindow()` on a dialog calls `DialogControl.cancel()` with no veto step (`StandardHandlers.java:37-44`).
- A dialog open at exit is disposed without saving its state (`DisposeWindowsStep.java:35-41`; `SaveWindowStateStep` saves the main window only).
- The dialog title falls back to the displayed name without a `title` resource (`DialogFlow.java:62-65`). Dialogs are `DOCUMENT_MODAL`; `ErrorDialog` is `APPLICATION_MODAL`.
- "Unless `configureFrame` moved it" ([source](milestone-1.md#singleframe)) is detected by comparing `getLocation()` before and after (`MainWindowFlow.java:72-80`), so `setLocation(0, 0)` or `setLocationByPlatform(true)` isn't detected.
- `FlatLafInstaller.java:56-58` reads the `theme` resource only when `ApplicationInfo instanceof DefaultApplicationInfo`; an unknown property value warns and falls through, an unknown resource value silently means `system`.
- `$X$$State` warns only for private fields declared on the view itself; inherited non-public fields from another package are silently skipped (`ViewsStep.java:208-216`, `ViewModel.java:29-31`).
- `AboutDialog.java:32-33` builds "Version 1.0" and "By imgn" by concatenation; a message pattern would translate in every word order.

### Resources and processor

- A component `icon` containing `${…}` is skipped, neither checked nor listed in `icons()`, while a command `icon` containing `${…}` is checked literally and reported missing (`ResourceChecks.java:173,422-424`).
- Icons are loaded against the concrete view class's package at runtime (`ViewBuilder.java:98`) but checked against the declaring class's package at compile time (`ResourceChecks.java:229`); cross-package inheritance resolves them differently.
- `Shared.java:95-101` errors when a class of a later round names an icon after the package's `$$Resources` was written; a class gaia defers could hit it.
- `accessible-name` and `accessible-description` require an `Accessible` field type at compile time (`ComponentProperty.java:62`); `Component` itself isn't one, though the runtime setter works on any `Component`.
- Checks beyond the [table](milestone-1.md#build-time-checks): `theme` must be one of `system`, `light`, `dark`; class properties must be strings; a `@Messages` interface must not be private, have generic methods or duplicate names.
- `Resources` caches per class, not "per class and locale" ([source](milestone-1.md#reading-resources-on-the-module-path)) (`Resources.java:32,63`); equivalent, since the locale is fixed per instance.
- `swing-processor`'s `Java.dollarName` doesn't escape a `$` inside a simple name; gaia-processing 0.1.2 now provides `ClassName.generatedName`, which does, and milestone 2 switches to it.

### Build and modules

- Root `pom.xml:60-77` relaxes the parent's enforcer (`requirePluginVersions` and `banSnapshots` off) for the snapshot plugins; the `release` profile comment at `pom.xml:107-109` is stale ("until milestone 1 gives them types").
- `swing-examples/pom.xml:60-64` puts `swing-processor` on the compile path as `provided` besides `annotationProcessorPaths`; inert.
- `swing-examples` tests have no `module-info.java`, so they run on the class path while the main module is modular; nothing in the repository runs the examples on the module path besides the README's command.
- `FakeDirectories` is never named (`AppTester.java:113-114` builds `FakePlatform` only); gaia's builder defaults to it, so "gaia's `FakePlatform` and `FakeDirectories`" ([source](milestone-1.md#test-harness)) holds in behavior.
- CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on JDK 21 and 25, but the snapshots it needs aren't on GitHub; it can't pass until the upstream releases of the [order of work](milestone-1.md#order-of-work) exist.

## F. Where milestone 1 departs from the vision without recording it

The requirements document has no "Changes to the vision" section as [milestone 2's](milestone-2.md#changes-to-the-vision) has. The code follows the requirements in each case; the vision should be amended, or the departure recorded, in a later revision.

### Unrecorded departures

- **Scope of the generated commands class.** The vision: "The generated class takes the declaring class's scope, or the view scope when the declaring class is a singleton" ([source](vision.md#ui-layer-swing)). Milestone 1: "The generated class is always `@ViewScoped`" ([source](milestone-1.md#generated-class)). The code follows milestone 1 (`CommandsStep.java`, the generated `CounterAppCommands`).
- **What the generated class holds.** The vision: "It holds the UI services, so creating a component needs nothing else" ([source](vision.md#ui-layer-swing)). Milestone 1: its direct dependencies are `CommandRegistry` and `Provider<CounterApp>` ([source](milestone-1.md#generated-class)). A refinement.
- **Resource key constants.** The vision: "Generated constants for icons, so code never looks up resources by string" ([source](vision.md#resources-and-internationalization)) and the processor row "Resource key constants" ([source](vision.md#what-the-processor-generates)). Milestone 1 neither mentions nor schedules them; icons are paths in TOML, checked at build time, and application code never names one. No code needs the constants today.
- **EDT adapters.** The vision's processor row "EDT adapters for listener interfaces", from `@EdtListener` interfaces ([source](vision.md#what-the-processor-generates)). Mentioned in neither milestone 1 nor milestone 2.
- **The ready signal.** The vision: "a *ready* signal once the event queue is idle" ([source](vision.md#core-no-swing)). Milestone 1: "after showing, one `invokeLater` calls `ready()`" ([source](milestone-1.md#startup)). A refinement.
- **Timings in resources.** The vision puts "colors and timings" in resources ([source](vision.md#principles)). Milestone 1 fixes "after 300 ms" ([source](milestone-1.md#input-blocking)) and "2 s by default" ([source](milestone-1.md#shutdown)), both Java constants (`BlockingGlassPane.java:35`, `DefaultLifecycle.java:23`). A tension between the documents; the code follows milestone 1.
- **`component()` or `content()`.** The vision's `NotesView` overrides `component()` ([source](vision.md#a-sample-application)) while its `CounterApp` and milestone 1 use `content()` ([source](vision.md#a-single-frame-application), [source](milestone-1.md#scopes)). The vision is inconsistent with itself; `content()` is the name.

### Recorded, or fine as an extension

- **Property accessors.** The vision: accessors "for the components a class holds and for common Swing types", from "a pre-generated list of Swing types" ([source](vision.md#what-the-processor-generates)). Milestone 1 supports 14 properties and says "Wider coverage comes with the pre-generated property list of the vision" ([source](milestone-1.md#injection-into-components)).
- **Shutdown event, event bus, undo and redo, OK/Cancel/Apply, the full About dialog, theme tokens.** Deferred by the [Scope table](milestone-1.md#scope) to milestones 2 and 3, as the vision's roadmap has them.
- **`SingleFrame` hooks.** The vision lists `content()`, `menuBar()` and `configureFrame(JFrame)` ([source](vision.md#ui-layer-swing)); milestone 1 adds `toolBar()` and `restoreBounds()` ([source](milestone-1.md#singleframe)). An extension.
- **`execute()`.** The vision: "Runs the command; tests drive the UI this way" ([source](vision.md#ui-layer-swing)); milestone 1 returns `boolean`, `false` when disabled ([source](milestone-1.md#boundcommand)). A refinement.
- **The processor's dependencies.** The vision's module table has gaia-processing and toml ([source](vision.md#modules)); milestone 1 adds `java.desktop` and `gaia-core`, with the reason ([source](milestone-1.md#modules)).
- **The roadmap.** All nine items of the vision's milestone 1 ([source](vision.md#milestone-1-first-useful-release)) are specified and implemented; the acceptance ports are incomplete ([B2](#b2-the-acceptance-ports-are-incomplete-and-their-reductions-unrecorded--gap)).

## G. Verified as conforming

By area, one line per topic; each was checked against the code, not inferred from names.

### Application model

- `@Application` attributes and optionality; directory resolution by qualifier, vendor and name; the identifier ([source](milestone-1.md#declaration-and-launch)).
- `$X$$Application` registered through `ServiceFiles` and found through `ServiceLoader`; `ApplicationDescriptor`, `ApplicationInfo`, `Arguments` and `DisplayLocale` exact.
- Startup steps 1 to 5 on the stated threads and in the stated order ([source](milestone-1.md#startup)): macOS properties before AWT; the root binds `ApplicationInfo`, `Arguments`, `DisplayLocale` and `Scope`; steps sorted by order then class name, the framework's at 100 and 200; look and feel, icons, main window, show; one `invokeLater` for `ready()`.
- `LookAndFeelInstaller` with a `@Provides @Default` system look and feel; a startup failure shows the error dialog if the look and feel is installed, else prints the stack trace, and exits with 1.
- The four shutdown sources; `Lifecycle` semantics, the hand-off to the `UiExecutor` and the ignored re-entry; shutdown steps 1 to 5 in order, the UI's at 100 and 200, windows disposed innermost first ([source](milestone-1.md#shutdown)).
- The core beans table; `DialogFailureHandler` choosing the focused window else the main one.
- `ExitVeto` registration through the construction hook, asked once when found twice, in construction order, `@IntoSet` singletons last by class name.
- `System.Logger` named after the reporting class, `WARNING` and `ERROR` only ([source](milestone-1.md#modules)).

### Scopes and observables

- `@WindowScoped` and `@ViewScoped` as `@ScopingAnnotation` with `CLASS` retention and no `Scope` service; hosts through `scopedChildBuilder`, the bindings of each host, a dialog's window child under the opener's view child; the scoped beans as listed ([source](milestone-1.md#scopes)).
- `View`, `Scope`, `Observable`, `Subscription` and `Property` with the exact signatures; `AtomicReference` properties with synchronous change-only notification; lazy `map` and `combine` ([source](milestone-1.md#observables)).
- `Ui.bind`; the three-point EDT delivery rule on `Ui.bind`, command states and dynamic entries ([source](milestone-1.md#delivery-to-the-edt)).
- The processor's scope order check.

### Commands

- Every `@Command` attribute and every listed compile error, with the exact warning text of an override ([source](milestone-1.md#overriding-standard-commands)); the handler rules, on the EDT.
- `CommandContext`, `Modifier` and `Trigger`: six records, the right component types, no name clash; the modifier sources per path; `CommandContext.of` ([source](milestone-1.md#declaration)).
- The generated class shape verbatim, nested naming, inherited commands ([source](milestone-1.md#generated-class)); `CommandRegistry` keying; `CommandBuilder` exact.
- Attachment at the end of a view build or at once ([source](milestone-1.md#attaching)); overrides for views resolved by type, including the macOS application menu and menu dropping.
- The `BoundCommand` API, its `IllegalStateException`s, the shared `Action`, `hideActionText` ([source](milestone-1.md#boundcommand)).
- `StandardCommands` and each behavior of its table ([source](milestone-1.md#standard-commands)); macOS `Desktop` handlers; English and French resources with platform variants.
- `&` and `&&`, no mnemonics on macOS, the `shortcut` word ([source](milestone-1.md#resources-of-a-command)); accelerators; `WHEN_IN_FOCUSED_WINDOW` registration for every attached command; the exception in development mode and in tests, the warning otherwise; one registration per window per command ([source](milestone-1.md#keyboard)).

### Menus

- The `XMenus` shape and naming; `StandardMenus` and the five standard menus; an override generates no method; `BoundMenu.with` to an immutable `MenuSpec` ([source](milestone-1.md#menus-and-toolbars)).
- `Ui.menuBar`, `Ui.popupMenu` with `setComponentPopupMenu`, `Ui.toolBar`, `MenuSpec.createMenu`; the macOS drop from menus only; separator cleanup; mnemonic checks per menu and across the bar.
- The three sealed interfaces with the exact permits; one `Separator`.
- `MenuEntry.dynamic` with the exact signature, per-menu rebuild in the build scope with EDT delivery and `pack()` when open; `Item` and `numbered`; no `menuSelected` ([source](milestone-1.md#dynamic-entries)).

### Resources, messages, checks

- The file list and merge order for `fr_BE` on macOS; platform ids from gaia; deep merge over `entries()` and `toBuilder()` ([source](milestone-1.md#files-and-merge-order)).
- `$$Resources` with `packageName()`, `open()` and `icons()`, registered through `ServiceFiles`; `Resources` through `ServiceLoader` and `Toml.parse(ByteSource)` ([source](milestone-1.md#reading-resources-on-the-module-path)).
- The `IconLoader` API, the PNG `@2x` loader, the SVG loader through `FlatSVGIcon`, the startup icon check ([source](milestone-1.md#icons)).
- The locale read once from `DISPLAY`, and `Locale.getDefault` never read again ([source](milestone-1.md#locale)).
- The component injection rules, `$X$$Injector` around `content()`, the 14 properties, `title` and `tab-title` with their exceptions, the value formats and `${}` references ([source](milestone-1.md#injection-into-components)).
- Messages with name-to-index tables, a one-time rewrite, `MessageFormat` per locale, a `@Singleton` exposed as the interface, nested naming ([source](milestone-1.md#messages)).
- `Filer.getResource` on `CLASS_OUTPUT`, `-Abe.imgn.swing.resources`; every row of the checks table; diagnostics on the element with `file:line:column`, per-file positions, the base file's position for a missing key ([source](milestone-1.md#build-time-checks)).

### Tasks

- `Task`, `TaskContext` and `TaskWork` exact; a virtual thread named after the command or `task`; delivery through the `UiExecutor`; cancel interrupts; `onCancel` only after cancellation; an unhandled failure goes to the `FailureHandler`; scope ownership and cancellation on close ([source](milestone-1.md#tasks)).
- `TaskService.run`; `Ui.run` with `IllegalArgumentException` for `COMMAND` and `COMPONENT`; a `BoundCommand` applies its block with itself and the context.
- The `Block` table, the `COMPONENT` fallbacks and the glass pane: wait cursor, indicator after 300 ms, Cancel ([source](milestone-1.md#input-blocking)); cancel and wait for 2 s at shutdown.

### Windows, dialogs, state, look and feel

- The `SingleFrame` defaults; main-window steps 1 to 7, the headless `Windows` skipping 6 and 7 only; `Window` and `Windows` exact, real and headless; `@Application` must implement `SingleFrame` ([source](milestone-1.md#singleframe)).
- The `Dialogs` API with a details area and Copy; `DialogView` exact; both `showDialog` forms, modal to and centered on the opener's window, a child of the opener's view injector, the factory resolved through the dialog's child, `buttons` given the dialog's own `StandardCommands`, Enter, Escape and close ([source](milestone-1.md#dialogs)).
- The `Ui` method table ([source](milestone-1.md#ui)).
- The window-state TOML layout, the binary-name key for dialogs, `$X$$State` field access, the off-screen rule, `window-state.toml` in `state()` with `createDirectories` and `writeAtomically`, a non-binding file discarded with a warning ([source](milestone-1.md#window-state)).
- The FlatLaf installer, the property over the resource, the exact detection commands, failure means light, the system look and feel without FlatLaf ([source](milestone-1.md#look-and-feel)).

### Harness, processor, modules

- Module and artifact names, module contents, no `java.desktop` in core, the processor's `provides` and `requires`, both processor paths tested in subprocesses; parent, group and `java.release`; the dependency versions; `register-factories` and `register-services` ([source](milestone-1.md#modules)).
- The `AppTester` API as in the document's examples, the headless root pane, `component` and `components`, `Exit` recording, settling before assertions, `FakeDialogs`, `Locale.ROOT` by default, `gaia-memfs`, `InstantSource` and `FakeTicker`, the `RepaintManager` EDT checker, the dialog mnemonic and shortcut checker ([source](milestone-1.md#test-harness)).
- `StepProcessor` with the six named steps and triggers, `processesRootElements` and `rootElements` used; tests with `TestCompiler` and `CompilationAssert` and both `withResource` overloads ([source](milestone-1.md#processor)).
- Nothing from the "Later" column of the [Scope table](milestone-1.md#scope) implemented.

## H. What acceptance needs

In the order that unblocks the most:

1. Fix [A1](#a1-a-vetoed-os-quit-is-never-cancelled--deviation) (cancel the OS Quit through `DefaultLifecycle.onCancel`), [A2](#a2-the-error-dialog-is-never-owned-by-its-window--deviation) (pass the window itself to `ErrorDialog`), [A3](#a3-the-french-standard-menus-view-and-help-share-a-mnemonic--deviation) (another French mnemonic for `view` or `help`), [A5](#a5-a-programmatic-context-reached-through-the-action-carries-modifiers--deviation) and [A6](#a6-translation-diagnostics-contain-nul-characters--deviation); decide [A4](#a4-standard-command-overrides-are-ignored-in-dialogs-built-from-a-factory--deviation) and write the decision into the [overriding section](milestone-1.md#overriding-standard-commands).
2. Fix [C1](#c1-the-focus-trackers-listener-belongs-to-no-scope--deviation) (own the `KeyboardFocusManager` listener in the application scope) and [C2](#c2-the-startup-error-dialog-hard-codes-its-texts--deviation) (use `UiTexts` when the injector exists).
3. Add the test of [B1](#b1-the-actionevent-to-commandcontext-test-doesnt-exist--gap), and tests to `swing-ui` and `swing-testing` for what only they can test; make [B3](#b3-the-tester-doesnt-itself-recheck-menus-after-a-dynamic-change--gap) and [B4](#b4-not-every-apptester-call-runs-on-the-edt--gap) true, or change the [test harness section](milestone-1.md#test-harness).
4. Port `LocaleExample3`, `LocaleExample4` and `Tabbed` as far as milestone 1 allows, and record in the requirements what the ports leave out and why ([B2](#b2-the-acceptance-ports-are-incomplete-and-their-reductions-unrecorded--gap)); move the examples' literal texts, fonts and colors into their resources ([C3](#c3-the-acceptance-ports-keep-text-fonts-and-colors-in-java--deviation)).
5. Correct the requirements for [D1](#d1-the-inherited-member-rule-is-unsatisfiable--doc) to [D8](#d8-maximized-is-written-and-restored-otherwise-than-described--doc), and add a "Changes to the vision" section covering [F](#f-where-milestone-1-departs-from-the-vision-without-recording-it).
