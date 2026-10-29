# Milestone 1: audit

An audit of the `milestone-1` branch, at `3757ae4`, against the [milestone 1 requirements](milestone-1.md) and the [vision](vision.md). It records what conforms, what doesn't, and where the two documents and the code disagree, so that the branch can be brought to acceptance and the documents corrected where they, not the code, are wrong. Each finding ends with a suggested fix, which may be to change the code, to change the document, or to drop the requirement.

## Method

- The requirements document was split into eight areas: application model; scopes and observables; commands; menus and toolbars; resources, messages and build-time checks; tasks and input blocking; windows, dialogs, window state and look and feel; test harness, processor, modules and acceptance. Each area was checked sentence by sentence against the full source of the files that implement it, with the generated sources under `swing-examples/target/generated-sources` as ground truth for the processor.
- The vision was checked in two ways: its principles against the code directly (reflection, discovery, ownership of every listener and subscription, text and tuning in Java, module descriptors, the out-of-scope list, the processor table), and its feature list and roadmap against the requirements document, to find where milestone 1 departed from the vision without saying so.
- Every finding cites code as `path:line` on `3757ae4`. A quoted requirement links to the section it comes from, as `([source](milestone-1.md#startup))`. Each headline finding was confirmed by hand.
- Build: `./mvnw -B verify` passes on JDK 21 and on JDK 25, with gaia `main` at `6256bea` (the event bus in `gaia-core`, `gaia-processor`, the standard generated names) and toml `main` at `60678e0` installed locally: 100 tests, 26 in `swing-core`, 36 in `swing-processor`, 38 in `swing-examples`. `swing-ui`, `swing-flatlaf` and `swing-testing` have no tests of their own.

## Verdict

The branch implements milestone 1 faithfully in structure and in API. Every module, type, method, signature, generated class shape, resource file, TOML layout, startup and shutdown step, build-time check and error case that the requirements name exists and, with the exceptions below, behaves as written. The counter example's `CounterAppCommands` and `$CounterApp$$Application` match the document's listings verbatim; the two harness tests of the [test harness section](milestone-1.md#test-harness) compile and pass as written; the vision's seven principles hold in the code, with three violations and a few tensions.

It is not yet acceptable: six behaviors contradict the requirements ([A](#a-behaviors-that-contradict-the-requirements)), four required things are missing ([B](#b-required-and-missing)), the vision's principles are broken in three places ([C](#c-vision-principles-broken-in-the-code)), and the requirements were departed from in eight places without the document being updated first, as its own rule demands ([D](#d-code-that-departs-from-the-requirements-without-the-document-being-updated)): "Any deviation […] is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text" ([source](milestone-1.md#milestone-1-requirements)). Section [F](#f-where-milestone-1-departs-from-the-vision-without-recording-it) lists the places where milestone 1 departs from the vision without recording it, a documentation debt rather than a code one.

Status: the six findings of section A are fixed on this branch (`f74a613` to `ab11dbd`), after the audit, and marked so in their headings. Sections B to F are as found.

Severity, throughout:

- **DEVIATION**: the code behaves or is named otherwise than the document says.
- **GAP**: required and missing.
- **DOC**: the code is defensible and the document must change.
- **NOTE**: an edge the documents don't settle, recorded for later.

## A. Behaviors that contradict the requirements

All six are fixed on this branch, in `f74a613` to `ab11dbd`; each finding says what was done.

### A1. A vetoed OS Quit is never cancelled — DEVIATION, fixed

- Requirement: "The first one that returns `false` cancels; for the OS Quit, the framework then calls `QuitResponse.cancelQuit()`" ([source](milestone-1.md#shutdown)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/MainWindowFlow.java:106-112`.
- The quit handler runs the `exit` command, which posts the shutdown with `invokeLater`, then posts a second runnable that calls `cancelQuit()` only if `!lifecycle.isShuttingDown()`. A veto that shows a modal dialog, as `CounterApp.canExit()` does through `Dialogs.confirm`, pumps a nested event loop: the check runs while the state is `ASKING`, sees a shutdown in progress, and does nothing. When the veto then returns `false`, the `QuitResponse` is left unanswered.
- `DefaultLifecycle.onCancel(Runnable)` (`swing-core/src/main/java/be/imgn/swing/internal/DefaultLifecycle.java:74-77`) exists for exactly this and has no caller outside tests.
- Fix: in the quit handler, register a one-shot cancel listener before running `exit`: `Runnable withdraw = lifecycle.onCancel(() -> { withdraw.run(); response.cancelQuit(); })`, and withdraw it when the shutdown goes through instead. When the request is ignored because a shutdown already runs (`isShuttingDown()` before the call), call `cancelQuit()` at once. The requirement can't be dropped: an unanswered `QuitResponse` leaves macOS believing the application is quitting, so a second ⌘Q is ignored.
- Applied in `f74a613`: `MainWindowFlow` answers the `QuitResponse` through `DefaultLifecycle.onCancel`, at once when a shutdown already runs, and when the `exit` command is disabled; `LifecycleTest.withdrawsACancelListener` covers the withdrawal.

### A2. The error dialog is never owned by its window — DEVIATION, fixed

- Requirement: the `FailureHandler` of the UI module "Shows the error dialog, owned by the focused window, else by the main window" ([source](milestone-1.md#shutdown)); `Dialogs` "shows standard dialogs, owned by its window" ([source](milestone-1.md#dialogs)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/internal/SwingDialogs.java:24-26,41`, `swing-ui/src/main/java/be/imgn/swing/ui/internal/ErrorDialog.java:77,103`.
- `SwingDialogs` passes the AWT `JFrame` or `JDialog` itself as the owner component, and `ErrorDialog` resolves the owner with `SwingUtilities.getWindowAncestor(owner)`, which starts at `getParent()`: `null` for a `JFrame`, the owner frame for a `JDialog`. The error dialog is created with no owner, and `setLocationRelativeTo(null)` centers it on the screen.
- `confirm` and `inform` are unaffected: `JOptionPane` special-cases a `Window` argument.
- Fix: in `ErrorDialog.show`, resolve the owner as `owner instanceof Window window ? window : SwingUtilities.getWindowAncestor(owner)`, or change the parameter to `@Nullable Window` and have `SwingDialogs` pass `real.awt()` directly. One line either way.
- Applied in `c4a3e0e`: `ErrorDialog.show` takes a window given as the owner as its own owner.

### A3. The French standard menus `view` and `help` share a mnemonic — DEVIATION, fixed

- Requirement: `StandardMenus` provides `file()`, `edit()`, `view()`, `window()` and `help()` "with text and mnemonics from the framework's resources" ([source](milestone-1.md#menus-and-toolbars)), and the framework "checks for duplicate mnemonics in each menu and among the bar's menus when it builds them: an exception in development mode and in tests, a warning otherwise" ([source](milestone-1.md#menus-and-toolbars)).
- Code: `swing-ui/src/main/resources/be/imgn/swing/ui/StandardMenus_fr.toml:7-8,13-14`.
- `[menus.view] text = "&Affichage"` and `[menus.help] text = "&Aide"` both mark `A`. A French menu bar that holds `view()` and `help()` fails the framework's own check (`MenuBuilder.java:137-151`) in tests and in development mode, and warns otherwise.
- Fix: give `help` another letter, `Aid&e` (`d` is `É&dition`'s, `n` is `Fe&nêtre`'s), or `view` one, `Afficha&ge`. Guard it: a `swing-ui` test that builds a bar from the five standard menus in every locale the framework ships and runs `MenuBuilder.check`; with [B1](#b1-the-actionevent-to-commandcontext-test-doesnt-exist--gap) it starts `swing-ui`'s test suite.
- Applied in `012a33b`: `help` is `Aid&e`; `swing-ui`'s first test, `StandardMenusResourcesTest`, parses `StandardMenus.toml` and `StandardMenus_fr.toml` and fails on a missing or shared mnemonic.

### A4. Standard-command overrides are ignored in dialogs built from a factory — DEVIATION, fixed

- Requirement: "In a view whose class, or one of its superclasses, declares the override, every `StandardCommands` instance of the view returns the user-written command. The framework registers the view class's overrides in the view's `CommandRegistry` before it resolves the view" ([source](milestone-1.md#overriding-standard-commands)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/Ui.java:131-135`, `swing-ui/src/main/java/be/imgn/swing/ui/ViewBuilder.java:71-78`, `swing-ui/src/main/java/be/imgn/swing/ui/CommandRegistry.java:90`.
- `showDialog(factory, create)` passes the type as `Optional.empty()`, so `ViewBuilder` skips `overrides(known, injector)` and `view.type()` is `Object.class` while the dialog's generated commands class is constructed; `CommandRegistry.bind` records an override only if `owner.isAssignableFrom(view.type())`. A dialog built through an assisted factory that overrides `ok`, `cancel` or any other standard command gets the standard one. Views resolved by type conform.
- As written, the requirement can't be met for the factory form, since the type is unknown before `create` runs.
- Fix: `StandardCommands.exit()` and its siblings call `registry.standard(...)` on every call (`StandardCommands.java:35-55`), so the overrides only need to be in place before anything calls them, not before resolution. In `ViewBuilder.buildView`, once `type` is empty and the instance exists, set `view.type(instance.getClass())` and call `overrides(type, injector)`, and have `CommandRegistry` re-run the override check over the commands already bound (those whose owner is assignable from the now-known type). That happens before `content()` and `buttons()`, which is where standard commands are placed. Then reword the requirement: "before it resolves the view, or, for a view created by a factory, as soon as the view exists and before its component resources are applied". A standard command called from the dialog's constructor stays the standard one; say so.
- Applied in `2fe9269`: `ViewBuilder.buildView` registers the overrides of a factory-built view as soon as it exists, and `CommandRegistry.viewTypeKnown()` records the overrides among the commands bound in its constructor; the [overriding section](milestone-1.md#overriding-standard-commands) says so.

### A5. A `Programmatic` context reached through the `Action` carries modifiers — DEVIATION, fixed

- Requirement: "From `OperatingSystem` and `Programmatic` triggers, the set is empty" ([source](milestone-1.md#declaration)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/CommandAction.java:35-37,56-66`.
- An `ActionEvent` whose source is neither a `JMenuItem` nor an `AbstractButton` maps to `Trigger.Programmatic`, but the event's modifiers are still read into the context, so a `Programmatic` context can carry `SHIFT`.
- Fix: in `contextOf`, compute the trigger first and pass `Set.of()` when it is `Programmatic`. Cover it in the [B1](#b1-the-actionevent-to-commandcontext-test-doesnt-exist--gap) test with an event whose source is a plain `Object`.
- Applied in `91203e5`: `CommandAction.contextOf` gives a `Programmatic` trigger no modifiers; the test waits for [B1](#b1-the-actionevent-to-commandcontext-test-doesnt-exist--gap).

### A6. Translation diagnostics contain NUL characters — DEVIATION, fixed

- Requirement: "Diagnostics are attached to the Java element […], with the file, line and column in the message" ([source](milestone-1.md#build-time-checks)).
- Code: `swing-processor/src/main/java/be/imgn/swing/processor/ResourceChecks.java:487,552,567`.
- The key paths of the Translations check are joined with `"\u0000"` and printed as such: `The key commands<NUL>increment<NUL>tool-tip-text is not in the base file`. `ResourcesStepTest.java:109` asserts the prefix only.
- Fix: key the `leaves` map by `List<String>` instead of a joined string, and print the path as a TOML key path, `commands.increment.tool-tip-text`, quoting a segment that isn't a bare key. Make `ResourcesStepTest` assert the whole message.
- Applied in `ab11dbd`: the paths are `List<String>` keys and print as dotted TOML keys; `ResourcesStepTest.checksTheTranslations` asserts the whole message.

## B. Required and missing

### B1. The `ActionEvent` to `CommandContext` test doesn't exist — GAP

- Requirement: "a test sends `new ActionEvent(item, ActionEvent.ACTION_PERFORMED, id, when, ActionEvent.SHIFT_MASK)` to the command's `Action`, for a `JMenuItem` in a menu bar, in a popup menu and for a `JButton`, and checks the context the handler receives" ([source](milestone-1.md#declaration)).
- No such test exists. `swing-ui` has no `src/test` directory; the only `ActionEvent` built in a test (`swing-examples/src/test/java/be/imgn/swing/examples/saf/TaskExamplesTest.java:54`) uses `this` as its source and never inspects the context. The package-private `CommandAction.contextOf(ActionEvent, boolean)` was written for this test.
- More broadly, `swing-ui`, `swing-flatlaf` (an empty `src/test`) and `swing-testing` have no tests: the harness and all UI behavior are exercised through `swing-examples` only.
- Fix: add `swing-ui/src/test/java/be/imgn/swing/ui/CommandActionTest` calling `CommandAction.contextOf` with the three sources and `SHIFT_MASK`, for both values of the macOS flag, and asserting the trigger's record type and component and the modifier set; it needs no `AppTester`. Then give `swing-ui` and `swing-testing` the tests only they can hold: `MenuBuilder` (separators, mnemonic and shortcut checks, the macOS drop, dynamic rebuilds), `ErrorDialog` ownership ([A2](#a2-the-error-dialog-is-never-owned-by-its-window--deviation-fixed)), `WindowStateFile` round trips, `Components` search order, `TestExecutor` settling. Keep the examples' tests as acceptance tests, not as the framework's only tests.

### B2. The acceptance ports are incomplete, and their reductions unrecorded — GAP

- Requirement: "The release is accepted when SwingAppFramework's examples and Guts' Address Book demo are ported" ([source](milestone-1.md#milestone-1-requirements)); the vision's acceptance line says the same ([source](vision.md#milestone-1-first-useful-release)).
- Of the 24 originals in `ogregoire/swingappframework/src/test/java/examples`, three are not ported: `LocaleExample3`, `LocaleExample4` and `Tabbed`.
- The Guts port drops the locale menu (`swing-examples/src/main/java/be/imgn/swing/examples/addressbook/GeneralActions.java:9` says so, in code) and folds `AddressBookMainView`, `ContactsListView`, `ContactDetailView` and `ContactPictureView` into the application class (`AddressBookApp.java:31-33`). `SingleFrameExample6` shows its secondary frames as modal dialog views (`SingleFrameExample6.java:17-19`).
- Each reduction follows the "Later" column of the [Scope table](milestone-1.md#scope): live locale switching, views in other windows, opening frames from code. But the acceptance sentence is unqualified, and neither document records what the ports leave out.
- Fix: port `Tabbed` now; it needs only `tab-title` and component resources, which milestone 1 has. Leave `LocaleExample3` and `LocaleExample4` to milestone 4, which brings live locale switching, and qualify the acceptance sentence: "…are ported, except the two locale examples, which need live locale switching (milestone 4), and with the Address Book's three views as one view and without its locale menu (milestones 3 and 4)". The same sentence, or a short "Ports" subsection, lists `SingleFrameExample6`'s dialogs for frames. The requirement itself stays.

### B3. The tester doesn't itself recheck menus after a dynamic change — GAP

- Requirement: "The tester also checks every dialog it builds, and every menu after a dynamic section changes, for duplicate mnemonics and duplicate shortcuts" ([source](milestone-1.md#test-harness)).
- Code: `swing-testing/src/main/java/be/imgn/swing/testing/AppTester.java:287` is the only call of `MnemonicChecker.check`, for dialogs.
- Menus are rechecked after a dynamic rebuild by the framework (`MenuContent.rebuild` → `built` → `MenuBuilder.check`), which throws in the tester's strict `UiMode`. The tester adds nothing for menus and sees that throw only through the default uncaught-exception handler it installs.
- Fix: drop the menu half of the requirement rather than duplicate the framework's check: "The framework checks menus when it builds and rebuilds them, and in tests that check throws; the tester records the failure and fails the test. The tester itself checks every dialog it builds". Then make the second half true in the tester: route `UiMode`'s strict failures through `Failures` instead of the uncaught-exception handler, so a duplicate found during a dynamic rebuild fails the test at the next tester call, as an EDT violation does.

### B4. Not every `AppTester` call runs on the EDT — GAP

- Requirement: "Every call on `AppTester` and on the commands it returns runs on the EDT and waits" ([source](milestone-1.md#test-harness)).
- Code: `swing-testing/src/main/java/be/imgn/swing/testing/AppTester.java:232` (`dialogs()`), `:241` (`ticker()`), `:259` (`isRunning()`), `:271` (`exitStatus()`).
- None goes through `call()`. The last two settle the executor; the first two neither settle nor wait, and the `FakeDialogs` methods run on the caller's thread.
- Fix: route `isRunning()` and `exitStatus()` through `call()`, so that they also see the exit the EDT may be processing. For `dialogs()` and `ticker()`, narrow the requirement: "Every call on `AppTester` that reaches the application, and every call on the commands it returns, runs on the EDT and waits. `dialogs()` and `ticker()` return the fakes, which tests configure from their own thread; the fakes are thread-safe and the application reads them on the EDT".

## C. Vision principles broken in the code

### C1. The focus tracker's listener belongs to no scope — DEVIATION

- Principle: "Everything has an owner. Every subscription, listener, binding and task belongs to a scope (application, window or view). Closing the scope releases them" ([source](vision.md#principles)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/internal/FocusTracker.java:37-45`.
- `start()` adds a `PropertyChangeListener` for `permanentFocusOwner` to the JVM-global `KeyboardFocusManager`, once per application start (`Launch.java:53`), owned by no scope: there is no `stop()`, no `@PreDestroy`, no `removePropertyChangeListener`.
- In production the JVM exits with it. `AppTester` starts many applications in one JVM, and its `close()` (`AppTester.java:298-318`) closes the windows and the root injector only, so every test leaks a listener holding the tracker, its `Property<Focus>` and the last focused component. The application `DisposalScope` (`Launch.java:39`, closed by `DefaultLifecycle.java:109-118`) could own it.
- Fix: `FocusTracker` is a `@Singleton`, and closing the root injector runs `@PreDestroy` on the singletons it created ([source](milestone-1.md#shutdown)): add `@PreDestroy void stop()` that removes the listener and the caret listener and clears `caretSource`. `AppTester.close()` closes the root, so tests stop leaking without a change there. Record the listener in the [startup steps](milestone-1.md#startup) while at it (see [E](#lifecycle)).

### C2. The startup error dialog hard-codes its texts — DEVIATION

- Principle: "Text, icons, shortcuts, mnemonics, accessible names, colors and timings go in resources, where translators and designers can edit them" ([source](vision.md#principles)).
- Code: `swing-ui/src/main/java/be/imgn/swing/ui/Swing.java:60`.
- The startup failure path calls `ErrorDialog.show(null, "Error", …, "Details", "Copy", "Close")` with four hard-coded English strings, even when the injector was built and `UiTexts`, whose `UiTexts_fr.toml` holds exactly these keys, is available. The requirement says only that a failure "shows the error dialog if the look and feel is installed" ([source](milestone-1.md#startup)).
- Fix: when the `Optional<Injector>` is present, take `UiTexts` from it and call the `UiTexts` overload of `ErrorDialog.show`; keep the English literals for a failure in step 2, before any injector exists, and say so in the requirement: "shows the error dialog, in the application's language once the injector exists and in English before".

### C3. The acceptance ports keep text, fonts and colors in Java — DEVIATION

- Principle: the same ([source](vision.md#principles)). The ports are the acceptance criterion, so they should show the framework's way.
- Code, in `swing-examples/src/main/java/be/imgn/swing/examples/`:
  - `saf/ExitExample1.java:40`: the label `" Close the Window to Exit "`; no `ExitExample1.toml`.
  - `saf/ApplicationExample1.java:35-36`: `"Hello World"` and `new Font("LucidaSans", Font.PLAIN, 32)`; `font` is a supported property.
  - `saf/SessionStorageExample1.java:66,70,73,82-83,90,93`: column headers, the tab title `"all colors"`, an explanatory label, the `.darker()` labels, a font and `Color.WHITE`; `tab-title`, `font` and `foreground` are supported properties.
  - `saf/PeriodicTaskExample.java:40`: the initial status `"Ready..."`, while `ExampleMessages` is injected on the same lines.
  - `saf/BaseScene.java:21`, `saf/DerivedSceneA.java:17`, `saf/DerivedSceneB.java:17`, with `saf/ScenePanel.java:36-37`: titled-border titles passed as strings, and icons loaded with `getResource` instead of the `icon` property; `title` is a supported `JComponent` property.
  - `addressbook/TaskTestActions.java:134,138`: `"Sleep chunk #…"` and `"Task done!"`, shown by the blocking glass pane, while `AddressBookMessages` exists.
- Fix: give each example the `.toml` it lacks and move the labels, fonts, tab title and foreground there as component properties (`[label] text = …`, `font = …`; `[tabs] tab-title`), the status and task texts to `ExampleMessages` and `AddressBookMessages`, and the scene titles and icons to `[scene] title`/`icon` on a `ScenePanel` field, or to the scene class's own resources merged along the hierarchy, which is what `BaseScene`/`DerivedSceneA`/`DerivedSceneB` exist to show. The demo's subject colors (`Color.RED`…) and the Mars images are data and stay in code. Alternatively, drop the requirement for the ports by saying in the acceptance sentence that they keep the originals' literal texts; not recommended, since the ports are the examples users read.

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
- Fix: change the requirement to "must be `public`, since the generated class of the subclass is not a subclass of the declaring class and can't reach a `protected` member in another package".

### D2. The document disagrees with itself on which classes get `XMenus` and checks — DOC

- Requirement, one way: "For each class whose resources have `[menus.<id>]` tables, the processor generates a `<Class>Menus` class" ([source](milestone-1.md#menus-and-toolbars)); "For every class with commands, menus, component fields with resources, or a `@Messages` interface, the processor reads the class's TOML files" ([source](milestone-1.md#build-time-checks)).
- Requirement, the other way: the Menus step is triggered by "`[menus.*]` tables in the resources of any of the above", the application, command, message and view classes ([source](milestone-1.md#processor)).
- Code: `MenusStep.java:50-54,61-78` visits the application class, `@Command` classes and views only; `ResourceChecks` reads the files of those classes only.
- A plain class with only `[menus.*]` tables, or with component-field tables, gets no generated class, no check and no diagnostic.
- Fix: keep the processor table's rule, since a generated `XMenus` needs a view scope anyway, and reword the two other sentences to "For each application, command or view class whose resources have `[menus.<id>]` tables" and "For every application, command or view class, and every `@Messages` interface, the processor reads…". So that a stray file doesn't vanish silently, have the Resources step warn on a TOML file in a package it processes whose base name matches a class it doesn't: "`Helper.toml` belongs to no class the processor generates for".

### D3. The task timeout isn't configurable — DOC

- Requirement: "Cancel running tasks and wait for them, up to a timeout (2 s by default)" ([source](milestone-1.md#shutdown)).
- Code: `DefaultLifecycle.java:23` hard-codes `TASK_TIMEOUT = 2 s`.
- "By default" implies a knob that doesn't exist.
- Fix: drop "by default": "up to a timeout of 2 s". A configurable timeout, if ever needed, belongs to milestone 2's settings records.

### D4. The scope widening check skips records, enums and inherited members — DOC

- Requirement: "Every class the processor compiles is checked", at every "injection point (constructor parameter, `@Inject` field or method parameter)" ([source](milestone-1.md#build-time-checks)).
- Code: `ViewsStep.java:65-70,86-101`.
- Records and enums are skipped (`ElementKind.CLASS` only), and only the class's own `@Inject` members are examined, not those inherited from an unscoped superclass.
- Fix: in the code, walk `elements.getAllMembers(type)` for `@Inject` fields and methods, so an inherited injection point is checked against the subclass's scope; that is cheap and closes a real hole. In the document, say "Every class the processor compiles", and note that records and enums aren't beans of a scope and are not checked.

### D5. Only language files are checked for missing keys — DOC

- Requirement: an error when "A locale file lacks a key of the base file, or has a key the base file lacks" ([source](milestone-1.md#build-time-checks)).
- Code: `ResourceChecks.java:456` and `MessagesStep.java:115` check language files (`X_fr.toml`) only.
- Country files (`X_fr_BE.toml`) and platform variants are not checked for missing keys.
- Fix: narrow the requirement to what is right: "A language file (`X_fr.toml`) lacks a key of the base file; any locale or platform file has a key the base file lacks". A country file overrides its language file key by key and legitimately holds a few keys; a platform file likewise. The code already does this.

### D6. Duplicate shortcuts are checked per file — DOC

- Requirement: "Two commands with the same shortcut in one class's resources fail the build" ([source](milestone-1.md#keyboard)); an error when "two commands of the class share one" ([source](milestone-1.md#build-time-checks)).
- Code: `ResourceChecks.java:112-158`; the map of shortcuts is local to one file.
- `save = "shortcut S"` in `X.toml` and `open = "shortcut S"` in `X_fr.toml` pass, while `"shortcut S"` and `"ctrl S"` are reported as one shortcut although they differ on macOS.
- Fix: in the document, "in one file of a class's resources", which matches the per-file position rule of the same section. In the code, compare the written key stroke with `shortcut` kept as its own modifier, so that `shortcut S` and `ctrl S` are distinct, as they are on macOS; or, better, check the merged table per platform (`base`, `base + windows`, `base + macos`, `base + linux`) and report at the position in the file that set the colliding key. The second is the check users expect; it costs one merge per platform.

### D7. The views step never holds an element back — DOC

- Requirement: "An element it holds back is given back as its top-level type, since a step only gets root elements and elements with its annotations" ([source](milestone-1.md#processor)).
- Code: `ViewsStep.java:49-56` always returns `Set.of()`.
- The mechanism described is never used. Only `MenusStep.heldBack` has a notion of holding back, done by re-finding classes through their resource files.
- Fix: drop the sentence, and describe what the Menus step does instead: "A view that injects its own generated menus class can't be resolved until that class exists; the Menus step finds such classes again through their resource files in later rounds rather than by holding elements back".

### D8. `maximized` is written and restored otherwise than described — DOC

- Requirement: the example file has `maximized = false` under `[windows.main]`, and the table lists `maximized` as state separate from `bounds`; when saved bounds are rejected, "the rest of its state is still restored" ([source](milestone-1.md#window-state)).
- Code: `WindowStateFile.java:168-170` writes `maximized` only when true; `WindowStateSupport.java:85-89` applies `maximized` only with accepted bounds.
- Round-trips fine; the file and the sentence about rejected bounds differ from what is written and restored.
- Fix: write `maximized` always, so the file reads as the example shows; and keep restoring it only with accepted bounds, since maximizing a frame whose normal bounds were rejected would maximize it on a screen chosen by the toolkit, then restore it to nowhere. Say so: "the window opens as with no saved state, not maximized, and its components' state is still restored".

## E. Edges the documents don't settle

Recorded so that milestone 2 or a later audit can decide them; none counts against conformance. Where a fix is obvious it follows the finding.

### Lifecycle

- Two `requestExit` calls queued before the EDT drains both run; if the first is vetoed, the user gets a second veto prompt (`DefaultLifecycle.java:53-57,80-85`). Fix: move the `RUNNING` check into a `REQUESTED` state set by `requestExit` itself, so the second request is ignored as the document intends.
- `VetoRegistrar.java:26-27` silently drops an instance whose injector is not yet in `Hosts`; `ViewBuilder` registers a host after `build()`, so anything inject constructs eagerly during `build()` is lost as a veto. Fix: register the host before resolving anything through it, or have `Hosts` fail loudly on an unknown injector.
- The startup exit falls back to `System::exit` when the injector itself failed (`Swing.java:76`), since no `Exit` bean exists then. Fix: none needed; say so in the [startup section](milestone-1.md#startup).
- `Launch.java:53` starts `FocusTracker` inside startup step 4, which the [startup steps](milestone-1.md#startup) don't list. Fix: list it, between the icon check and the main window.
- A `ShutdownStep` that throws is logged and skipped (`DefaultLifecycle.java:100-106`); unspecified. Fix: specify it; the behavior is the right one.
- `DefaultLifecycle.onCancel` is public API with no caller (`DefaultLifecycle.java:74-77`). Fix: [A1](#a1-a-vetoed-os-quit-is-never-cancelled--deviation-fixed) gives it its caller.

### Scopes and observables

- A view's framework `Scope` is closed as a resource of the window's `Scope`, after the window injector's `@PreDestroy` methods, not together with the view injector (`ViewBuilder.java:116-122`, `DisposeWindowsStep.java:37-41`). Fix: close each `DisposalScope` right after its own injector, view first, then window, and specify the order: "the view's injector, the view's `Scope`, the window's injector, the window's `Scope`".
- `Derived.get()` recomputes rather than returning the kept value when that value is `null` (`Derived.java:37-40`); `Property.of(null)` and `set(null)` throw `NullPointerException` (`AtomicProperty.java:26,36`). Fix: specify that observables never hold `null` (an `Optional` does), which both pieces of code already assume; then `Derived` can test the connection alone.
- `EdtDelivery.java:33-35` clears the pending flag before `get()`, so a concurrent `set` can deliver an equal value twice; coalescing before the update runs holds. Fix: none needed; targets must tolerate an equal value, as any Swing setter does.
- `BlockingGlassPane.java:175-176` subscribes to progress and message without `EdtDelivery`; safe, since those properties are only set through the `UiExecutor`. Fix: none, or a comment saying why.
- Subscribing or registering into a closed scope throws `IllegalStateException` (`Listeners.java:26-28`, `DisposalScope.java:77-80`), so `Ui.bind` on a closed view throws rather than doing nothing. Fix: specify it; failing fast is the better behavior.
- `FocusTracker.java:56-66` keeps a strong reference to the last focused `JTextComponent` until focus moves again, across view scopes. Fix: in `follow`, also drop the caret source when its window is disposed; or accept it, since the next focus change clears it.
- `AppTester.close()` never closes the application `Scope` bound in the root (`Launch.java:39-40`), so tasks and subscriptions an application registers in it are not released between tests. Fix: have `AppTester.close()` run the shutdown's steps 3 to 5 through `DefaultLifecycle.close()` instead of closing the injector itself, which closes the application scope too.

### Commands and menus

- An override of a standard command declared in a `@Singleton` or `@WindowScoped` helper, not in the view class, leaves the standard command attached with the same inherited shortcut, so `ShortcutBindings` reports a duplicate and throws in tests and development mode (`CommandAttacher.java:30-37`, `CommandRegistry.java:110-125`). Fix: define "where the override applies" as the view class and its superclasses only, and have the processor warn on an override declared elsewhere: "Command `exit` of `p.Helper` overrides the standard command `exit`, but `p.Helper` is not a view: the override applies nowhere".
- The platform's menu shortcut modifier is derived from `Platform.isMacOS()` everywhere; `Toolkit.getMenuShortcutKeyMaskEx()` is never consulted. Fix: none; the platform is what tests fake, and the two agree on real platforms. Say so where the document says "the platform's menu shortcut modifier" ([source](milestone-1.md#declaration)).
- `execute()`'s "enabled" folds in the `COMMAND` block state (`BoundCommand.java:218-220`), which the [`BoundCommand` table](milestone-1.md#boundcommand) doesn't mention. Fix: add "and the command is not blocked by its own running task" to the table.
- `DynamicEntry.numbered` prefixes `"&1 "` with a trailing space (`DynamicEntry.java:37`); the [dynamic entries section](milestone-1.md#dynamic-entries) says `&1`. Fix: write "`&1 ` to `&9 `, a space after the digit" in the document; the space is what recent-file menus show.
- Dropped separators are hidden (`setVisible(false)`), not removed (`MenuContent.java:75-91`); a submenu inside a dynamic section re-subscribes on every rebuild and never closes the previous subscription (`MenuBuilder.java:113`, `MenuContent.java:46`). Fix: for the first, say "hidden" in the document, since hiding keeps the rebuild's index arithmetic simple. For the second, give each dynamic section its own child `DisposalScope`, closed before each rebuild, so the subscriptions of a replaced submenu go with it.
- `MenuBuilder.check` also reports duplicate shortcuts within a menu, so a cross-class duplicate is reported twice, at attachment and at build. Fix: drop the shortcut check from `MenuBuilder.check`; attachment already covers every command of the window, placed in a menu or not.
- A `[menus.file]` override is looked up in the current view class's resources (`MenuBuilder.java:38-42`), so one in the application class's file isn't seen from a dialog view, while the text says "A `[menus.file]` table in the application's resources overrides their text" ([source](milestone-1.md#menus-and-toolbars)). Fix: look the override up in the application class's resources first, then the view class's, so a renamed File menu is renamed everywhere; and say "in the resources of the application class or of the view class".
- `CommandRegistry.constructing` is a single slot (`CommandRegistry.java:34,86-89`); nested construction of two commands classes would mis-attribute commands. Fix: make it a stack, or pass the instance explicitly: `registry.command(owner, id, this)` from the generated constructor.
- The check that "Two classes in one package that would produce the same name are a compile error" ([source](milestone-1.md#inheritance-and-naming)) is per compilation (`Shared.java:122-124`). Fix: also ask `GeneratedSources.isAvailable(name)` before writing, which sees classes of earlier compilations on the class path.

### Tasks and blocking

- `Block.APPLICATION` snapshots the window list when the task starts (`TaskBlocking.java:46,57-62`); a window opened while it runs is not covered. Fix: have `WindowRegistry` tell `InputBlocker` of windows opened while an application block is active, and cover them on opening; or specify the snapshot, since a blocked application can hardly open a window except from the task's own callbacks.
- Key blocking relies on the glass pane owning focus (`BlockingGlassPane.java:132-133`); if `requestFocusInWindow` fails, `WHEN_IN_FOCUSED_WINDOW` shortcuts still fire. Only wheel events are literally consumed. Fix: also install a `KeyEventDispatcher` on the `KeyboardFocusManager` for the duration of the block, scoped to the blocked windows, which consumes key events before any input map sees them; and `consume()` the mouse events for the avoidance of doubt.
- `COMPONENT` blocking uses `setEnabled(false)` on a button bound to the command's `Action`; an `enabled` change during the task re-enables it (`InputBlocker.java:34-54`). Fix: for a component that shares the command's `Action`, block the command instead, as for `MenuSelection`; the visible effect is the same button disabled, and it can't be undone by the `Action`.
- The 300 ms indicator delay is polled by a 50 ms `javax.swing.Timer` (`BlockingGlassPane.java:35,38,159-166`); with several tasks on one window, Cancel cancels the last one only. Fix: specify "cancels the task that blocked last", or cancel all of them; one `Timer` with a 300 ms initial delay and no repeat replaces the poll if the `Ticker` is dropped for this, but the `Ticker` is what makes it testable; keep the poll and say "within 350 ms".
- `progress()` outside `[0, 1]` and `-1` throws (`RunningTask.java:72-77`); starting a task twice or in a closed scope throws (`TaskRunner.java:60-64`, `BackgroundTask.java:57-61`). Fix: specify these three errors; they are right.

### Windows, dialogs, state, look and feel

- Enter and Escape are `WHEN_IN_FOCUSED_WINDOW` bindings on the dialog's root pane (`DialogFlow.java:106-125`), not `setDefaultButton`: a focused component that consumes Enter wins, and there is no default-button visual. Fix: also call `rootPane.setDefaultButton` on the OK button created for the dialog, which gives the visual and the same precedence; keep the bindings for a dialog whose `buttons()` leaves `ok()` out.
- `closeWindow()` on a dialog calls `DialogControl.cancel()` with no veto step (`StandardHandlers.java:37-44`). Fix: specify it: a dialog has no vetoes in milestone 1; "after its vetoes" applies to the main frame.
- A dialog open at exit is disposed without saving its state (`DisposeWindowsStep.java:35-41`; `SaveWindowStateStep` saves the main window only). Fix: have `SaveWindowStateStep` save every registered window, dialogs included, since the registry already lists them.
- The dialog title falls back to the displayed name without a `title` resource (`DialogFlow.java:62-65`). Dialogs are `DOCUMENT_MODAL`; `ErrorDialog` is `APPLICATION_MODAL`. Fix: specify both the fallback and the modality types.
- "Unless `configureFrame` moved it" ([source](milestone-1.md#singleframe)) is detected by comparing `getLocation()` before and after (`MainWindowFlow.java:72-80`), so `setLocation(0, 0)` or `setLocationByPlatform(true)` isn't detected. Fix: also treat `isLocationByPlatform()` as moved; for `(0, 0)`, set the fresh frame's location to a sentinel such as `(-1, -1)` before calling `configureFrame`, or document the limit.
- `FlatLafInstaller.java:56-58` reads the `theme` resource only when `ApplicationInfo instanceof DefaultApplicationInfo`; an unknown property value warns and falls through, an unknown resource value silently means `system`. Fix: expose the application class's plain resources through `ApplicationInfo` (a `String theme()` or a generic `Optional<String> resource(String key)`), and warn on an unknown resource value as on an unknown property value; the processor already rejects unknown values at build time, so the warning only matters for a property.
- `$X$$State` warns only for private fields declared on the view itself; inherited non-public fields from another package are silently skipped (`ViewsStep.java:208-216`, `ViewModel.java:29-31`). Fix: warn on those too, with the reason: "`split` of `p.Base` is not public, so the state of `q.View` can't save it".
- `AboutDialog.java:32-33` builds "Version 1.0" and "By imgn" by concatenation; a message pattern would translate in every word order. Fix: make `UiTexts` a `@Messages` interface with `version(String version)` and `by(String vendor)` patterns, which the framework's own processor can generate as it does for applications.

### Resources and processor

- A component `icon` containing `${…}` is skipped, neither checked nor listed in `icons()`, while a command `icon` containing `${…}` is checked literally and reported missing (`ResourceChecks.java:173,422-424`). Fix: resolve `${…}` references within the file before checking, as the runtime resolves them after merging, and check and list the resolved path; a reference to a key of another file stays unchecked, and is reported as such.
- Icons are loaded against the concrete view class's package at runtime (`ViewBuilder.java:98`) but checked against the declaring class's package at compile time (`ResourceChecks.java:229`); cross-package inheritance resolves them differently. Fix: record, in the merged table, the class each table came from, and load each icon against its own class's package; or specify that icon paths are relative to the package of the class whose file names them, which is what the processor checks, and make the runtime follow.
- `Shared.java:95-101` errors when a class of a later round names an icon after the package's `$$Resources` was written; a class gaia defers could hit it. Fix: write `$$Resources` in the last round only, as `ServiceFiles` writes its files, so every class of the package has been seen.
- `accessible-name` and `accessible-description` require an `Accessible` field type at compile time (`ComponentProperty.java:62`); `Component` itself isn't one, though the runtime setter works on any `Component`. Fix: accept any `Component` field, since `Component.getAccessibleContext()` exists on all of them.
- Checks beyond the [table](milestone-1.md#build-time-checks): `theme` must be one of `system`, `light`, `dark`; class properties must be strings; a `@Messages` interface must not be private, have generic methods or duplicate names. Fix: add them to the table.
- `Resources` caches per class, not "per class and locale" ([source](milestone-1.md#reading-resources-on-the-module-path)) (`Resources.java:32,63`); equivalent, since the locale is fixed per instance. Fix: write "per class; the locale is fixed per `Resources` instance", or leave it for milestone 4, whose locale switching will decide.
- `swing-processor`'s `Java.dollarName` doesn't escape a `$` inside a simple name; gaia-processing 0.1.2 now provides `ClassName.generatedName`, which does, and milestone 2 switches to it. Fix: already scheduled in [milestone 2](milestone-2.md#processor).

### Build and modules

- Root `pom.xml:60-77` relaxes the parent's enforcer (`requirePluginVersions` and `banSnapshots` off) for the snapshot plugins; the `release` profile comment at `pom.xml:107-109` is stale ("until milestone 1 gives them types"). Fix: restore the enforcer rules when the upstream releases exist; fix the comment now.
- `swing-examples/pom.xml:60-64` puts `swing-processor` on the compile path as `provided` besides `annotationProcessorPaths`; inert. Fix: remove the `provided` dependency.
- `swing-examples` tests have no `module-info.java`, so they run on the class path while the main module is modular; nothing in the repository runs the examples on the module path besides the README's command. Fix: add a test `module-info.java` to `swing-examples`, as `swing-processor` has, so the acceptance tests exercise the module path; and an `exec` profile that runs an example as the README says.
- `FakeDirectories` is never named (`AppTester.java:113-114` builds `FakePlatform` only); gaia's builder defaults to it, so "gaia's `FakePlatform` and `FakeDirectories`" ([source](milestone-1.md#test-harness)) holds in behavior. Fix: none; the sentence describes the result.
- CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on JDK 21 and 25, but the snapshots it needs aren't on GitHub; it can't pass until the upstream releases of the [order of work](milestone-1.md#order-of-work) exist. Fix: until then, have the workflow check out and install the four upstream repositories at pinned commits before building, so CI tells the truth; drop that step at the first releases.

## F. Where milestone 1 departs from the vision without recording it

The requirements document has no "Changes to the vision" section as [milestone 2's](milestone-2.md#changes-to-the-vision) has. The code follows the requirements in each case. The fix for all of them is one such section in the requirements, with the dispositions suggested below; the vision itself is amended when its next revision comes.

### Unrecorded departures

- **Scope of the generated commands class.** The vision: "The generated class takes the declaring class's scope, or the view scope when the declaring class is a singleton" ([source](vision.md#ui-layer-swing)). Milestone 1: "The generated class is always `@ViewScoped`" ([source](milestone-1.md#generated-class)). The code follows milestone 1 (`CommandsStep.java`, the generated `CounterAppCommands`). Fix: record it, with milestone 1's reason: the class builds components for a view through the view's `CommandRegistry`, so no wider scope could hold it.
- **What the generated class holds.** The vision: "It holds the UI services, so creating a component needs nothing else" ([source](vision.md#ui-layer-swing)). Milestone 1: its direct dependencies are `CommandRegistry` and `Provider<CounterApp>` ([source](milestone-1.md#generated-class)). Fix: record it as a refinement; the registry is the UI service it needs.
- **Resource key constants.** The vision: "Generated constants for icons, so code never looks up resources by string" ([source](vision.md#resources-and-internationalization)) and the processor row "Resource key constants" ([source](vision.md#what-the-processor-generates)). Milestone 1 neither mentions nor schedules them; icons are paths in TOML, checked at build time, and application code never names one. Fix: drop them from the vision: the goal, "code never looks up resources by string", is met without them, since commands and components get their icons from their resources and no API takes an icon path. If an API ever does (the frame icon of milestone 2 takes a resource key), reconsider then.
- **EDT adapters.** The vision's processor row "EDT adapters for listener interfaces", from `@EdtListener` interfaces ([source](vision.md#what-the-processor-generates)). Mentioned in neither milestone 1 nor milestone 2. Fix: drop the row, or schedule it in milestone 4 with the inspector; `EdtDelivery` and `@OnEdt` cover the framework's own needs.
- **The ready signal.** The vision: "a *ready* signal once the event queue is idle" ([source](vision.md#core-no-swing)). Milestone 1: "after showing, one `invokeLater` calls `ready()`" ([source](milestone-1.md#startup)). Fix: record it as a refinement; one posted event after the show is what "idle" can mean on the EDT.
- **Timings in resources.** The vision puts "colors and timings" in resources ([source](vision.md#principles)). Milestone 1 fixes "after 300 ms" ([source](milestone-1.md#input-blocking)) and "2 s by default" ([source](milestone-1.md#shutdown)), both Java constants (`BlockingGlassPane.java:35`, `DefaultLifecycle.java:23`). Fix: record that the framework's own timings are constants until an application needs to tune one, and that application timings go in the application's resources; the principle is about what applications write.
- **`component()` or `content()`.** The vision's `NotesView` overrides `component()` ([source](vision.md#a-sample-application)) while its `CounterApp` and milestone 1 use `content()` ([source](vision.md#a-single-frame-application), [source](milestone-1.md#scopes)). The vision is inconsistent with itself. Fix: `content()` in the vision's `NotesView`.

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

1. Done: [A1](#a1-a-vetoed-os-quit-is-never-cancelled--deviation-fixed), [A2](#a2-the-error-dialog-is-never-owned-by-its-window--deviation-fixed), [A3](#a3-the-french-standard-menus-view-and-help-share-a-mnemonic--deviation-fixed), [A5](#a5-a-programmatic-context-reached-through-the-action-carries-modifiers--deviation-fixed) and [A6](#a6-translation-diagnostics-contain-nul-characters--deviation-fixed) as suggested, and the [A4](#a4-standard-command-overrides-are-ignored-in-dialogs-built-from-a-factory--deviation-fixed) fix with its requirement reworded.
2. Fix [C1](#c1-the-focus-trackers-listener-belongs-to-no-scope--deviation) and [C2](#c2-the-startup-error-dialog-hard-codes-its-texts--deviation).
3. Add the tests of [B1](#b1-the-actionevent-to-commandcontext-test-doesnt-exist--gap); apply the [B3](#b3-the-tester-doesnt-itself-recheck-menus-after-a-dynamic-change--gap) and [B4](#b4-not-every-apptester-call-runs-on-the-edt--gap) changes to the tester and to the [test harness section](milestone-1.md#test-harness).
4. Port `Tabbed`, and qualify the acceptance sentence as [B2](#b2-the-acceptance-ports-are-incomplete-and-their-reductions-unrecorded--gap) suggests; move the examples' literal texts, fonts and colors into their resources ([C3](#c3-the-acceptance-ports-keep-text-fonts-and-colors-in-java--deviation)).
5. Correct the requirements for [D1](#d1-the-inherited-member-rule-is-unsatisfiable--doc) to [D8](#d8-maximized-is-written-and-restored-otherwise-than-described--doc), and add a "Changes to the vision" section with the dispositions of [F](#f-where-milestone-1-departs-from-the-vision-without-recording-it).
