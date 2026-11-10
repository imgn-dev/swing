package be.imgn.swing.wizard;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A wizard that gives its steps as {@link Misuse} says. */
public final class MisusedDialog implements DialogView<Void> {

    @Exposed
    final JPanel first = new JPanel();

    @Exposed
    final JPanel second = new JPanel();

    private final Wizard wizard;
    private final Misuse misuse;

    @Inject
    MisusedDialog(Wizard wizard, Misuse misuse) {
        this.wizard = wizard;
        this.misuse = misuse;
    }

    @Override
    public JComponent content() {
        return switch (misuse.kind()) {
            case NONE -> wizard.steps();
            case TWICE -> {
                wizard.steps(first);
                yield wizard.steps(second);
            }
            case DUPLICATE -> wizard.steps(first, first);
            case UNEXPOSED -> wizard.steps(first, new JPanel());
        };
    }

    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }
}
