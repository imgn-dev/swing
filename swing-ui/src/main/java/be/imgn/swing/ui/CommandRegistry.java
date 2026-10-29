package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.swing.Scope;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.toml.TomlTable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every command of a view, keyed by the class the generated class is for ({@code StandardCommands} for standard
 * commands) and the id: asking twice for the same command returns the same {@link BoundCommand}. Generated code builds
 * commands through it.
 */
@ViewScoped
public final class CommandRegistry {

    private final ViewHost view;
    private final Injector injector;
    private final UiServices services;
    private final Map<Key, BoundCommand> commands = new LinkedHashMap<>();
    private final Map<Standard, BoundCommand> overrides = new EnumMap<>(Standard.class);
    private final Map<Standard, Class<? extends StandardCommands>> overridingClasses = new EnumMap<>(Standard.class);
    private final IdentityHashMap<StandardCommands, List<BoundCommand>> byInstance = new IdentityHashMap<>();
    private final StandardHandlers standards;
    // The instances under construction, the innermost first: a constructor that resolves another commands class nests.
    private final Deque<StandardCommands> constructing = new ArrayDeque<>();

    @Inject
    CommandRegistry(Window window, Scope scope, Injector injector, UiServices services) {
        this.view = services.registry().viewOf(scope);
        if (!Objects.equals(view.window().window(), window)) {
            throw new IllegalStateException("The view " + view.type().getName() + " is not in this window");
        }
        this.injector = injector;
        this.services = services;
        this.standards = new StandardHandlers(this);
    }

    /**
     * Returns a builder of a command.
     *
     * @param owner the class the generated class is for
     * @param id the id of the command
     * @return the builder
     */
    public CommandBuilder command(Class<?> owner, String id) {
        return new CommandBuilder(this, Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(id, "id"));
    }

    /**
     * Registers a command that overrides a standard command in this view: the generated injectors of view classes call
     * it, before the view is resolved, for each override of the view class and its superclasses.
     *
     * @param id the id of the standard command, such as {@code exit}
     * @param commands the generated class that declares the override
     * @throws IllegalArgumentException if the id is not a standard command's
     */
    public void override(String id, Class<? extends StandardCommands> commands) {
        Standard standard =
                Standard.of(id).orElseThrow(() -> new IllegalArgumentException(id + " is not a standard command"));
        overridingClasses.putIfAbsent(standard, commands);
    }

    BoundCommand bind(CommandBuilder builder) {
        Key key = new Key(builder.owner(), builder.id());
        BoundCommand existing = commands.get(key);
        if (existing != null) {
            return existing;
        }
        if (builder.builtHandler() == null && builder.builtTaskHandler() == null) {
            throw new IllegalStateException("The command " + key + " has no handler");
        }
        if (builder.builtBlock() != Block.NONE && builder.builtTaskHandler() == null) {
            throw new IllegalStateException("The command " + key + " blocks input but returns no task");
        }
        BoundCommand command = new BoundCommand(this, builder, resourcesOf(builder.owner(), builder.id()));
        commands.put(key, command);
        StandardCommands instance = constructing.peek();
        if (instance != null) {
            byInstance.computeIfAbsent(instance, ignored -> new ArrayList<>()).add(command);
        }
        recordOverride(key, command);
        return command;
    }

    /**
     * Records the overrides among the commands already bound, once the view class is known: a view created by an
     * assisted factory binds the commands of its constructor before its class is known.
     */
    void viewTypeKnown() {
        commands.forEach(this::recordOverride);
    }

    // A command of the view class, or of a superclass of it, whose id is a standard command's overrides it.
    private void recordOverride(Key key, BoundCommand command) {
        if (key.owner() != StandardCommands.class && key.owner().isAssignableFrom(view.type())) {
            Standard.of(key.id()).ifPresent(standard -> overrides.putIfAbsent(standard, command));
        }
    }

    private CommandResources resourcesOf(Class<?> owner, String id) {
        Map<Class<?>, TomlTable> tables = new LinkedHashMap<>();
        if (Standard.of(id).isPresent()) {
            tables.put(StandardCommands.class, section(StandardCommands.class, id));
        }
        Class<?> type = owner == StandardCommands.class ? view.type() : owner;
        tables.put(type, section(type, id));
        return CommandResources.of(tables);
    }

    private TomlTable section(Class<?> type, String id) {
        return CommandResources.section(services.resources().table(type), "commands", id);
    }

    BoundCommand standard(Standard standard) {
        BoundCommand override = overrides.get(standard);
        if (override != null) {
            return override;
        }
        Class<? extends StandardCommands> overriding = overridingClasses.get(standard);
        if (overriding != null) {
            injector.getInstance(overriding);
            override = overrides.get(standard);
            if (override != null) {
                return override;
            }
        }
        BoundCommand existing = commands.get(new Key(StandardCommands.class, standard.id()));
        return existing != null ? existing : standards.create(standard);
    }

    boolean isSystemActionOverride(BoundCommand command) {
        return overrides.entrySet().stream()
                .anyMatch(entry -> entry.getValue() == command && entry.getKey().isSystemAction());
    }

    void constructing(StandardCommands instance) {
        constructing.push(instance);
    }

    /**
     * Returns the commands an instance bound while it was constructed, and forgets them.
     *
     * @param instance the instance
     * @return the commands
     */
    List<BoundCommand> constructed(StandardCommands instance) {
        constructing.removeFirstOccurrence(instance);
        List<BoundCommand> bound = byInstance.remove(instance);
        return bound == null ? List.of() : bound;
    }

    ViewHost view() {
        return view;
    }

    Injector injector() {
        return injector;
    }

    UiServices services() {
        return services;
    }

    /** The class the generated class is for, and the id. */
    private record Key(Class<?> owner, String id) {
        @Override
        public String toString() {
            return owner.getName() + "#" + id;
        }
    }
}
