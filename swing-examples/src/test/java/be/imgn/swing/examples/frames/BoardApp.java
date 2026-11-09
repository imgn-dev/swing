package be.imgn.swing.examples.frames;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Arguments;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.MultiFrame;

/** A multi-frame application that opens as many boards as its first argument says, one without arguments. */
@Application(name = "Board", vendor = "imgn", qualifier = "be")
public final class BoardApp implements MultiFrame, ExitVeto {

    private final BoardLog log;
    private boolean allowExit = true;

    @Inject
    BoardApp(BoardLog log) {
        this.log = log;
    }

    @Override
    public void start(Frames frames, Arguments arguments) {
        int count = arguments.values().isEmpty()
                ? 1
                : Integer.parseInt(arguments.values().getFirst());
        for (int index = 0; index < count; index++) {
            frames.open(BoardView.class);
        }
    }

    @Override
    public void ready() {
        log.add("ready");
    }

    void allowExit(boolean allowed) {
        allowExit = allowed;
    }

    @Override
    public boolean canExit() {
        log.add("application asked");
        return allowExit;
    }
}
