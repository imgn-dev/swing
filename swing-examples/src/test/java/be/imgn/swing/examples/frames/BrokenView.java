package be.imgn.swing.examples.frames;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.FrameView;
import javax.swing.JComponent;

/** A frame view whose content fails, so that opening its frame fails once the view exists. */
public final class BrokenView implements FrameView {

    @Inject
    BrokenView() {}

    @Override
    public JComponent content() {
        throw new IllegalStateException("The content of the broken view fails");
    }
}
