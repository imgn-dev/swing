package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A dock view whose build asks for a view that is already open, which no build may either. */
public final class ReopeningView implements DockView {

    private final Dock dock;

    @Inject
    ReopeningView(Dock dock) {
        this.dock = dock;
    }

    @Override
    public JComponent content() {
        dock.open(AlphaView.class, Placement.tab());
        return new JPanel();
    }
}
