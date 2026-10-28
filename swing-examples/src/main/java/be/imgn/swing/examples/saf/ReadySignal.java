package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ReadyListener;

/** Sets {@link Ready} when the lifecycle signals that the application is ready. */
@Singleton
@IntoSet(ReadyListener.class)
public final class ReadySignal implements ReadyListener {

    private final Ready ready;

    @Inject
    ReadySignal(Ready ready) {
        this.ready = ready;
    }

    @Override
    public void ready() {
        ready.set();
    }
}
