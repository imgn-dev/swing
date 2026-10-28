package be.imgn.swing.ui;

import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.ui.internal.Edt;
import be.imgn.swing.ui.internal.EdtDelivery;
import be.imgn.swing.ui.internal.KeyStrokes;
import be.imgn.swing.ui.internal.Mnemonics;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.support.ComponentResources;
import java.util.Objects;
import java.util.Optional;
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
    private final CommandResources resources;
    private final CommandAction action;
    private final Property<Boolean> blocked = Property.of(false);
    private final @Nullable KeyStroke keyStroke;
    private final Mnemonics text;
    private int blockCount;
    private @Nullable Observable<Boolean> enabled;
    private @Nullable Property<Boolean> selected;
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
        if (handler != null) {
            handler.accept(context);
        } else if (taskHandler != null) {
            Task<?> task = Objects.requireNonNull(taskHandler.apply(context), "The handler returned no task");
            start(task, context);
        }
        return true;
    }

    private void start(Task<?> task, CommandContext context) {
        UiServices services = registry.services();
        RunningTask running = services.tasks().start(task, view().scope(), id);
        Runnable unblock = TaskBlocking.block(services, view(), running, block, this, context);
        running.whenEnded(() -> services.executor().execute(unblock));
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
        return enabled().get() && !blocked.get();
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
        action.setEnabled(effective.get());
        EdtDelivery.subscribe(view.scope(), effective, action::setEnabled);
        if (selectedSupplier != null) {
            Property<Boolean> property = selected();
            action.putValue(Action.SELECTED_KEY, property.get());
            EdtDelivery.subscribe(view.scope(), property, value -> action.putValue(Action.SELECTED_KEY, value));
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
}
