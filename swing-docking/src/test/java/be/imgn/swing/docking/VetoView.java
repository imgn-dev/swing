package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import be.imgn.swing.ExitVeto;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A dock view that may refuse to close. */
public final class VetoView implements DockView, ExitVeto {

    private final DockLog log;

    @Inject
    VetoView(DockLog log) {
        this.log = log;
    }

    @Override
    public JComponent content() {
        return new JPanel();
    }

    @Override
    public boolean canExit() {
        log.add("veto");
        return log.closable();
    }
}
