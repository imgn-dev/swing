package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;

/**
 * The ready signal of the lifecycle, as an observable that views follow: a {@code ReadyListener} is a contribution to a
 * set, which views cannot inject, so {@link ReadySignal} sets this property.
 */
@Singleton
public final class Ready {

    private final Property<Boolean> ready = Property.of(false);

    @Inject
    Ready() {}

    void set() {
        ready.set(true);
    }

    /**
     * Returns whether the application is ready.
     *
     * @return the observable state, {@code true} once the main window is shown
     */
    public Observable<Boolean> isReady() {
        return ready;
    }
}
