package be.imgn.swing.docking;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import javax.swing.JComponent;

/** An application whose dock starts empty, which the tests fill. */
@Application
public final class EmptyDockApp implements SingleFrame {

    private final Dock dock;

    @Inject
    EmptyDockApp(Dock dock) {
        this.dock = dock;
    }

    @Override
    public JComponent content() {
        return dock.area(opened -> {});
    }
}
