package be.imgn.swing.examples.frames;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.SingleFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** A single-frame application that opens secondary frames. */
@Application
public final class HostApp implements SingleFrame {

    private final Frames frames;

    @Inject
    HostApp(Frames frames) {
        this.frames = frames;
    }

    @Override
    public JComponent content() {
        return new JLabel("Host");
    }

    BoardView openBoard() {
        return frames.open(BoardView.class);
    }

    PaletteView openPalette(PaletteView.Shade shade) {
        return frames.open(PaletteView.class, shade);
    }

    Frames frames() {
        return frames;
    }
}
