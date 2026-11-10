package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A dock view whose build opens another view, which no build may. */
public final class NestingView implements DockView {

    private final Dock dock;

    @Inject
    NestingView(Dock dock) {
        this.dock = dock;
    }

    @Override
    public JComponent content() {
        dock.open(AlphaView.class, Placement.tab());
        return new JPanel();
    }
}
