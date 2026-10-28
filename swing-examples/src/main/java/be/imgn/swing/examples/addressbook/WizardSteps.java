package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.inject.Provider;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.ViewScoped;
import java.util.List;

/**
 * The current step of {@link ContactWizardDialog}, with the previous and next commands that move it. The dialog, which
 * an assisted factory creates, can't depend on generated commands: inject reports an assisted constructor whose
 * parameters name a type generated in the same round as missing, so this bean holds them.
 */
@ViewScoped
// The framework reaches command targets from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class WizardSteps {

    final Property<Integer> step = Property.of(0);
    final Property<Integer> count = Property.of(1);
    final Observable<Boolean> hasPrevious = step.map(index -> index > 0);
    final Observable<Boolean> hasNext = Observable.combine(step, count, (index, steps) -> index < steps - 1);
    private final Provider<WizardStepsCommands> commands;

    @Inject
    WizardSteps(Provider<WizardStepsCommands> commands) {
        this.commands = commands;
    }

    /**
     * Returns the buttons that move between the steps.
     *
     * @return the previous and next commands
     */
    List<BoundCommand> buttons() {
        WizardStepsCommands own = commands.get();
        return List.of(own.previous(), own.next());
    }

    @Command(enabled = "hasPrevious")
    void previous() {
        step.set(step.get() - 1);
    }

    @Command(enabled = "hasNext")
    void next() {
        step.set(step.get() + 1);
    }
}
