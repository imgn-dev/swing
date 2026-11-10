package be.imgn.swing.ui;

import be.imgn.swing.FailureHandler;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.internal.Guarded;
import be.imgn.swing.internal.NoTask;
import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.ui.internal.ComponentInspector;
import be.imgn.swing.ui.internal.Edt;
import be.imgn.swing.ui.internal.EdtDelivery;
import be.imgn.swing.ui.internal.KeyStrokes;
import be.imgn.swing.ui.internal.Mnemonics;
import be.imgn.swing.ui.internal.ShortcutBindings;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import org.jspecify.annotations.Nullable;

/**
 * A command of a view, turned into Swing objects. Each created component shares the command's {@link Action}, so text,
 * icon, tooltip, enabled and selected state stay in sync.
 */
public final class BoundCommand implements MenuEntry, DynamicEntry, ToolBarEntry {

    private static final System.Logger LOGGER = System.getLogger(BoundCommand.class.getName());
    // The client property of a toolbar button that holds the accessible name the command gave it.
    private static final String TOOL_BAR_NAME = "be.imgn.swing.toolBarName";

    private final CommandRegistry registry;
    private final Class<?> owner;
    private final String id;
    private final @Nullable Consumer<CommandContext> handler;
    private final @Nullable Function<CommandContext, Task<?>> taskHandler;
    private final @Nullable Supplier<Observable<Boolean>> enabledSupplier;
    private final @Nullable Supplier<Property<Boolean>> selectedSupplier;
    private final Block block;
    private final Concurrency concurrency;
    private final @Nullable String cancels;
    private final boolean overridable;
    private CommandResources resources;
    private final CommandAction action;
    private final Property<Boolean> blocked = Property.of(false);
    private final Property<Boolean> running = Property.of(false);
    // The tasks the command started that haven't ended, in activation order, those that wait included. On the EDT.
    private final List<Started> tasks = new ArrayList<>();
    // The shortcut of the resources, and the effective one, the keymap's first, with where it comes from.
    private @Nullable KeyStroke defaultKeyStroke;
    private @Nullable KeyStroke keyStroke;
    private ShortcutBindings.Priority priority = ShortcutBindings.Priority.RESOURCES;
    private Mnemonics text;
    // The components the command created, which take its accessible name and description again at a refresh, until
    // its view closes.
    private final List<AbstractButton> created = new ArrayList<>();
    // The accessible name and description the resources gave the created components last, null for none.
    private @Nullable String givenName;
    private @Nullable String givenDescription;
    private int blockCount;
    private @Nullable Observable<Boolean> enabled;
    private @Nullable Property<Boolean> selected;
    private @Nullable BoundCommand stopped;
    private boolean attached;

    BoundCommand(CommandRegistry registry, CommandBuilder builder, CommandResources resources) {
        this.registry = registry;
        this.owner = builder.owner();
        this.id = builder.id();
        this.handler = builder.builtHandler();
        this.taskHandler = builder.builtTaskHandler();
        this.enabledSupplier = builder.builtEnabled();
        this.selectedSupplier = builder.builtSelected();
        this.block = builder.builtBlock();
        this.concurrency = builder.builtConcurrency();
        this.cancels = builder.builtCancels();
        this.overridable = builder.isOverridable();
        this.resources = resources;
        this.text = Mnemonics.parse(id);
        this.action = new CommandAction(this);
        action.putValue(Action.ACTION_COMMAND_KEY, id);
        apply(resources);
        if (selectedSupplier != null) {
            action.putValue(Action.SELECTED_KEY, false);
        }
    }

    // Puts the resources on the command's action and on the components it created: at its creation, and at a refresh.
    private void apply(CommandResources applied) {
        this.resources = applied;
        UiServices services = registry.services();
        boolean macOS = services.isMacOS();
        this.text = Mnemonics.parse(applied.string("text").orElse(id));
        this.defaultKeyStroke = applied.string("shortcut")
                .map(shortcut -> KeyStrokes.parse(shortcut, macOS))
                .orElse(null);
        Keymap keymap = services.keymap();
        this.keyStroke = keymap.effective(identity(), defaultKeyStroke);
        this.priority = keymap.priority(identity(), applied.frameworkShortcut());
        action.putValue(Action.NAME, text.text());
        boolean mnemonic = !macOS && text.hasMnemonic();
        action.putValue(Action.MNEMONIC_KEY, mnemonic ? text.keyCode() : null);
        action.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, mnemonic ? text.index() : null);
        Icon icon = applied.string("icon")
                .map(path -> services.icons().load(applied.iconPackage(), path))
                .orElse(null);
        action.putValue(Action.SMALL_ICON, icon);
        action.putValue(Action.LARGE_ICON_KEY, icon);
        action.putValue(Action.ACCELERATOR_KEY, keyStroke);
        action.putValue(
                Action.SHORT_DESCRIPTION, applied.string("tool-tip-text").orElse(null));
        created.forEach(this::describe);
        givenName = applied.string("accessible-name").orElse(null);
        givenDescription = applied.string("accessible-description").orElse(null);
    }

    /**
     * Returns the id, also the action command of every component created for the command.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Returns a new button for the command.
     *
     * @return the button
     */
    public JButton createButton() {
        return accessible(new JButton(action));
    }

    /**
     * Returns a new menu item for the command.
     *
     * @return the menu item
     */
    public JMenuItem createMenuItem() {
        return accessible(new JMenuItem(action));
    }

    /**
     * Returns a new toggle button for a command with a selected state.
     *
     * @return the toggle button
     * @throws IllegalStateException if the command has no selected state
     */
    public JToggleButton createToggleButton() {
        requireSelected();
        return accessible(new JToggleButton(action));
    }

    /**
     * Returns a new check box menu item for a command with a selected state.
     *
     * @return the menu item
     * @throws IllegalStateException if the command has no selected state
     */
    public JCheckBoxMenuItem createCheckBoxMenuItem() {
        requireSelected();
        return accessible(new JCheckBoxMenuItem(action));
    }

    /**
     * Returns the command's Swing action, the same on each call.
     *
     * @return the action
     */
    public Action asAction() {
        return action;
    }

    /**
     * Runs the command as if activated, with a {@link Trigger.Programmatic} trigger and no modifier, if it is enabled.
     *
     * @return {@code true} if the command ran, {@code false} if it is disabled
     */
    public boolean execute() {
        return execute(CommandContext.programmatic());
    }

    /**
     * Runs the command as if activated, if it is enabled: whether it is enabled is the {@code enabled} observable's
     * current value, not the state of the {@link Action}, which follows it one posted update later.
     *
     * @param context how the command was activated
     * @return {@code true} if the command ran, {@code false} if it is disabled
     */
    public boolean execute(CommandContext context) {
        Objects.requireNonNull(context, "context");
        return Edt.call(registry.services().executor(), () -> run(context));
    }

    /**
     * Returns whether the command is running: {@code true} from the moment it starts a task until every task it started
     * has ended, waiting ones included; {@code false} otherwise. A task has ended once its last hook has run, and the
     * value changes on the EDT, after those hooks. A command whose handler returns {@code void}, or returns
     * {@code Task.none()}, starts nothing, so it isn't running.
     *
     * @return whether the command is running
     */
    public Observable<Boolean> running() {
        return running;
    }

    /**
     * Cancels every task the command started that hasn't ended, waiting ones included; does nothing when there is none.
     * It may be called from any thread, and runs on the EDT, as {@link #execute()} does.
     */
    public void cancel() {
        Edt.call(registry.services().executor(), () -> {
            cancelTasks();
            return Boolean.TRUE;
        });
    }

    private void requireSelected() {
        if (selectedSupplier == null) {
            throw new IllegalStateException("The command " + identity() + " has no selected state");
        }
    }

    private <C extends AbstractButton> C accessible(C component) {
        created.add(component);
        component.putClientProperty(ComponentInspector.COMMAND_PROPERTY, identity());
        describe(component);
        return component;
    }

    // A name or description the command gave and its resources no longer give goes back to the component's own; one
    // that something else gave, such as the view's resources or its code, stays.
    private void describe(AbstractButton component) {
        AccessibleContext context = component.getAccessibleContext();
        String name = resources.string("accessible-name").orElse(null);
        if (name != null) {
            context.setAccessibleName(name);
        } else if (givenName != null && givenName.equals(context.getAccessibleName())) {
            context.setAccessibleName(null);
        }
        String description = resources.string("accessible-description").orElse(null);
        if (description != null) {
            context.setAccessibleDescription(description);
        } else if (givenDescription != null && givenDescription.equals(context.getAccessibleDescription())) {
            context.setAccessibleDescription(null);
        }
    }

    // Takes the resources again, at a refresh; the registry registers the shortcut again. Resources that can't be
    // applied, such as those of a reloaded file, are a warning, so that the refresh goes on.
    void refresh() {
        Exception thrown = Guarded.runCatchingExceptions(() -> apply(registry.resourcesOf(owner, id, overridable)));
        if (thrown != null) {
            LOGGER.log(
                    System.Logger.Level.WARNING, "Skipped the resources of the command " + identity() + ": " + thrown);
        }
    }

    // A toolbar button hides its text while the command has an icon: its accessible name is then the text, unless the
    // resources give one, so that a toolbar never fails the accessibility checks. A name something else gave stays.
    void nameToolBarButton(AbstractButton button) {
        if (resources.string("accessible-name").isPresent()) {
            return;
        }
        Object named = button.getClientProperty(TOOL_BAR_NAME);
        String current = button.getAccessibleContext().getAccessibleName();
        String name = hasIcon() ? text.text() : null;
        if (named == null ? current == null || current.equals(button.getText()) : named.equals(current)) {
            button.getAccessibleContext().setAccessibleName(name);
            button.putClientProperty(TOOL_BAR_NAME, name);
        }
    }

    // A component of a rebuilt menu section, which no longer follows the command's resources.
    void forget(AbstractButton component) {
        created.remove(component);
    }

    boolean run(CommandContext context) {
        if (!isEnabledNow()) {
            return false;
        }
        if (selectedSupplier != null) {
            Property<Boolean> property = selected();
            property.set(!property.get());
        }
        BoundCommand target = stopped;
        if (target != null) {
            target.cancelTasks();
        }
        Consumer<CommandContext> plain = handler;
        Function<CommandContext, Task<?>> tasking = taskHandler;
        if (plain != null) {
            failed(Guarded.runCatchingExceptions(() -> plain.accept(context)));
        } else if (tasking != null) {
            AtomicReference<@Nullable Task<?>> returned = new AtomicReference<>();
            failed(Guarded.runCatchingExceptions(() ->
                    returned.set(Objects.requireNonNull(tasking.apply(context), "The handler returned no task"))));
            Task<?> task = returned.get();
            if (task != null && !(task instanceof NoTask)) {
                start(task, context);
            }
        }
        // The command ran, even when its handler threw.
        return true;
    }

    // An exception a handler throws goes to the FailureHandler, as a task's failure does; an Error propagates.
    private void failed(@Nullable Throwable thrown) {
        if (thrown != null) {
            registry.injector().getInstance(FailureHandler.class).failed(thrown);
        }
    }

    // A task that waits for the tasks before it, with RESTART or SERIAL, begins once they have ended, and blocks input
    // only from then on.
    private void start(Task<?> task, CommandContext context) {
        RunningTask handle = registry.services().tasks().prepare(task, view().scope(), id, true);
        if (concurrency == Concurrency.RESTART) {
            cancelTasks();
        }
        Started started = new Started(handle, context);
        tasks.add(started);
        running.set(true);
        handle.whenEnded(() -> ended(started));
        if (concurrency == Concurrency.PARALLEL || tasks.getFirst() == started) {
            begin(started);
        }
    }

    private void begin(Started started) {
        if (started.handle.isBegun()) {
            return;
        }
        started.unblock = TaskBlocking.block(registry.services(), view(), started.handle, block, this, started.context);
        started.handle.begin();
    }

    private void ended(Started started) {
        started.unblock.run();
        tasks.remove(started);
        if (tasks.isEmpty()) {
            running.set(false);
        } else if (concurrency != Concurrency.PARALLEL) {
            begin(tasks.getFirst());
        }
    }

    private void cancelTasks() {
        List.copyOf(tasks).forEach(started -> started.handle.cancel());
    }

    Runnable blockItself() {
        blockCount++;
        blocked.set(true);
        return () -> {
            if (--blockCount == 0) {
                blocked.set(false);
            }
        };
    }

    boolean isEnabledNow() {
        BoundCommand target = stopped;
        return enabled().get() && !blocked.get() && (cancels == null || (target != null && target.running.get()));
    }

    private Observable<Boolean> enabled() {
        Observable<Boolean> current = enabled;
        if (current == null) {
            current = enabledSupplier == null ? Property.of(true) : enabledSupplier.get();
            enabled = current;
        }
        return current;
    }

    private Property<Boolean> selected() {
        Property<Boolean> current = selected;
        if (current == null) {
            current = Objects.requireNonNull(selectedSupplier, "selected").get();
            selected = current;
        }
        return current;
    }

    /** Attaches the command: subscribes its enabled and selected states, and registers its shortcut. Idempotent. */
    void attach() {
        if (attached) {
            return;
        }
        attached = true;
        ViewHost view = view();
        Observable<Boolean> effective = Observable.combine(enabled(), blocked, (on, isBlocked) -> on && !isBlocked);
        String stoppedId = cancels;
        if (stoppedId != null) {
            // Resolved now, since the generated code builds commands in declaration order: a stop command may come
            // before the command it cancels.
            BoundCommand target = registry.find(owner, stoppedId)
                    .filter(command -> command.taskHandler != null)
                    .orElseThrow(() -> new IllegalStateException("The command " + identity() + " cancels " + stoppedId
                            + ", which is no command of its class whose handler returns a task"));
            stopped = target;
            effective = Observable.combine(effective, target.running, (on, isRunning) -> on && isRunning);
        }
        action.setEnabled(effective.get());
        EdtDelivery.subscribe(view.scope(), effective, action::setEnabled, EdtDelivery.Order.FRAMEWORK);
        if (selectedSupplier != null) {
            Property<Boolean> property = selected();
            action.putValue(Action.SELECTED_KEY, property.get());
            EdtDelivery.subscribe(
                    view.scope(),
                    property,
                    value -> action.putValue(Action.SELECTED_KEY, value),
                    EdtDelivery.Order.FRAMEWORK);
        }
        registerShortcut();
    }

    // Registers the shortcut in the window, once the command is attached.
    void registerShortcut() {
        KeyStroke shortcut = keyStroke;
        if (attached && shortcut != null) {
            // A command that loses its shortcut to another one in the window shows none there.
            view().window()
                    .shortcuts()
                    .register(
                            shortcut,
                            identity(),
                            new ShortcutAction(this, shortcut),
                            priority,
                            () -> action.putValue(Action.ACCELERATOR_KEY, null));
        }
    }

    boolean isAttached() {
        return attached;
    }

    ViewHost view() {
        return registry.view();
    }

    CommandRegistry registry() {
        return registry;
    }

    Class<?> owner() {
        return owner;
    }

    String identity() {
        return owner.getName() + "#" + id;
    }

    Mnemonics text() {
        return text;
    }

    Optional<KeyStroke> keyStroke() {
        return Optional.ofNullable(keyStroke);
    }

    @Nullable
    KeyStroke defaultKeyStroke() {
        return defaultKeyStroke;
    }

    boolean hasSelectedState() {
        return selectedSupplier != null;
    }

    boolean hasIcon() {
        return action.getValue(Action.SMALL_ICON) != null;
    }

    boolean isSystemAction() {
        boolean standard = owner == StandardCommands.class
                && Standard.of(id).map(Standard::isSystemAction).orElse(false);
        return standard || registry.isSystemActionOverride(this);
    }

    @Override
    public String toString() {
        return "BoundCommand[" + identity() + "]";
    }

    // A task the command started, with how the command was activated and what unblocks its input once it began.
    private static final class Started {
        private final RunningTask handle;
        private final CommandContext context;
        private Runnable unblock = () -> {};

        Started(RunningTask handle, CommandContext context) {
            this.handle = handle;
            this.context = context;
        }
    }
}
