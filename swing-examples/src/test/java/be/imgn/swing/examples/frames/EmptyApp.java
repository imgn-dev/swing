package be.imgn.swing.examples.frames;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Arguments;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.MultiFrame;

/** A multi-frame application that opens no frame, which fails its startup. */
@Application
public final class EmptyApp implements MultiFrame {

    @Inject
    EmptyApp() {}

    @Override
    public void start(Frames frames, Arguments arguments) {}
}
