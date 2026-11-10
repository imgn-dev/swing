package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import javax.swing.JComponent;

/**
 * An application whose frame view holds a dock, with a first layout of three views: {@code Alpha} as a tab, then
 * {@code Beta} west of it, then {@code Gamma} south of {@code Beta}.
 */
@Application
public final class DockApp implements SingleFrame {

    static final double BETA = 0.3;
    static final double GAMMA = 0.5;

    private final Dock dock;

    @Inject
    DockApp(Dock dock) {
        this.dock = dock;
    }

    @Override
    public JComponent content() {
        return dock.area(opened -> {
            opened.open(AlphaView.class, Placement.tab());
            opened.open(BetaView.class, Placement.of(DockRegion.WEST, BETA));
            opened.open(GammaView.class, Placement.beside(BetaView.class, DockRegion.SOUTH, GAMMA));
        });
    }
}
