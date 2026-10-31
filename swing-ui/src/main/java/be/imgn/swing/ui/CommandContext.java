package be.imgn.swing.ui;

import be.imgn.gaia.core.platform.Platform;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** How a command was activated: the modifier keys held, and the trigger with the objects of its path. */
public final class CommandContext {

    private static final CommandContext PROGRAMMATIC = new CommandContext(new Trigger.Programmatic(), Set.of());

    private final Trigger trigger;
    private final Set<Modifier> modifiers;

    private CommandContext(Trigger trigger, Set<Modifier> modifiers) {
        this.trigger = trigger;
        this.modifiers = modifiers;
    }

    /**
     * Returns a context. {@link Modifier#SHORTCUT} is added when {@link Modifier#CONTROL} or {@link Modifier#META} is
     * given and is the platform's menu shortcut modifier.
     *
     * @param trigger the trigger
     * @param modifiers the modifier keys held
     * @return the context
     */
    public static CommandContext of(Trigger trigger, Modifier... modifiers) {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(modifiers, "modifiers");
        return of(trigger, Arrays.asList(modifiers), Platform.current().isMacOS());
    }

    static CommandContext of(Trigger trigger, Iterable<Modifier> modifiers, boolean macOS) {
        EnumSet<Modifier> set = EnumSet.noneOf(Modifier.class);
        modifiers.forEach(modifier -> set.add(Objects.requireNonNull(modifier, "modifier")));
        if (set.contains(macOS ? Modifier.META : Modifier.CONTROL)) {
            set.add(Modifier.SHORTCUT);
        }
        return new CommandContext(trigger, Set.copyOf(set));
    }

    /**
     * Returns the context of a command run by code: a {@link Trigger.Programmatic} trigger and no modifier.
     *
     * @return the context
     */
    static CommandContext programmatic() {
        return PROGRAMMATIC;
    }

    /**
     * Returns the modifier keys held when the command was activated.
     *
     * @return the modifiers, empty for the operating system's and programmatic triggers
     */
    public Set<Modifier> modifiers() {
        return modifiers;
    }

    /**
     * Returns the trigger, with the objects of the path that activated the command.
     *
     * @return the trigger
     */
    public Trigger trigger() {
        return trigger;
    }

    @Override
    public String toString() {
        return "CommandContext[" + trigger + ", " + modifiers + "]";
    }
}
