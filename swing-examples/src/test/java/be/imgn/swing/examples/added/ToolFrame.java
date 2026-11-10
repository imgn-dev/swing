package be.imgn.swing.examples.added;

import be.imgn.inject.Inject;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FrameView;
import java.awt.GridLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A secondary frame that tests add views to, and whose veto they follow. */
public final class ToolFrame implements FrameView, ExitVeto {

    @Exposed
    final JPanel area = new JPanel(new GridLayout(0, 1));

    private final ToolLog log;

    @Inject
    ToolFrame(ToolLog log) {
        this.log = log;
    }

    @Override
    public JComponent content() {
        return area;
    }

    @Override
    public boolean canExit() {
        log.add("frame veto");
        return true;
    }
}
