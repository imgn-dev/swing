# Milestone 1: audit

An audit of the `milestone-1` branch, at `3757ae4`, against the [milestone 1 requirements](milestone-1.md) and the [vision](vision.md). It records what conforms, what doesn't, and where the two documents and the code disagree, so that the branch can be brought to acceptance and the documents corrected where they, not the code, are wrong.

## Method

- The requirements document was split into eight areas (application model; scopes and observables; commands; menus and toolbars; resources, messages and build-time checks; tasks and input blocking; windows, dialogs, window state and look and feel; test harness, processor, modules and acceptance). Each area was checked sentence by sentence against the full source of the files that implement it, with the generated sources under `swing-examples/target/generated-sources` as ground truth for the processor.
- The vision was checked in two ways: its principles against the code directly (reflection, discovery, ownership of every listener and subscription, text and tuning in Java, module descriptors, the out-of-scope list, the processor table), and its feature list and roadmap against the requirements document, to find where milestone 1 departed from the vision without saying so.
- Every finding cites code as `path:line` on `3757ae4`, and the requirement it concerns as a line of `milestone-1.md` (`M1 l.n`) or `vision.md` (`V l.n`). Each headline finding was confirmed by hand.
- Build: `./mvnw -B verify` passes on JDK 21 and on JDK 25, with gaia `main` at `6256bea` (the event bus in `gaia-core`, `gaia-processor`, the standard generated names) and toml `main` at `60678e0` installed locally: 100 tests (26 in `swing-core`, 36 in `swing-processor`, 38 in `swing-examples`). `swing-ui`, `swing-flatlaf` and `swing-testing` have no tests of their own.

## Verdict

The branch implements milestone 1 faithfully in structure and in API. Every module, type, method, signature, generated class shape, resource file, TOML layout, startup and shutdown step, build-time check and error case that the requirements name exists and, with the exceptions below, behaves as written. The counter example's `CounterAppCommands` and `$CounterApp$$Application` match the document's listings verbatim; the two harness tests at M1 l.850–870 compile and pass as written; the vision's seven principles hold in the code, with two violations and a few tensions.

It is not yet acceptable: six behaviors contradict the requirements (A), four required things are missing (B), the vision's principles are broken in two places (C), and the requirements were departed from in eight places without the document being updated first, as its own rule (M1 l.5) demands (D). Section F lists the places where milestone 1 departs from the vision without recording it, which is a documentation debt rather than a code one.

Severity, throughout: **DEVIATION** behaves or is named otherwise than the document says; **GAP** is required and missing; **DOC** the code is defensible and the document must change; **NOTE** an edge the documents don't settle, recorded for later.

## A. Behaviors that contradict the requirements

| # | Requirement | Code | Finding |
|---|---|---|---|
| A1 | M1 l.138: on a vetoed OS Quit "the framework then calls `QuitResponse.cancelQuit()`" | `swing-ui/src/main/java/be/imgn/swing/ui/MainWindowFlow.java:106-112` | **DEVIATION.** The quit handler runs the `exit` command, which posts the shutdown with `invokeLater`, then posts a second runnable that calls `cancelQuit()` only if `!lifecycle.isShuttingDown()`. A veto that shows a modal dialog, as `CounterApp.canExit()` does through `Dialogs.confirm`, pumps a nested event loop: the check runs while the state is `ASKING`, sees a shutdown in progress, and does nothing; when the veto then returns `false`, the `QuitResponse` is left unanswered. `DefaultLifecycle.onCancel(Runnable)` (`swing-core/.../internal/DefaultLifecycle.java:74-77`) exists for this and has no caller outside tests. |
| A2 | M1 l.149, l.754: the error dialog is "owned by the focused window, else by the main window"; `Dialogs` shows dialogs "owned by its window" | `swing-ui/.../internal/SwingDialogs.java:24-26,41`, `swing-ui/.../internal/ErrorDialog.java:77,103` | **DEVIATION.** `SwingDialogs` passes the AWT `JFrame` or `JDialog` itself as the owner component, and `ErrorDialog` resolves the owner with `SwingUtilities.getWindowAncestor(owner)`, which starts at `getParent()`: `null` for a `JFrame`, the owner frame for a `JDialog`. The error dialog is created with no owner and `setLocationRelativeTo(null)` centers it on the screen. `confirm` and `inform` are unaffected because `JOptionPane` special-cases a `Window` argument. |
| A3 | M1 l.471, l.496: standard menus come "with text and mnemonics from the framework's resources"; duplicate mnemonics among the bar's menus throw in development mode and in tests | `swing-ui/src/main/resources/be/imgn/swing/ui/StandardMenus_fr.toml:7-8,13-14` | **DEVIATION.** `[menus.view] text = "&Affichage"` and `[menus.help] text = "&Aide"` both mark `A`. A French menu bar that holds `view()` and `help()` fails the framework's own check (`MenuBuilder.java:137-151`) in tests and in development mode, and warns otherwise. |
| A4 | M1 l.376: "The framework registers the view class's overrides in the view's `CommandRegistry` before it resolves the view", so every `StandardCommands` of the view returns the override | `swing-ui/.../ui/Ui.java:131-135`, `swing-ui/.../ui/ViewBuilder.java:71-78`, `swing-ui/.../ui/CommandRegistry.java:90` | **DEVIATION.** `showDialog(factory, create)` passes the type as `Optional.empty()`, so `ViewBuilder` skips `overrides(known, injector)` and `view.type()` is `Object.class` while the dialog's generated commands class is constructed; `CommandRegistry.bind` records an override only if `owner.isAssignableFrom(view.type())`. A dialog built through an assisted factory that overrides `ok`, `cancel` or any standard command gets the standard one. Views resolved by type conform. As written, the requirement can't be met for the factory form, since the type is unknown before `create` runs: either the document says so, or the registry re-scans its commands once the type is known. |
| A5 | M1 l.300: "From `OperatingSystem` and `Programmatic` triggers, the set is empty" | `swing-ui/.../ui/CommandAction.java:35-37,56-66` | **DEVIATION.** An `ActionEvent` whose source is neither a `JMenuItem` nor an `AbstractButton` maps to `Trigger.Programmatic`, but the event's modifiers are still read into the context, so a `Programmatic` context can carry `SHIFT`. |
| A6 | M1 l.655: diagnostics carry "the file, line and column in the message" | `swing-processor/.../processor/ResourceChecks.java:487,552,567` | **DEVIATION.** The key paths of the Translations check are joined with `"\u0000"` and printed as such: `The key commands<NUL>increment<NUL>tool-tip-text is not in the base file`. `ResourcesStepTest.java:109` asserts the prefix only. |

## B. Required and missing

| # | Requirement | Finding |
|---|---|---|
| B1 | M1 l.304: a headless test sends `new ActionEvent(item, ACTION_PERFORMED, id, when, SHIFT_MASK)` to the command's `Action` for a `JMenuItem` in a menu bar, in a popup menu and for a `JButton`, and checks the context the handler receives | **GAP.** No such test exists. `swing-ui` has no `src/test` directory; the only `ActionEvent` built in a test (`swing-examples/src/test/.../TaskExamplesTest.java:54`) uses `this` as its source and never inspects the context. The package-private `CommandAction.contextOf(ActionEvent, boolean)` was written for this test. More broadly, `swing-ui`, `swing-flatlaf` (an empty `src/test`) and `swing-testing` have no tests: the harness and all UI behavior are exercised through `swing-examples` only. |
| B2 | M1 l.3, V l.441: "accepted when SwingAppFramework's examples and Guts' Address Book demo are ported" | **GAP.** Of the 24 originals in `ogregoire/swingappframework/src/test/java/examples`, three are not ported: `LocaleExample3`, `LocaleExample4` and `Tabbed`. The Guts port drops the locale menu (`GeneralActions.java:9` says so, in code) and folds `AddressBookMainView`, `ContactsListView`, `ContactDetailView` and `ContactPictureView` into the application class (`AddressBookApp.java:31-33`); `SingleFrameExample6` shows its secondary frames as modal dialog views (`SingleFrameExample6.java:17-19`). Each reduction follows the Scope table's "Later" column (live locale switching, views in other windows, opening frames from code), but the acceptance sentence is unqualified and neither document records them. |
| B3 | M1 l.879: "The tester also checks every dialog it builds, and every menu after a dynamic section changes, for duplicate mnemonics and duplicate shortcuts" | **GAP.** `MnemonicChecker.check` is called from `AppTester.showDialog` only (`swing-testing/.../AppTester.java:287`). Menus are rechecked after a dynamic rebuild by the framework (`MenuContent.rebuild` → `built` → `MenuBuilder.check`), which throws in the tester's strict `UiMode`; the tester adds nothing for menus and sees that throw only through the default uncaught-exception handler it installs. |
| B4 | M1 l.875: "Every call on `AppTester` and on the commands it returns runs on the EDT and waits" | **GAP.** `dialogs()` (`AppTester.java:232`), `ticker()` (`:241`), `isRunning()` (`:259`) and `exitStatus()` (`:271`) don't go through `call()`; the last two settle the executor, the first two neither settle nor wait, and `FakeDialogs`' methods run on the caller's thread. |

## C. Vision principles broken in the code

| # | Principle | Code | Finding |
|---|---|---|---|
| C1 | V l.29, principle 6: "Every subscription, listener, binding and task belongs to a scope. Closing the scope releases them." | `swing-ui/.../internal/FocusTracker.java:37-45` | **DEVIATION.** `start()` adds a `PropertyChangeListener` for `permanentFocusOwner` to the JVM-global `KeyboardFocusManager`, once per application start (`Launch.java:53`), owned by no scope: there is no `stop()`, no `@PreDestroy`, no `removePropertyChangeListener`. In production the JVM exits with it; `AppTester` starts many applications in one JVM and `close()` (`AppTester.java:298-318`) closes windows and the root injector only, so every test leaks a listener holding the tracker, its `Property<Focus>` and the last focused component. The application `DisposalScope` (`Launch.java:39`, closed by `DefaultLifecycle.java:109-118`) could own it. |
| C2 | V l.27, principle 4: "Text, icons, shortcuts, mnemonics, accessible names, colors and timings go in resources" | `swing-ui/.../ui/Swing.java:60` | **DEVIATION.** The startup failure path calls `ErrorDialog.show(null, "Error", …, "Details", "Copy", "Close")` with four hard-coded English strings, even when the injector was built and `UiTexts` (whose `UiTexts_fr.toml` holds exactly these keys) is available. M1 l.123 says only "shows the error dialog". |
| C3 | Same principle, in the acceptance ports | `swing-examples/src/main/java/be/imgn/swing/examples/saf/ExitExample1.java:40`, `ApplicationExample1.java:35-36`, `SessionStorageExample1.java:66,70,73,82-83,90,93`, `PeriodicTaskExample.java:40`, `BaseScene.java:21`, `DerivedSceneA.java:17`, `DerivedSceneB.java:17` (with `ScenePanel.java:36-37`), `addressbook/TaskTestActions.java:134,138` | **DEVIATION.** User-visible labels, column headers, a tab title, titled-border titles, fonts, a foreground color, an initial status text and the messages a task shows in the glass pane are Java literals, although `text`, `tab-title`, `title`, `font` and `foreground` are supported properties and each example already has, or could have, a `.toml` and `@Messages`. `ScenePanel` also loads its icons with `getResource` instead of the `icon` property. The ports are the acceptance criterion, so they should show the framework's way. |

Everything else the principles ask for holds: no reflective member access anywhere, all runtime lookups by `Class` through inject or `ServiceLoader` (`Launcher.java:29`, `ResourceTables.java:58`, `ViewSupports.java:22,25`); no weak or soft reference in any module; every observable subscription, EDT delivery, dynamic menu section, command state, shortcut, task, window and view registration and exit veto is owned by a `DisposalScope` and released when it closes; `map` and `combine` disconnect from their sources on the last unsubscribe; input blocking is undone when the task ends even if its scope closed first; `swing-core` and the three libraries it requires are free of `java.desktop`; `META-INF/services` entries and `module-info` `provides`/`uses` agree in every module, and the examples module gets its `provides` from `register-factories` and `register-services`, as its compiled descriptor shows; none of the dropped items of V l.407–418 (component trees in resources, navigation by name, `%{}` templates, weak consumers, a module system, task groups, a widget library) is present.

## D. Code that departs from the requirements without the document being updated

M1 l.5 asks that a deviation be reported and the document updated before the implementation follows the new text. These are the places where the code is defensible and the document is wrong, incomplete or inconsistent.

| # | Requirement | Code | Finding |
|---|---|---|---|
| D1 | M1 l.381: an inherited handler or target "in another package must be `protected` or `public`" | `swing-processor/.../CommandsStep.java:118-122,182-186` require `public` | **DOC.** The rule is unsatisfiable: `q.SubCommands` is not a subclass of `p.Base`, so it can't call a `protected` member of `p.Base`. The code is right. |
| D2 | M1 l.448: "For each class whose resources have `[menus.<id>]` tables" vs l.893: "`[menus.*]` tables in the resources of any of the above"; also l.635: "every class with commands, menus, component fields with resources…" | `MenusStep.java:50-54,61-78` visits the application class, `@Command` classes and views only; `ResourceChecks` reads the files of those classes only | **DOC.** The document contradicts itself; a plain class with only `[menus.*]` tables, or with component-field tables, gets no generated class, no check and no diagnostic. The processor table (l.893) is the behavior to keep. |
| D3 | M1 l.139: tasks are cancelled and awaited "up to a timeout (2 s by default)" | `DefaultLifecycle.java:23` hard-codes `TASK_TIMEOUT = 2 s` | **DOC.** "By default" implies a knob that doesn't exist. Either name the bean or option that sets it, or drop "by default". |
| D4 | M1 l.649: scope widening is checked for "every class the processor compiles", at "constructor parameter, `@Inject` field or method parameter" | `ViewsStep.java:65-70,86-101` | **DOC.** Records and enums are skipped (`ElementKind.CLASS` only) and only the class's own `@Inject` members are examined, not those inherited from an unscoped superclass. |
| D5 | M1 l.646: "A locale file lacks a key of the base file" | `ResourceChecks.java:456`, `MessagesStep.java:115`: language files (`X_fr.toml`) only | **DOC.** Country files (`X_fr_BE.toml`) and platform variants are not checked for missing keys. |
| D6 | M1 l.444, l.642: "two commands of the class share one [shortcut]" fails the build | `ResourceChecks.java:112-158`: the map of shortcuts is local to one file | **DOC.** `save = "shortcut S"` in `X.toml` and `open = "shortcut S"` in `X_fr.toml` pass; `"shortcut S"` and `"ctrl S"` are reported as one shortcut although they differ on macOS. |
| D7 | M1 l.898: the views step "holds back" elements and gives them back as their top-level type | `ViewsStep.java:49-56` always returns `Set.of()` | **DOC.** The mechanism described is never used; only `MenusStep.heldBack` has a notion of holding back, done by re-finding classes through their resource files. |
| D8 | M1 l.809–823: `maximized = false` is written for the main frame; `maximized` is state separate from `bounds`; l.830: when bounds are rejected "the rest of its state is still restored" | `WindowStateFile.java:168-170` writes `maximized` only when true; `WindowStateSupport.java:85-89` applies `maximized` only with accepted bounds | **DOC.** Round-trips fine; the file and the sentence about rejected bounds differ from what is written and restored. |

## E. Edges the documents don't settle

Recorded so that milestone 2 or a later audit can decide them; none counts against conformance.

**Lifecycle**

- Two `requestExit` calls queued before the EDT drains both run; if the first is vetoed, the user gets a second veto prompt (`DefaultLifecycle.java:53-57,80-85`).
- `VetoRegistrar.java:26-27` silently drops an instance whose injector is not yet in `Hosts`; `ViewBuilder` registers a host after `build()`, so anything inject constructs eagerly during `build()` is lost as a veto.
- The startup exit falls back to `System::exit` when the injector itself failed (`Swing.java:76`), since no `Exit` bean exists then.
- `Launch.java:53` starts `FocusTracker` inside startup step 4, which M1 l.118 doesn't list.
- A `ShutdownStep` that throws is logged and skipped (`DefaultLifecycle.java:100-106`); unspecified.
- `DefaultLifecycle.onCancel` is public API with no caller (`DefaultLifecycle.java:74-77`).

**Scopes and observables**

- A view's framework `Scope` is closed as a resource of the window's `Scope`, after the window injector's `@PreDestroy` methods, not together with the view injector (`ViewBuilder.java:116-122`, `DisposeWindowsStep.java:37-41`).
- `Derived.get()` recomputes rather than returning the kept value when that value is `null` (`Derived.java:37-40`); `Property.of(null)` and `set(null)` throw `NullPointerException` (`AtomicProperty.java:26,36`).
- `EdtDelivery.java:33-35` clears the pending flag before `get()`, so a concurrent `set` can deliver an equal value twice; coalescing before the update runs holds.
- `BlockingGlassPane.java:175-176` subscribes to progress and message without `EdtDelivery`; safe, since those properties are only set through the `UiExecutor`.
- Subscribing or registering into a closed scope throws `IllegalStateException` (`Listeners.java:26-28`, `DisposalScope.java:77-80`), so `Ui.bind` on a closed view throws rather than doing nothing.
- `FocusTracker.java:56-66` keeps a strong reference to the last focused `JTextComponent` until focus moves again, across view scopes.
- `AppTester.close()` never closes the application `Scope` bound in the root (`Launch.java:39-40`), so tasks and subscriptions an application registers in it are not released between tests.

**Commands and menus**

- An override of a standard command declared in a `@Singleton` or `@WindowScoped` helper, not in the view class, leaves the standard command attached with the same inherited shortcut, so `ShortcutBindings` reports a duplicate and throws in tests and development mode (`CommandAttacher.java:30-37`, `CommandRegistry.java:110-125`).
- The platform's menu shortcut modifier is derived from `Platform.isMacOS()` everywhere; `Toolkit.getMenuShortcutKeyMaskEx()` is never consulted.
- `execute()`'s "enabled" folds in the `COMMAND` block state (`BoundCommand.java:218-220`), which M1 l.393 doesn't mention.
- `DynamicEntry.numbered` prefixes `"&1 "` with a trailing space (`DynamicEntry.java:37`); M1 l.526 says `&1`.
- Dropped separators are hidden (`setVisible(false)`), not removed (`MenuContent.java:75-91`); a submenu inside a dynamic section re-subscribes on every rebuild and never closes the previous subscription (`MenuBuilder.java:113`, `MenuContent.java:46`).
- `MenuBuilder.check` also reports duplicate shortcuts within a menu, so a cross-class duplicate is reported twice, at attachment and at build.
- A `[menus.file]` override is looked up in the current view class's resources (`MenuBuilder.java:38-42`), so one in the application class's file isn't seen from a dialog view; M1 l.471 says "the application's resources".
- `CommandRegistry.constructing` is a single slot (`CommandRegistry.java:34,86-89`); nested construction of two commands classes would mis-attribute commands.
- The generated name collision check of M1 l.383 is per compilation (`Shared.java:122-124`).

**Tasks and blocking**

- `Block.APPLICATION` snapshots the window list when the task starts (`TaskBlocking.java:46,57-62`); a window opened while it runs is not covered.
- Key blocking relies on the glass pane owning focus (`BlockingGlassPane.java:132-133`); if `requestFocusInWindow` fails, `WHEN_IN_FOCUSED_WINDOW` shortcuts still fire. Only wheel events are literally consumed.
- `COMPONENT` blocking uses `setEnabled(false)` on a button bound to the command's `Action`; an `enabled` change during the task re-enables it (`InputBlocker.java:34-54`).
- The 300 ms indicator delay is polled by a 50 ms `javax.swing.Timer` (`BlockingGlassPane.java:35,38,159-166`); with several tasks on one window, Cancel cancels the last one only.
- `progress()` outside `[0, 1]` and `-1` throws (`RunningTask.java:72-77`); starting a task twice or in a closed scope throws (`TaskRunner.java:60-64`, `BackgroundTask.java:57-61`).

**Windows, dialogs, state, look and feel**

- Enter and Escape are `WHEN_IN_FOCUSED_WINDOW` bindings on the dialog's root pane (`DialogFlow.java:106-125`), not `setDefaultButton`: a focused component that consumes Enter wins, and there is no default-button visual.
- `closeWindow()` on a dialog calls `DialogControl.cancel()` with no veto step (`StandardHandlers.java:37-44`).
- A dialog open at exit is disposed without saving its state (`DisposeWindowsStep.java:35-41`; `SaveWindowStateStep` saves the main window only).
- The dialog title falls back to the displayed name without a `title` resource (`DialogFlow.java:62-65`). Dialogs are `DOCUMENT_MODAL`; `ErrorDialog` is `APPLICATION_MODAL`.
- "Unless `configureFrame` moved it" compares `getLocation()` before and after (`MainWindowFlow.java:72-80`), so `setLocation(0, 0)` or `setLocationByPlatform(true)` isn't detected.
- `FlatLafInstaller.java:56-58` reads the `theme` resource only when `ApplicationInfo instanceof DefaultApplicationInfo`; an unknown property value warns and falls through, an unknown resource value silently means `system`.
- `$X$$State` warns only for private fields declared on the view itself; inherited non-public fields from another package are silently skipped (`ViewsStep.java:208-216`, `ViewModel.java:29-31`).
- `AboutDialog.java:32-33` builds "Version 1.0" and "By imgn" by concatenation; a message pattern would translate in every word order.

**Resources and processor**

- A component `icon` containing `${…}` is skipped (neither checked nor listed in `icons()`), while a command `icon` containing `${…}` is checked literally and reported missing (`ResourceChecks.java:173,422-424`).
- Icons are loaded against the concrete view class's package at runtime (`ViewBuilder.java:98`) but checked against the declaring class's package at compile time (`ResourceChecks.java:229`); cross-package inheritance resolves them differently.
- `Shared.java:95-101` errors when a class of a later round names an icon after the package's `$$Resources` was written; a class gaia defers could hit it.
- `accessible-name` and `accessible-description` require an `Accessible` field type at compile time (`ComponentProperty.java:62`); `Component` itself isn't one, though the runtime setter works on any `Component`.
- Extra checks beyond the table: `theme ∈ {system, light, dark}`, class properties must be strings, a `@Messages` interface must not be private, have generic methods or duplicate names.
- `Resources` caches per class, not per class and locale (`Resources.java:32,63`); equivalent, since the locale is fixed per instance.
- `swing-processor`'s `Java.dollarName` doesn't escape a `$` inside a simple name; gaia-processing 0.1.2 now provides `ClassName.generatedName`, which does, and milestone 2 switches to it.

**Build and modules**

- Root `pom.xml:60-77` relaxes the parent's enforcer (`requirePluginVersions` and `banSnapshots` off) for the snapshot plugins; the `release` profile comment at `pom.xml:107-109` is stale ("until milestone 1 gives them types").
- `swing-examples/pom.xml:60-64` puts `swing-processor` on the compile path as `provided` besides `annotationProcessorPaths`; inert.
- `swing-examples` tests have no `module-info.java`, so they run on the class path while the main module is modular; nothing in the repository runs the examples on the module path besides the README's command.
- `FakeDirectories` is never named (`AppTester.java:113-114` builds `FakePlatform` only); gaia's builder defaults to it, so M1 l.877 holds in behavior.
- CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on JDK 21 and 25, but the snapshots it needs aren't on GitHub; it can't pass until the upstream releases exist (M1 l.945).

## F. Where milestone 1 departs from the vision without recording it

The requirements document has no "Changes to the vision" section as [milestone 2's](milestone-2.md#changes-to-the-vision) has. The code follows the requirements in each case; the vision should be amended, or the departure recorded, in a later revision.

| Vision | Milestone 1 | Status |
|---|---|---|
| V l.129: the generated commands class "takes the declaring class's scope, or the view scope when the declaring class is a singleton" | M1 l.342: always `@ViewScoped` | Departure, unrecorded. The code follows M1 (`CommandsStep.java`, generated `CounterAppCommands`). |
| V l.126: the generated class "holds the UI services, so creating a component needs nothing else" | M1 l.345: it holds the view's `CommandRegistry` | Refinement, unrecorded. |
| V l.88, l.386: "Generated constants for icons", "Resource key constants" | Neither mentioned nor scheduled; icons are paths in TOML, checked at build time, and application code never names one | Omission, unrecorded. No code needs the constants today. |
| V l.390: "EDT adapters for listener interfaces (`@EdtListener`)" | Not mentioned in milestone 1 or 2 | Omission, unrecorded. |
| V l.387: property accessors "for common Swing types, plus a pre-generated list of Swing types" | M1 l.593: 14 properties, "wider coverage comes with the pre-generated property list" | Recorded. |
| V l.58: a ready signal "once the event queue is idle" | M1 l.119: one `invokeLater` after showing | Refinement, unrecorded. |
| V l.59: shutdown "publishes a shutdown event" | No bus in milestone 1 (M1 l.22, M2) | Recorded by the Scope table. |
| V l.110: `SingleFrame` hooks `content()`, `menuBar()`, `configureFrame(JFrame)` | M1 l.714-719 adds `toolBar()` and `restoreBounds()` | Extension; fine. |
| V l.142-148: `StandardCommands` has `undo()` and `redo()` | M1 l.403-411 omits them (undo is M3) | Recorded by the Scope table. |
| V l.138: `execute()` "runs the command" | M1 l.393: returns `boolean`, false when disabled | Refinement; fine. |
| V l.27: timings in resources | M1 l.704: "after 300 ms"; l.139: "2 s by default"; both Java constants (`BlockingGlassPane.java:35`, `DefaultLifecycle.java:23`) | Tension between the documents; the code follows M1. |
| V l.41: `swing-processor` depends on gaia-processing and toml | M1 l.32 adds `java.desktop` and `gaia-core` | Recorded, with the reason. |
| V l.223: `NotesView.component()` | M1 l.177, and V l.323 itself: `content()` | The vision is inconsistent with itself; `content()` is the name. |
| V l.159: an About dialog "filled from the application's metadata"; OK/Cancel/Apply | M1 l.17: minimal About; forms in M2 | Recorded by the Scope table. |
| V l.429-441: the milestone 1 roadmap | All nine items are specified and implemented; the acceptance ports are incomplete (B2) | — |

## G. Verified as conforming

By area, one line per topic; each was checked against the code, not inferred from names.

- **Application model**: `@Application` attributes and optionality; directory resolution by qualifier, vendor and name; the identifier; `$X$$Application` registered through `ServiceFiles` and found through `ServiceLoader`; `ApplicationDescriptor`, `ApplicationInfo`, `Arguments`, `DisplayLocale` exact; startup steps 1–5 on the stated threads and in the stated order (macOS properties before AWT; the root binds `ApplicationInfo`, `Arguments`, `DisplayLocale` and `Scope`; steps sorted by order then class name, the framework's at 100 and 200; look and feel, icons, main window, show; one `invokeLater` for `ready()`); `LookAndFeelInstaller` with a `@Provides @Default` system look and feel; a startup failure shows the error dialog if the look and feel is installed, else prints the stack trace, and exits with 1; the four shutdown sources; `Lifecycle` semantics, the hand-off to the `UiExecutor` and the ignored re-entry; shutdown steps 1–5 in order, the UI's at 100 and 200, windows disposed innermost first; the core beans table, `DialogFailureHandler` choosing the focused window else the main one; `ExitVeto` registration through the construction hook, asked once when found twice, in construction order, `@IntoSet` singletons last by class name; `System.Logger` named after the reporting class, `WARNING` and `ERROR` only.
- **Scopes and observables**: `@WindowScoped` and `@ViewScoped` as `@ScopingAnnotation` with `CLASS` retention and no `Scope` service; hosts through `scopedChildBuilder`, the bindings of each host, a dialog's window child under the opener's view child; the scoped beans as listed; `View`, `Scope`, `Observable`, `Subscription` and `Property` with the exact signatures; `AtomicReference` properties with synchronous change-only notification; lazy `map` and `combine`; `Ui.bind`; the three-point EDT delivery rule on `Ui.bind`, command states and dynamic entries; the processor's scope order check.
- **Commands**: every `@Command` attribute and every listed compile error, with the exact warning text of M1 l.373; the handler rules, on the EDT; `CommandContext`, `Modifier` and `Trigger` (six records, the right component types, no name clash); the modifier sources per path; `CommandContext.of`; the generated class shape verbatim, nested naming, inherited commands; `CommandRegistry` keying; `CommandBuilder` exact; attachment at the end of a view build or at once; overrides for views resolved by type, including the macOS application menu and menu dropping; the `BoundCommand` API, its `IllegalStateException`s, the shared `Action`, `hideActionText`; `StandardCommands` and each behavior of M1 l.403–411; macOS `Desktop` handlers; English and French resources with platform variants; `&` and `&&`, no mnemonics on macOS, the `shortcut` word; accelerators; `WHEN_IN_FOCUSED_WINDOW` registration for every attached command; the exception in development mode and in tests, the warning otherwise; one registration per window per command.
- **Menus**: the `XMenus` shape and naming; `StandardMenus` and the five standard menus; an override generates no method; `BoundMenu.with` to an immutable `MenuSpec`; `Ui.menuBar`, `Ui.popupMenu` with `setComponentPopupMenu`, `Ui.toolBar`, `MenuSpec.createMenu`; the macOS drop from menus only; separator cleanup; mnemonic checks per menu and across the bar; the three sealed interfaces with the exact permits; one `Separator`; `MenuEntry.dynamic` with the exact signature, per-menu rebuild in the build scope with EDT delivery and `pack()` when open; `Item` and `numbered`; no `menuSelected`.
- **Resources, messages, checks**: the file list and merge order for `fr_BE` on macOS; platform ids from gaia; deep merge over `entries()` and `toBuilder()`; `$$Resources` with `packageName()`, `open()` and `icons()`, registered through `ServiceFiles`; `Resources` through `ServiceLoader` and `Toml.parse(ByteSource)`; the `IconLoader` API, the PNG `@2x` loader, the SVG loader through `FlatSVGIcon`, the startup icon check; the locale read once from `DISPLAY` and `Locale.getDefault` never read again; the component injection rules, `$X$$Injector` around `content()`, the 14 properties, `title` and `tab-title` with their exceptions, the value formats and `${}` references; messages with name-to-index tables, a one-time rewrite, `MessageFormat` per locale, a `@Singleton` exposed as the interface, nested naming; `Filer.getResource` on `CLASS_OUTPUT`, `-Abe.imgn.swing.resources`; every row of the checks table; diagnostics on the element with `file:line:column`, per-file positions, the base file's position for a missing key.
- **Tasks**: `Task`, `TaskContext` and `TaskWork` exact; a virtual thread named after the command or `task`; delivery through the `UiExecutor`; cancel interrupts; `onCancel` only after cancellation; an unhandled failure goes to the `FailureHandler`; scope ownership and cancellation on close; `TaskService.run`; `Ui.run` with `IllegalArgumentException` for `COMMAND` and `COMPONENT`; a `BoundCommand` applies its block with itself and the context; the `Block` table, the `COMPONENT` fallbacks and the glass pane (wait cursor, indicator after 300 ms, Cancel); cancel and wait for 2 s at shutdown.
- **Windows, dialogs, state, look and feel**: the `SingleFrame` defaults; main-window steps 1–7, the headless `Windows` skipping 6 and 7 only; `Window` and `Windows` exact, real and headless; `@Application` must implement `SingleFrame`; the `Dialogs` API with a details area and Copy; `DialogView` exact; both `showDialog` forms, modal to and centered on the opener's window, a child of the opener's view injector, the factory resolved through the dialog's child, `buttons` given the dialog's own `StandardCommands`, Enter, Escape and close; the `Ui` method table; the window-state TOML layout, the binary-name key for dialogs, `$X$$State` field access, the off-screen rule, `window-state.toml` in `state()` with `createDirectories` and `writeAtomically`, a non-binding file discarded with a warning; the FlatLaf installer, the property over the resource, the exact detection commands, failure means light, the system look and feel without FlatLaf.
- **Harness, processor, modules**: module and artifact names, module contents, no `java.desktop` in core, the processor's `provides` and `requires`, both processor paths tested in subprocesses; parent, group and `java.release`; the dependency versions; `register-factories` and `register-services`; the `AppTester` API as in the document's examples, the headless root pane, `component` and `components`, `Exit` recording, settling before assertions, `FakeDialogs`, `Locale.ROOT` by default, `gaia-memfs`, `InstantSource` and `FakeTicker`, the `RepaintManager` EDT checker, the dialog mnemonic and shortcut checker; `StepProcessor` with the six named steps and triggers, `processesRootElements` and `rootElements` used; tests with `TestCompiler` and `CompilationAssert` and both `withResource` overloads; nothing from the Scope table's "Later" column implemented.

## H. What acceptance needs

In the order that unblocks the most:

1. Fix A1 (cancel the OS Quit through `DefaultLifecycle.onCancel`), A2 (pass the window itself to `ErrorDialog`), A3 (a French mnemonic for `view` or `help`), A5 and A6; decide A4 and write the decision into M1 l.376.
2. Fix C1 (own the `KeyboardFocusManager` listener in the application scope) and C2 (use `UiTexts` when the injector exists).
3. Add the test of M1 l.304, and tests to `swing-ui` and `swing-testing` for what only they can test; make B3 and B4 true, or change M1 l.875 and l.879.
4. Port `LocaleExample3`, `LocaleExample4` and `Tabbed` as far as milestone 1 allows, and record in M1 what the ports leave out and why (B2); move the examples' literal texts, fonts and colors into their resources (C3).
5. Correct M1 for D1–D8, and add a "Changes to the vision" section covering F.
