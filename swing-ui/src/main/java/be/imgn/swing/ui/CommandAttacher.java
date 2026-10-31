package be.imgn.swing.ui;

import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import java.util.List;

/**
 * Attaches the commands of each {@link StandardCommands} instance, generated {@code <Class>Commands} included: an
 * instance constructed while the framework builds a view is attached when that build ends, any other at once.
 */
@Singleton
@IntoSet(ConstructionListener.class)
final class CommandAttacher implements ConstructionListener {

    @Inject
    CommandAttacher() {}

    @Override
    public void constructed(Object instance, Injector injector) {
        if (instance instanceof StandardCommands commands) {
            CommandRegistry registry = commands.registry();
            List<BoundCommand> own = registry.constructed(commands);
            registry.view().attach(() -> attach(registry, own));
        }
    }

    private static void attach(CommandRegistry registry, List<BoundCommand> own) {
        own.forEach(BoundCommand::attach);
        boolean dialog = registry.view().dialog().isPresent();
        for (Standard standard : Standard.values()) {
            if (dialog || (standard != Standard.OK && standard != Standard.CANCEL)) {
                registry.standard(standard).attach();
            }
        }
    }
}
