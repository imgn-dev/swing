package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Injector;
import be.imgn.inject.TypeLiteral;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.CommandRegistry;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The order in which a change reaches the EDT when it is made off the EDT: a view's {@code Ui.bind} target sees the
 * commands of its view following the same change, however the EDT runs between the listeners of the change.
 */
class CommandStateDeliveryTest {

    private static final Class<?> OWNER = CommandStateDeliveryTest.class;

    @Test
    void aViewBindingSeesTheCommandFollowingTheChangeEvenWhenTheEdtRunsBeforeTheCommandIsNotified() {
        Property<Boolean> enabled = Property.of(false);
        List<String> seen = new CopyOnWriteArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Ui ui = app.bean(Ui.class);
            Scope scope = app.bean(Scope.class);
            List<BoundCommand> holder = new CopyOnWriteArrayList<>();
            // The view's listener first, then one between it and the command's, during which the EDT runs whatever
            // the change has posted so far.
            app.run(() -> {
                ui.bind(
                        value -> {
                            if (!holder.isEmpty()) {
                                seen.add(value + " with the command enabled "
                                        + holder.getFirst().asAction().isEnabled());
                            }
                        },
                        enabled);
                enabled.subscribe(scope, ignored -> {
                    try {
                        EventQueue.invokeAndWait(() -> {});
                    } catch (InterruptedException | InvocationTargetException e) {
                        throw new IllegalStateException(e);
                    }
                });
            });
            holder.addAll(attach(
                    app,
                    registry -> List.of(registry.command(OWNER, "go")
                            .handler(context -> {})
                            .enabled(() -> enabled)
                            .bind())));

            enabled.set(true);
            app.run(() -> {});

            assertThat(seen).containsExactly("true with the command enabled true");
        }
    }

    // Builds commands as a generated <Class>Commands does, in its constructor, then reports the instance to the
    // construction listeners, as inject does, which attach its commands.
    private static List<BoundCommand> attach(
            AppTester<CounterApp> app, Function<CommandRegistry, List<BoundCommand>> build) {
        CommandRegistry registry = app.bean(CommandRegistry.class);
        Injector injector = app.bean(Injector.class);
        Set<ConstructionListener> listeners = injector.getInstance(new TypeLiteral<Set<ConstructionListener>>() {});
        return app.call(() -> {
            BuiltCommands commands = new BuiltCommands(registry, build);
            listeners.forEach(listener -> listener.constructed(commands, injector));
            return commands.bound;
        });
    }

    private static final class BuiltCommands extends StandardCommands {
        private final List<BoundCommand> bound;

        BuiltCommands(CommandRegistry registry, Function<CommandRegistry, List<BoundCommand>> build) {
            super(registry);
            this.bound = build.apply(registry);
        }
    }
}
