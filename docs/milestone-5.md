# Milestone 5: requirements

This is the requirement document of the optional-modules milestone, described in the [vision](vision.md#milestone-5-optional-modules): wizards and docking. It delivers what milestones [2](milestone-2.md#scope) and [3](milestone-3.md#scope) left for it: the wizard that the Address Book's `ContactWizardDialog` builds by hand, which the [ideas](ideas.md#11-wizard) name as the first test case of `swing-wizard`, and the Address Book's three views, which milestone 3 kept as one view until docking. The release is accepted when the [acceptance](#acceptance) examples and their tests pass.

Every part of every sentence in this document is a requirement, and must be followed exactly: names, packages, signatures, behaviors, orders and error cases. Any deviation, whether a part that can't be implemented as written, a part that contradicts another, or a gap, is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text.

Milestone 5 builds on milestones 1 to 4 as implemented. Everything they specify stays, except where a section below changes it; [Changes to milestones 1 to 4](#changes-to-milestones-1-to-4) lists those changes.

---

## Scope

| In milestone 5 | Later |
|---|---|
| `swing-wizard`: dialogs whose content is a sequence of steps, with Previous, Next and Finish, each step validated before the next | Steps that choose the following steps (branching); steps that are views of their own, with their own scopes; wizards in frames |
| `swing-docking`: views docked in a frame, split and tabbed, rearranged by the user, opened and closed while the frame is open, their layout saved | Floating docked views, auto-hidden views, maximized views; docks in dialogs; moving views between frames; named layouts (perspectives); restoring views opened through a factory; views the user can't close |
| The Address Book's three views docked, with a Views menu and a picture view per contact | — |
| The test harness for both | — |

Milestone 5 adds two modules, `swing-wizard` and `swing-docking`, both optional: an application that doesn't need them doesn't depend on them. `swing-ui` gets what they build on: views opened in an open window and closed before it, the state of several views per window, and two additions to `Form`. `swing-processor` gets the checks below and the assisted factories of dock views. `swing-testing` gets a way to resolve beans in an added view; each module brings its own replacements in the harness, as `swing-desktop` does (see [Test harness](#test-harness)). Core doesn't change.

The libraries are those of milestone 4: the parent 2026-11-01, gaia 0.1.2, inject 0.1.1 and toml 0.1.0, from Maven Central, and FlatLaf 3.7.2. `swing-docking` adds ModernDocking 1.4.9 (`io.github.andrewauclair:modern-docking-api` and `io.github.andrewauclair:modern-docking-multi-app`, MIT License), whose versions the parent doesn't manage. Milestone 5 needs no upstream change (see [Upstream changes](#upstream-changes)).

---

## Views opened in an open window

Until milestone 4, a window holds one view, built when the window opens: its frame view or its dialog view. From milestone 5 on, a window may also hold views opened while it is open, which `swing-docking` opens; they are its *added views*. The view the window opened with is its *own view*.

- **Building.** An added view is built as a window's own view is ([milestone 3](milestone-3.md#frame-views)), in this order: a view scope and a child injector of its own, under the window's; the overrides of its class's commands; the view, resolved by its class through its child injector, or created through the assisted factory that the processor generates for a view whose constructor has `@Assisted` parameters, resolved there, as `Frames.open(Class<F>, Function)` does; its component resources; its content, placed by the module that opens it; its window's component orientation, applied to its content; its tab titles; the end of its build, which attaches its commands and registers the instances built meanwhile on the bus; the [accessibility checks](milestone-4.md#accessibility-checks), over its content only, in development mode and tests; and last its component state, restored as [Window state](#window-state) describes.
- **When.** An added view is built on the EDT, never while another view of any window is being built: opening one then throws `IllegalStateException`, as does opening one in a window that is closed or closing. swing-docking opens the views of a frame's first layout once its frame view's build has ended and its accessibility checks have run, before `configureFrame` and `pack()` (steps 5 and 6 of [opening a frame](milestone-3.md#frame-views)), so that the frame is packed and placed with them.
- **Belonging.** An added view is one of its window's views, in the order they were built, after the own view: its commands' shortcuts are registered in the window, as [milestone 4](milestone-4.md#refreshing-open-windows) describes for all the views of a window; the palette and the keymap dialog list its commands; a refresh applies its resources again; the inspector names it; `Ui.window()` in it is its window. Its window's menu bar, toolbar and title stay those of the own view.
- **Several views of one class.** When several open views of a window share a command id with a shortcut, as views of one class do, the shortcut acts on the one of them built last, and the palette and the keymap dialog list the command once, for that view. Closing one of them leaves the others' shortcuts: the shortcut then acts on the one built last among those left.
- **Closing.** An added view is closed by the module that opened it, or with its window, before the own view, the view built last first. Closing it asks the exit vetoes of its view scope, as closing a frame does; a veto keeps it open. Closed, its component state is kept (see [Window state](#window-state)), its scope is closed, its injector too, which unsubscribes its instances from the bus, and it leaves its window's views: its commands' shortcuts are removed, the palette, the keymap dialog and a refresh no longer see it.

`swing-ui` exports `be.imgn.swing.ui.internal` to `be.imgn.swing.docking` and `be.imgn.swing.wizard` too, and gives them, there, the internal entry points that: open and close added views; read the resources of a view's class and of its fields; run code once a frame view's build has ended and its accessibility checks have run; run the accessibility checks over a component of a view; add a module's own table to a window's saved state and read it back; and find a view class by its binary name among those whose `$X$$Injector` the `ServiceLoader` finds, which are all the view classes the processor saw.

---

## Window state

A window's state holds the state of the components of each of its views. The own view's stays where milestone 1 put it, `[windows.<key>.components.<field>]`. An added view's goes under the binary name of its class, `[windows.<key>.views."<binary name>".components.<field>]`, with the same keys as milestone 1's.

- **Saving.** When an added view closes, its component state is read and kept with its window. When the window's state is saved, it holds the state of the added views open then, read then, and the kept state of the classes of no open view.
- **Restoring.** An added view restores its components from the state kept for its class in this session, else from the window's saved state, if any.
- An added view created through a factory saves no state, and restores none: views of one class created through a factory are told apart by their arguments, which the state doesn't know.

```toml
[windows.main.views."be.imgn.swing.examples.addressbook.ContactListView".components.table]
columns = [{ model-index = 0, width = 120 }, { model-index = 1, width = 120 }, { model-index = 2, width = 160 }]
```

A window's state may also hold the layout of its dock (see [Saved layout](#saved-layout)), `[windows.<key>.dock]`. The window state's binding gains `views` and `dock`; a file that doesn't bind, a `dock` value of the wrong TOML type included, is still discarded whole with a warning, as [milestone 1](milestone-1.md#window-state) says. A `dock` table that binds but breaks the rules of the saved layout discards only the layout (see [Saved layout](#saved-layout)).

---

## Forms

`Form` (UI) gains:

```java
public boolean validate(Component within);
public void revealWith(Consumer<? super Component> reveal);
```

- `validate(within)` does what `validate()` does ([milestone 2](milestone-2.md#forms)) for the bindings whose component is `within` or inside it, and returns whether those are valid: it shows their errors, moves the focus to the first of them with an error, in binding order, and leaves the other bindings as they are. It returns `true` and does nothing when no binding's component is `within` or inside it. `NullPointerException` for `null`.
- `revealWith(reveal)` registers code that makes a component visible before the form focuses it. Both `validate` methods run the registered code on the component they focus, in the order it was registered, after selecting the tabs that hold it, and before checking that it is visible up to its window's root pane. The wizard registers code that shows the step holding the component (see [Wizards](#wizards)). `NullPointerException` for `null`.

---

## Wizards

`swing-wizard` (module `be.imgn.swing.wizard`, package `be.imgn.swing.wizard`) requires `be.imgn.swing.ui` transitively, and nothing else.

### Wizard

```java
@ViewScoped
public final class Wizard {                                             // swing-wizard
    public JComponent steps(JComponent... steps);
    public Observable<Integer> step();
    public BoundCommand previous();
    public BoundCommand next();
    public List<BoundCommand> buttons(StandardCommands commands);
}
```

A dialog view that injects `Wizard` shows its content as steps: the view's `content()` returns what `steps` returns, or places it, and its `buttons` returns what `buttons` returns.

```java
public final class ContactWizardDialog implements DialogView<Contact> {

    @Exposed final JPanel identity;
    @Exposed final AddressPanel home;
    @Exposed final AddressPanel office;

    @Override
    public JComponent content() {
        fields.bind(form, messages);
        return wizard.steps(identity, home, office);
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return wizard.buttons(commands);
    }
}
```

- **`steps(steps)`** returns a new panel holding, at `BorderLayout.PAGE_START`, a heading label with an empty border of 8 pixels on each side, and at its center a panel with a `CardLayout` holding the steps, in the given order, the first shown. It is called once, on the EDT, during the build of the view: `IllegalStateException` for a second call, for a call outside the build of a dialog view, or outside the view whose scope holds this `Wizard`; `IllegalArgumentException` for no step, for a step given twice, or for a step that no `@Exposed` component field of the view holds, nested ones included, as `Ui.addTab` requires ([milestone 4](milestone-4.md#tab-titles)); `NullPointerException` for `null`.
- **Heading.** The heading shows `heading(step, steps, title)` from swing-wizard's messages, "Step {step} of {steps}: {title}", "Étape {step} sur {steps} : {title}" in French, where `step` counts from 1 and `title` is the shown step's `step-title` resource, read from the view's resources through the field that holds the step, as `Ui.addTab` reads `tab-title`. A step with no `step-title` has the empty title. The heading is rendered as `Ui.render` renders: at once, after every change of the step, and at every refresh, so that it follows the locale.
- **`step()`** is the index of the shown step, from 0. It changes on the EDT.
- **`previous()`** shows the step before the shown one. It is enabled while the shown step isn't the first.
- **`next()`** validates the shown step, with `form.validate(step)` on the view's `Form`, and shows the next step only when it returns `true`, as it does for a step with no binding. It is enabled while the shown step isn't the last. Like `ok()` ([milestone 2](milestone-2.md#ok-cancel-and-apply)), it stays enabled while the step is invalid, so that activating it shows what is wrong.
- **Showing a step** shows its card, updates `step()` and the heading, and moves the focus, through the window's `Window.focus(Component)`, to the first component of the step, in the order of its container's focus traversal policy, that is focusable, enabled and visible; to none when the step has none. The first time a step other than the first is shown, the accessibility checks run over it, in development mode and tests, as the build ran them over the first step: the steps not shown then were skipped, as hidden components are ([milestone 4](milestone-4.md#accessibility-checks)).
- **Finish** is the dialog's `ok()`, enabled on every step, so that a wizard that modifies something can end from any step. As milestone 2 describes, it validates the whole form and closes the dialog only when it is valid. `Wizard` registers, with `form.revealWith`, code that shows the step holding the component that `validate()` focuses, so that its error is seen. Its text stays `ok()`'s, which a wizard overrides in its resources, as `ContactWizardDialog` does with `&Save`. Enter, which activates `ok()` ([milestone 2](milestone-2.md#ok-cancel-and-apply)), therefore finishes the wizard from any step, as Finish does.
- **`buttons(commands)`** returns `previous()`, `next()`, `commands.ok()` and `commands.cancel()`, in that order.
- **Commands.** `previous` and `next` are `Wizard`'s own commands, in the registry of its view, with no shortcut, and texts from swing-wizard's resources: `< &Previous` and `&Next >`, `< &Précédent` and `&Suivant >` in French. A view that injects `Wizard` overrides them in its resources, `[commands.previous]` and `[commands.next]`, as it renames standard commands ([milestone 2](milestone-2.md#renaming-standard-commands-in-a-view)). They are reached in code through the `Wizard`, never through a generated `<Class>Commands` class.
- **Size.** The dialog's preferred size is that of its largest step, as a `CardLayout` gives it.

The steps share the view's `Form`, its result and its scope: going back keeps what was entered. A step is a component of the view, not a view of its own (see [Scope](#scope)).

### Step titles

`step-title` joins the component properties ([milestone 1](milestone-1.md#injection-into-components)): a string, on any component field, read by `Wizard` and set on no component. It is a text: the translations check requires it in every locale file, as every key of the base file.

---

## Docking

`swing-docking` (module `be.imgn.swing.docking`, package `be.imgn.swing.docking`) requires `be.imgn.swing.ui` transitively, `modern_docking.api` and `modern_docking.multi_app`. Its API holds no ModernDocking type: ModernDocking is how the real dock is drawn and dragged, and the test harness replaces it (see [Test harness](#test-harness)).

### Dock views

```java
public interface DockView extends View {}                              // swing-docking
```

A dock view is an [added view](#views-opened-in-an-open-window) of a frame, shown in its frame's [dock](#dock): a content, commands, a scope, resources. It has no menu bar and no toolbar of its own; its content may hold toolbars and popup menus.

- **Title.** A dock view's title is shown on its tab and in its header. At the end of its build, unless the view has set it through `Docked` meanwhile, it becomes its class's `title` resource, else the class's simple name. A refresh sets it again from the new resources while it is still the one the resource, or the simple name, gave it, as for a window's title ([milestone 4](milestone-4.md#refreshing-open-windows)).
- **Icon.** A dock view's class's `icon` resource, a path relative to the class's package, is the icon of its tab and its header, loaded as commands' icons are, at the end of its build and again at each refresh. None when absent.
- **Closing.** The user may close every dock view, through its header's close button. Closing it there is `Dock.close` (see below).

`title` and `icon` join the class properties of dock views (see [Build-time checks](#build-time-checks)).

```java
@ViewScoped
public final class Docked {                                             // swing-docking
    public String title();
    public void setTitle(String title);
}
```

`Docked` is the place of a dock view in its dock, which the view injects. `title()` is the title shown, the empty string until one is set. `setTitle(title)` sets it, on the EDT; `NullPointerException` for `null`. `Docked` resolved in a view scope that a `Dock` didn't open throws `IllegalStateException`: the class of a view created through a factory is unknown until its constructor returns, so the dock, not the class, tells.

### Dock

```java
@WindowScoped
public final class Dock {                                               // swing-docking
    public JComponent area(Consumer<? super Dock> layOut);
    public <V extends DockView> V open(Class<V> type, Placement placement);
    public <F, V extends DockView> V open(
            Class<F> factory, Function<? super F, ? extends V> create, String key, Placement placement);
    public List<DockView> views();
    public Observable<Optional<DockView>> active();
    public String title(DockView view);
    public void select(DockView view);
    public void toFront(DockView view);
    public boolean close(DockView view);
    public Optional<DockLayout> layout();
}
```

Every method is called on the EDT. A frame holds one dock, which its frame view, or any bean of the frame's window scope, injects.

- **`area(layOut)`** returns the dock's component, which the frame view returns from `content()` or places in it. It is called once, during the build of the frame view: `IllegalStateException` for a second call, for a call outside that build, or in a dialog's window; `NullPointerException` for `null`. Once the frame view's build has ended and its accessibility checks have run, before the frame is packed (see [When](#views-opened-in-an-open-window)), the dock restores its [saved layout](#saved-layout); when there is none, or none of it can be restored, it calls `layOut` with itself, which opens the views of the frame's first layout. An empty dock shows an empty area.
- **`open(type, placement)`** opens a view of a dock view class, resolved through its child injector, and docks it as `placement` says. It throws `IllegalStateException` while a view is being built, as `layOut` never is. A dock holds at most one view of a class opened this way: when one is open, `open` brings it to the front, as `toFront` does, ignores `placement`, and returns it. It returns the view once docked. `NullPointerException` for `null`.
- **`open(factory, create, key, placement)`** opens a view created through an assisted factory, as `Frames.open(Class<F>, Function)` does, and docks it. The views opened through one factory are told apart by `key`: when one with the same factory and key is open, `open` brings it to the front, ignores `create` and `placement`, and returns it, cast to the type `create` gives; a caller that gives one factory and key views of different types gets a `ClassCastException`. `NullPointerException` for `null`; `IllegalArgumentException` for a blank key.
- **`views()`** are the open dock views, in the order they were opened.
- **`active()`** is the dock view that has the focus, else the one that had it last, else, when none had it, the one opened last; empty when none is open. A view becomes the active one when it is opened, when it is brought to the front, and when a component of its content gains the permanent focus. When the active view closes, the active one is the open view that was active before it most recently, else the one opened last. It changes on the EDT.
- **`title(view)`** is the view's title, as `Docked.title()` gives it.
- **`select(view)`** selects the view's tab in its tab group, without moving the focus and without changing `active()`.
- **Hidden views.** The first time a view's tab is selected, after the build that skipped it while hidden, the accessibility checks run over its content, in development mode and tests.
- **`toFront(view)`** selects the view's tab, moves the focus to the first component of its content that is focusable, enabled and visible, through the window's `Window.focus(Component)`, and makes it the active one.
- **`close(view)`** closes the view as an added view is closed: asks the exit vetoes of its scope, and returns `false` when one vetoes; otherwise undocks it, closes its scope, and returns `true`. The user closing a view through its header's close button does the same, and nothing when it vetoes.
- **`layout()`** is the arrangement of the open views (see [Layout](#layout)), empty when none is open.

`select`, `toFront`, `close` and `title` throw `IllegalArgumentException` for a view that is not open in this dock, and `NullPointerException` for `null`.

### Placement

```java
public enum DockRegion { CENTER, NORTH, EAST, SOUTH, WEST }             // swing-docking

public record Placement(
        @Nullable Class<? extends DockView> target, DockRegion region, double proportion) {   // swing-docking
    public static Placement of(DockRegion region, double proportion);
    public static Placement tab();
    public static Placement beside(Class<? extends DockView> target, DockRegion region, double proportion);
    public static Placement tabWith(Class<? extends DockView> target);
}
```

A placement docks a view against a target: the open view of the target class opened last, or, when `target` is `null` or no view of it is open, the whole dock.

- **`CENTER`** adds the view as the last tab of the target's tab group, and selects it. Against the whole dock, the target is the active view's tab group; in an empty dock, the view fills it.
- **`NORTH`, `EAST`, `SOUTH` and `WEST`** split the target, a tab group or the whole dock's arrangement, in two: the view, in a tab group of its own, takes `proportion` of the space, on that side, and the target the rest. In an empty dock, the view fills it.
- `of(region, proportion)` is against the whole dock; `tab()` is `of(CENTER, 0.5)`; `beside(target, region, proportion)` is against a target; `tabWith(target)` is `beside(target, CENTER, 0.5)`.
- The constructor throws `NullPointerException` for a `null` region, and `IllegalArgumentException` for a proportion that is not strictly between 0 and 1, or not finite. A `CENTER` placement doesn't read its proportion.

### Layout

```java
public sealed interface DockLayout {                                    // swing-docking
    enum Orientation { HORIZONTAL, VERTICAL }

    record Split(Orientation orientation, double proportion, DockLayout first, DockLayout second)
            implements DockLayout {}

    record Tabs(List<DockView> views, DockView selected) implements DockLayout {}
}
```

- A `Split` puts `first` left of `second` when `HORIZONTAL`, above it when `VERTICAL`; `proportion` is `first`'s share of the space, strictly between 0 and 1. A `Tabs` lists the views of a tab group, in tab order, at least one, and its selected view, one of them. Their constructors check these rules, throwing `IllegalArgumentException`, and `NullPointerException` for `null`; `Tabs` copies its list, unmodifiable.
- A placement gives these layouts, where `old` is the target's layout and `new` the view's tab group: `WEST` gives `Split(HORIZONTAL, proportion, new, old)`; `EAST`, `Split(HORIZONTAL, 1 - proportion, old, new)`; `NORTH`, `Split(VERTICAL, proportion, new, old)`; `SOUTH`, `Split(VERTICAL, 1 - proportion, old, new)`. Closing the last view of a tab group replaces the split that holds it with the split's other part. The user dragging a view, a tab or a divider changes the layout in the same terms.
- The real dock and the harness's give the same `layout()` for the same calls, except for proportions. A proportion is the one given, by a placement or a restored layout, until the frame is shown; once it is, the real dock reports those of its dividers, rounded to 3 decimal places, which a user may have moved, and which a divider's pixel position may make differ slightly from the one given.

### Saved layout

The dock's layout is saved with its frame's window state (see [Window state](#window-state)), in `[windows.<key>.dock]`, as a tree of tables: a split has `split`, `"horizontal"` or `"vertical"`, `proportion`, and the tables `first` and `second`; a tab group has `tabs`, the binary names of its views' classes in tab order, and `selected`, the index of its selected view.

```toml
[windows.main.dock]
split = "horizontal"
proportion = 0.33

[windows.main.dock.first]
split = "vertical"
proportion = 0.5

[windows.main.dock.first.first]
tabs = ["be.imgn.swing.examples.addressbook.ContactListView"]
selected = 0

[windows.main.dock.first.second]
tabs = ["be.imgn.swing.examples.addressbook.ContactDetailView"]
selected = 0

[windows.main.dock.second]
tabs = ["be.imgn.swing.examples.addressbook.SelectedPictureView"]
selected = 0
```

- **Saving.** The views opened through a factory are left out, a tab group left with none is left out, and a split left with one part is replaced by that part; `selected` then points to the selected view when it is kept, else to the first. A dock with no view left saves no `dock` table, and the window state then has none.
- **Restoring.** Each binary name the saved layout names is looked up among the view classes the processor saw, through their `$X$$Injector` services, as [principle 3](vision.md#principles) requires; no class is loaded by name. A name that matches no class, or a class that isn't a dock view class, is logged at `WARNING`, naming it, and left out, as views opened through a factory are when saving, with the same pruning of tab groups and splits. The views left are resolved, built and checked as `open(type, placement)` builds them, in the order the tree lists them, depth first, and the dock then arranges them in the saved tree directly, with the saved proportions and selected tabs, rather than through placements, which can't express every tree. A table that is neither a split nor a tab group as described, a proportion not strictly between 0 and 1, a `selected` out of range, or a class named twice, discards the whole layout, with a `WARNING`; `layOut` then runs.
- A view that `layOut` didn't open but a saved layout names is opened all the same: the saved layout is the user's.

### The real dock

The real dock is drawn by ModernDocking, through `modern_docking.multi_app`: one `Docking` instance per frame that has a dock, with the frame as its main window, and a `RootDockingPanel` as the dock's area. It is created when `area` is called and uninitialized when the frame closes.

- Each dock view's content is held by a panel of swing-docking's own, which implements ModernDocking's `Dockable`, since ModernDocking takes its dockables for components. It is registered when the view opens, and, when the view closes, undocked then deregistered with `deregisterDockable`, so that a view of the same class may open again with the same persistent id. Its persistent id is the binary name of the view's class, or, for a view opened through a factory, the binary name of the factory class, `#`, and its key. Its tab text and title text are the view's title, its icon the view's icon.
- `isClosable` returns `true`. `requestClose`, which the header's close button calls, closes the view through `Dock.close`, which undocks it when no veto keeps it, and returns `false`, so that ModernDocking doesn't undock it a second time.
- A dockable may not float, auto-hide, be maximized or minimized, and is limited to its frame: `isFloatingAllowed`, `isAutoHideAllowed` and `isMinMaxAllowed` return `false`, `isLimitedToWindow` returns `true`, and `hasMoreMenuOptions` returns `false`. `isWrappableInScrollpane` returns `false`: a view puts its content in a scroll pane itself when it needs one, as `ContactListView` does its table.
- The dock's area is a `RootDockingPanel` built with the three-argument constructor, with no toolbars.
- Placements map to `Docking.dock` against the target's dockable or the frame. The saved layout is converted to and from ModernDocking's `WindowLayout`, read with `getDockingState().getWindowLayout(frame)` and restored with `getDockingState().restoreWindowLayout_PreserveSizeAndPos(frame, layout)`, which leaves the frame's bounds and maximized state to the framework's window state ([milestone 3](milestone-3.md#window-state-of-frames)). It is never written to a file of ModernDocking's own. A change of title or icon calls `updateTabInfo`.
- swing-docking sets none of ModernDocking's static settings, and uses neither its XML layout persistence nor its `modern_docking.ui_ext` module, which requires FlatLaf Extras. ModernDocking itself sets the JVM-wide `awt.dnd.drag.threshold` system property when a `Docking` is created, and keeps its docking listeners in static lists, shared by the `Docking` instances; swing-docking adds no listener there.

---

## Changes to milestones 1 to 4

These change what milestones 1 to 4 specify, from milestone 5 on:

- **Windows** may hold views opened while they are open, which leave them when closed (see [Views opened in an open window](#views-opened-in-an-open-window)); the palette, the keymap dialog, a refresh and the inspector see a window's views as they are when they run. A shortcut shared by several views of a window acts on the one built last.
- **Opening a frame** opens the first layout of its dock, if it has one, between steps 5 and 6 of [milestone 3](milestone-3.md#frame-views).
- **Window state** holds the state of each added view's components, under `views`, including that of views closed in the session, and a dock's layout, under `dock` (see [Window state](#window-state)).
- **`Form`** gains `validate(Component)` and `revealWith`; `validate()` runs the registered reveal code before checking that the component it focuses is visible, where milestone 2 left a component on another card of a wizard unfocused (see [Forms](#forms)).
- **The accessibility checks** of an added view cover its content, not its window's content pane; a wizard's steps and a dock's tabs that were hidden during the build are checked when first shown.
- **Assisted factories** are generated for dock views too.
- **Component properties** gain `step-title`; **class properties** gain `title` and `icon` for dock views.
- **`swing-ui`** exports its internal package to `be.imgn.swing.docking` and `be.imgn.swing.wizard`.

---

## Build-time checks

Milestone 5 adds these checks to those of milestones [1](milestone-1.md#build-time-checks), [2](milestone-2.md#build-time-checks), [3](milestone-3.md#build-time-checks) and [4](milestone-4.md#build-time-checks), with diagnostics attached to the Java element as there:

| Check | Error when |
|---|---|
| Dock view properties | `title` or `icon` of a dock view class's resources is not a string; `icon` names no resource |
| Step titles | A `step-title` is not a string |

- The class-properties check accepts `title` and `icon` on a class that implements `be.imgn.swing.docking.DockView`, which the processor knows by name. A class that is both a dock view and a frame view or a dialog view is an error.
- The commands' check of `[commands.<id>]` tables accepts `previous` and `next` in the resources of a view class whose constructor has a parameter of type `be.imgn.swing.wizard.Wizard`, which the processor knows by name.
- The component-fields check accepts `step-title` on every component field.

---

## Processor

- **Resources.** The resources step reads and checks the new class properties, `step-title`, and the commands of wizards, as [Build-time checks](#build-time-checks) describes. `$X$$Injector` sets no `step-title`.
- **Views.** A dock view's `$X$$Injector` and `$X$$State` are generated as for any view. Its assisted factory, `<Class>Factory`, is generated as for a dialog or frame view: the processor generates it for a class that implements `be.imgn.swing.docking.DockView` too, which it knows by name.

---

## Test harness

The harness never realizes a window ([milestone 1](milestone-1.md#test-harness)), and ModernDocking needs one. `swing-docking` provides a `TesterExtension` ([milestone 3](milestone-3.md#test-harness)), `be.imgn.swing.docking.internal.DockingTesterExtension`, which replaces the real dock with a headless one:

- It follows the [placement](#placement) and [layout](#layout) rules, and draws its layout into the dock's area: each split as a `JSplitPane`, of the split's orientation, whose resize weight is the split's proportion and whose divider location follows it once the area has a size; each tab group as a `JTabbedPane`, also of one view, whose tab titles are the views' titles and whose selected tab is the selected view. So the components of dock views are in the frame's root pane, where `component`, `components`, `inspect` and the checks find them.
- It has no header and no close button: tests close views with `Dock.close`, and reach `Dock` with `frame.bean(Dock.class)` or `app.bean(Dock.class)`.
- `active()` changes only when a view is opened, brought to the front or closed, since the harness's windows have no focus.

`AppTester` and `OpenFrame` (testing) gain `<B> B bean(View view, Class<B> type)`, which resolves a bean, on the EDT, through the injector of an open view of the main frame, or of that frame, its own view or an added one: `bean(Class)` and `commands(Class)` resolve through the own view's injector, where an added view's view-scoped beans, such as its generated `<Class>Commands`, would be built anew, apart from the view. `IllegalArgumentException` for a view that isn't open in that frame; `NullPointerException` for `null`.

`swing-wizard` needs no replacement: a test answers a wizard dialog with `answerDialog`, reaches its `Wizard` with `dialog.bean(Wizard.class)` and runs `previous()`, `next()` and the standard commands.

```java
@Test
void opensAPictureTabPerContact() {
    try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
        Dock dock = app.bean(Dock.class);
        ContactListView list = app.call(() -> (ContactListView) dock.views().get(1));
        app.run(() -> list.table.setRowSelectionInterval(0, 0));

        app.bean(list, ContactListViewCommands.class).openPicture().execute();

        assertThat(app.call(() -> dock.views().stream().map(dock::title).toList()))
                .containsExactly("Contact Picture", "Contacts List", "Contact Detail", "Smith");
    }
}
```

---

## Upstream changes

Milestone 5 needs none. It relies on this existing behavior:

- inject's child injectors, which a window's added views get under its injector, as its own view does, and its assisted factories.
- toml's binding of nested tables and arrays, and its generated writers, for the window state's `views` and `dock`.
- ModernDocking 1.4.9's `Docking(Window)`, `RootDockingPanel`, `registerDockable` and `deregisterDockable`, `getDockingState()` with `getWindowLayout` and `restoreWindowLayout_PreserveSizeAndPos`, the `Dockable` methods named in [The real dock](#the-real-dock), and its module descriptors.

---

## Changes to the vision

These depart from the [vision](vision.md), and are to be applied to it:

- **Modules.** `swing-docking` integrates ModernDocking, where the vision names DockingFrames: DockingFrames' last release is from 2013, for Java 5, with no module name, while the vision puts the module path first. Its API holds no ModernDocking type, so that the test harness can replace it.
- **Docking** shows views added to a frame while it is open, which belong to its window as its own view does: their commands are in the frame's palette and keymap dialog.
- **Window state.** What is saved and restored gains a dock's layout, next to bounds, split panes, table columns and selected tabs.

---

## Acceptance

`./mvnw -B verify` succeeds on JDK 21 and on JDK 25. Every test of milestones 1 to 4 still passes, except those of the behavior milestone 5 changes, which milestone 5's tests replace. `swing-examples` depends on `swing-wizard` and `swing-docking`, and has the following, each with `AppTester` tests of every behavior listed. The Address Book is also run once on macOS, docked by ModernDocking, as a check of the real dock that the headless build can't make.

### The contact wizard

`addressbook/ContactWizardDialog` uses `Wizard`, as the [example](#wizard) shows:

- Its steps are `@Exposed` fields: `identity`, the panel of the names and the birth date, built in the constructor, as `ContactTabsDialog` builds its `contactTab`, then `home` and `office`. Their `step-title`s are `Contact Information`, `Home Address` and `Office Address`, and `Information générale`, `Adresse maison` and `Adresse bureau` in French, in `ContactWizardDialog`'s resources.
- Its own `previous` and `next` commands, `hasPrevious`, `hasNext`, the step property, the `CardLayout` and the step label are removed; its resources keep `[commands.previous]` and `[commands.next]`, with their texts. `AddressBookMessages` loses `wizardStep`, `contactStep`, `homeAddressStep` and `officeAddressStep`.

Its tests check, at least: the heading reads `Step 1 of 3: Contact Information`, then `Step 2 of 3: Home Address` after `next()`, and `Étape 1 sur 3 : Information générale` in French, and follows a change of the locale while the dialog is open; `previous()` is disabled on the first step and `next()` on the last; `next()` with an empty first name stays on the first step, showing its error; showing a step focuses its first field; going back keeps what was entered; `ok()` on the first step creates the contact when the form is valid; modifying a contact ends from any step. Only the first step has rules, which `next()` enforces before any other step shows, so `ok()` showing another step's error is checked by `swing-wizard`'s own tests.

### The docked Address Book

`AddressBookApp` stays the single-frame application class, and its dock holds the views below. Guts' docking demo (`guts-sandbox/guts-gui-ex-docking-frames-common`) gives the list and detail views, their titles and icons, the per-contact picture tabs, opened by a double click on a row and titled with the last name (Guts' `%2$s`), the first layout's proportions, and the Views menu's texts and icons. The rest is new: Guts' picture area is a working area that only holds the per-contact tabs, while `SelectedPictureView` keeps milestone 4's picture of the selected contact; the `openPicture` command; the selection kept for views opened later; and Views menu commands that bring an open view to the front, where Guts' do nothing.

- **`CurrentContact`**, `@WindowScoped`, keeps the selection: it subscribes to `ContactSelected` and gives the last one's contact as `Observable<Optional<Contact>> contact()`, empty before any. The selection stays published as `ContactSelected` events ([milestone 2](milestone-2.md#acceptance)), which `ContactActions` consumes; the views show it from `CurrentContact`, read when they are built and followed until they close, so that a view opened after a selection shows it.
- **`ContactListView`**, a dock view holding the contacts table, the `@Exposed` field `table`, in a scroll pane, bound as milestone 4 binds it, which publishes `ContactSelected` on each selection change. Its command `openPicture`, `Open Picture`, `Ouvrir la photo` in French, with no mnemonic since it is in no menu, enabled while a contact is selected, publishes `PictureRequested(Contact contact)` for the selected contact; a double click on a row runs it, as `execute()` does. When a `ContactPictureView` becomes the dock's `active()` view, the list selects its contact, which `ContactPictureView.contact()` gives. Title `Contacts List`, `Tous les Contacts` in French, icon `icons/contact_list.png`.
- **`ContactDetailView`**, a dock view holding the current contact's names and birth date, as milestone 4's detail panel, its labels the `@Exposed` fields `firstNameLabel`, `lastNameLabel` and `birthLabel`. Title `Contact Detail`, `Information Contact` in French, icon `icons/contact_detail.png`.
- **`SelectedPictureView`**, a dock view holding the current contact's picture, the `@Exposed` field `picture`, with its texts as milestone 4 renders them. Title `Contact Picture`, `Photo du Contact` in French, icon `icons/contact_picture.png`.
- **`ContactPictureView`**, a dock view created through its generated `ContactPictureViewFactory` for one contact, `ContactPictureView(@Assisted Contact contact, ...)`, with `Contact contact()`, holding that contact's picture, the `@Exposed` field `picture`, loaded by a `Task` that its content runs with `Block.NONE`, and `noPicture()` when the contact has none. Its title is set through `Docked`, in its constructor, to the contact's last name.

`AddressBookApp`:

- **Layout.** Its `content()` returns `dock.area(...)`, whose `layOut` opens `SelectedPictureView` with `Placement.tab()`, `ContactListView` with `Placement.of(WEST, 0.33)`, and `ContactDetailView` with `Placement.beside(ContactListView.class, SOUTH, 0.5)`.
- **Pictures.** On `PictureRequested`, it opens the contact's `ContactPictureView` through its factory, with the contact's id as key and `Placement.tabWith(SelectedPictureView.class)`. On `ContactSelected`, it selects, with `Dock.select`, the open `ContactPictureView` of that contact, if any.
- **Views menu.** A `[menus.views]` menu, `&Views`, `&Vues` in French, between the Language and Task Tests menus, with the commands `showContactList`, `showContactDetail` and `showContactPicture` of `ViewActions`, a `@ViewScoped` bean of the frame view, as `ContactActions` is, with its resources in `ViewActions.toml` and `ViewActions_fr.toml`. Their texts are Guts', `Contacts &List`, `Contact &Detail` and `Contact &Picture`, `&Tous les Contacts`, `&Information du Contact` and `&Photo du Contact` in French, and their icons are the views'. Each opens its view with the placement of the first layout, or brings it to the front when open.
- Its fields `table`, `firstNameLabel`, `lastNameLabel`, `birthLabel`, `picture`, `secondarySplit` and `mainSplit` move to the views or go, and so do its `ContactSelected` subscribers; their resources move to the views' resource files, in English and French. The icons are copied from Guts' docking demo.

Its tests reach the views' beans with `bean(View, Class)`, and check, at least: the first layout is `Split(HORIZONTAL, 0.33, Split(VERTICAL, 0.5, Tabs[list], Tabs[detail]), Tabs[picture])`; selecting a contact shows it in the detail view and the picture view; `openPicture()` opens a tab titled with the contact's last name in the picture view's tab group, and opening it again brings the same view to the front; selecting a contact whose picture tab is open selects that tab, without changing the active view; bringing a picture tab to the front selects its contact; closing the detail view and choosing `Contact &Detail` in the Views menu opens it again, showing the contact selected meanwhile; a closed detail view no longer follows the selection; a closed view stays closed, and the layout is restored, after a restart on the same file system, without the contacts' picture views; the table's columns are restored with the list view, also after the list view was closed and opened again; a saved layout naming a class that doesn't exist is restored without it, with a warning; switching to French changes the views' titles but a picture view's; the palette lists `Open Picture` in the frame; `inspect` gives `ContactListView` and `table` for the table.

### Without the examples

`swing-ui`'s own tests check opening and closing views in an open window, their order of build, their vetoes, their subscribers leaving the bus, the shortcuts of several views of one class, their window state, kept and saved, and `Form`'s additions. `swing-wizard`'s own tests check `Wizard` against a dialog view of their own: its errors, its heading, its commands, its focus, the accessibility checks of later steps, and its reveal code, with `ok()` showing an invalid field of another step and focusing it. `swing-docking`'s own tests check, against the headless dock, every placement, `layout()`, `active()`, `select`, `toFront`, `close` and its vetoes, the saved layout's form, saving and restoring, a tree no placement can express included, the views opened through a factory, and `Docked`. They also check, headless, the real dock's conversion between `DockLayout` and ModernDocking's `WindowLayout`, both ways, and its persistent ids, which need no window. The processor's tests check every new build-time check.

---

## Order of work

Each step adds the harness pieces its own tests need.

1. UI and testing: views opened in an open window, the window state of several views, `Form`'s additions, and `bean(View, Class)`.
2. Wizard and processor: `swing-wizard`, `step-title`, the commands of wizards; the contact wizard.
3. Docking and processor: `swing-docking`'s API with the headless dock and its tester extension, the saved layout, the dock views' class properties and assisted factories.
4. Docking: the real dock, on ModernDocking.
5. The docked Address Book.
