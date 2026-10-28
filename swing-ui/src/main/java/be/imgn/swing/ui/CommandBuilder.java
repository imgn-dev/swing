package be.imgn.swing.ui;

import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Builds a command of a {@link CommandRegistry}; generated code calls it. */
public final class CommandBuilder {

    private final CommandRegistry registry;
    private final Class<?> owner;
    private final String id;
    private @Nullable Consumer<CommandContext> handler;
    private @Nullable Function<CommandContext, Task<?>> taskHandler;
    private @Nullable Supplier<Observable<Boolean>> enabled;
    private @Nullable Supplier<Property<Boolean>> selected;
    private Block block = Block.NONE;

    CommandBuilder(CommandRegistry registry, Class<?> owner, String id) {
        this.registry = registry;
        this.owner = owner;
        this.id = id;
    }

    /**
     * Sets a {@code void} handler.
     *
     * @param handler the handler, called on the EDT
     * @return this builder
     */
    public CommandBuilder handler(Consumer<CommandContext> handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.taskHandler = null;
        return this;
    }

    /**
     * Sets a handler returning a {@code Task}, which the command runs with its input blocking.
     *
     * @param handler the handler, called on the EDT
     * @return this builder
     */
    public CommandBuilder taskHandler(Function<CommandContext, Task<?>> handler) {
        this.taskHandler = Objects.requireNonNull(handler, "handler");
        this.handler = null;
        return this;
    }

    /**
     * Sets the {@code enabled} observable, read when the command is attached.
     *
     * @param enabled supplies the observable
     * @return this builder
     */
    public CommandBuilder enabled(Supplier<Observable<Boolean>> enabled) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        return this;
    }

    /**
     * Sets the {@code selected} property, read when the command is attached; the command becomes a toggle.
     *
     * @param selected supplies the property
     * @return this builder
     */
    public CommandBuilder selected(Supplier<Property<Boolean>> selected) {
        this.selected = Objects.requireNonNull(selected, "selected");
        return this;
    }

    /**
     * Sets the input blocking of a {@link #taskHandler(Function)}.
     *
     * @param block the input blocking
     * @return this builder
     */
    public CommandBuilder block(Block block) {
        this.block = Objects.requireNonNull(block, "block");
        return this;
    }

    /**
     * Returns the command, created on the first call for its class and id; later calls return the same one.
     *
     * @return the command
     * @throws IllegalStateException if no handler is set, or a block other than {@code NONE} is set without a task
     *     handler
     */
    public BoundCommand bind() {
        return registry.bind(this);
    }

    Class<?> owner() {
        return owner;
    }

    String id() {
        return id;
    }

    @Nullable
    Consumer<CommandContext> builtHandler() {
        return handler;
    }

    @Nullable
    Function<CommandContext, Task<?>> builtTaskHandler() {
        return taskHandler;
    }

    @Nullable
    Supplier<Observable<Boolean>> builtEnabled() {
        return enabled;
    }

    @Nullable
    Supplier<Property<Boolean>> builtSelected() {
        return selected;
    }

    Block builtBlock() {
        return block;
    }
}
