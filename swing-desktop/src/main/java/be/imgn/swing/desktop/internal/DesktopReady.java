package be.imgn.swing.desktop.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ReadyListener;

/** Hands the events the desktop held until the application is ready: second launches, and opened files. */
@Singleton
@IntoSet(ReadyListener.class)
public final class DesktopReady implements ReadyListener {

    private final SingleInstance instance;
    private final OpenFiles files;

    @Inject
    DesktopReady(SingleInstance instance, OpenFiles files) {
        this.instance = instance;
        this.files = files;
    }

    @Override
    public void ready() {
        instance.ready();
        files.ready();
    }
}
