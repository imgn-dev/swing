package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.DialogView;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A dialog view that injects Docked, which only a dock view may. */
public final class DockedDialog implements DialogView<Void> {

    @Inject
    DockedDialog(Docked docked) {}

    @Override
    public JComponent content() {
        return new JPanel();
    }

    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }
}
