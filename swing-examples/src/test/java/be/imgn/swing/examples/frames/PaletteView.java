package be.imgn.swing.examples.frames;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FrameView;
import be.imgn.swing.ui.Variants;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** A frame view with variants, which differ by their resources. */
public final class PaletteView implements FrameView {

    @Exposed
    final JLabel label = new JLabel();

    @Inject
    PaletteView() {}

    @Override
    public JComponent content() {
        return label;
    }

    /** The variants of the palette. */
    @Variants
    public enum Shade {
        /** Red. */
        RED,
        /** Blue. */
        BLUE
    }

    /** Not the variants of the palette. */
    public enum Other {
        /** A constant. */
        ANY
    }
}
