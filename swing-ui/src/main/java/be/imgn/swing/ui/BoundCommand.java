package be.imgn.swing.ui;

import be.imgn.swing.FailureHandler;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.internal.Guarded;
import be.imgn.swing.internal.NoTask;
import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.ui.internal.Edt;
import be.imgn.swing.ui.internal.EdtDelivery;
import be.imgn.swing.ui.internal.KeyStrokes;
import be.imgn.swing.ui.internal.Mnemonics;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.spi.ComponentResources;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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
    private final CommandResources resources;
    private final CommandAction action;
    private final Property<Boolean> blocked = Property.of(false);
    private final Property<Boolean> running = Property.of(false);
    // The tasks the command started that haven't ended, in activation order, those that wait included. On the EDT.
    private final List<Started> tasks = new ArrayList<>();
    private final @Nullable KeyStroke keyStroke;
    private final Mnemonics text;
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
        this.resources = resources;
        UiServices services = registry.services();
        boolean macOS = services.isMacOS();
        this.text = Mnemonics.parse(resources.string("text").orElse(id));
        this.keyStroke = resources
                .string("shortcut")
                .map(shortcut -> KeyStrokes.parse(shortcut, macOS))
                .orElse(null);
        this.action = new CommandAction(this);
        action.putValue(Action.NAME, text.text());
        action.putValue(Action.ACTION_COMMAND_KEY, id);
        if (!macOS && text.hasMnemonic()) {
            action.putValue(Action.MNEMONIC_KEY, text.keyCode());
            action.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, text.index());
        }
        resources.string("icon").ifPresent(path -> {
            Icon icon = services.icons().load(resources.iconPackage(), path);
            action.putValue(Action.SMALL_ICON, icon);
            action.putValue(Action.LARGE_ICON_KEY, icon);
        });
        if (keyStroke != null) {
            action.putValue(Action.ACCELERATOR_KEY, keyStroke);
        }
        resources.string("tool-tip-text").ifPresent(tip -> action.putValue(Action.SHORT_DESCRIPTION, tip));
        if (selectedSupplier != null) {
            action.putValue(Action.SELECTED_KEY, false);
        }
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
        resources.string("accessible-name").ifPresent(name -> ComponentResources.setAccessibleName(component, name));
        resources
                .string("accessible-description")
                .ifPresent(description -> ComponentResources.setAccessibleDescription(component, description));
        return component;
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
        KeyStroke shortcut = keyStroke;
        if (shortcut != null) {
            view.window().shortcuts().register(shortcut, identity(), new ShortcutAction(this, shortcut));
        }
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
