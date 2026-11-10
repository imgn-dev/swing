package be.imgn.swing.internal;

import be.imgn.swing.Appearance;
import be.imgn.swing.Appearances;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;

/**
 * An appearance that changes in the order a change requires: the resources switch to it, the look and feel is installed
 * again, the open windows are refreshed, then {@link #current()} changes.
 */
public final class SwitchableAppearances implements Appearances {

    private final ResourceState state;
    private final Refresher refresher;
    private final Property<Appearance> current;

    /**
     * Creates the appearance.
     *
     * @param initial the appearance until it starts or changes
     * @param state the state of the resources
     * @param refresher refreshes the open windows
     */
    public SwitchableAppearances(Appearance initial, ResourceState state, Refresher refresher) {
        this.state = state;
        this.refresher = refresher;
        this.current = Property.of(initial);
    }

    @Override
    public Observable<Appearance> current() {
        return current;
    }

    /**
     * Sets the starting appearance, before anything reads resources: nothing is refreshed.
     *
     * @param appearance the appearance
     */
    public void start(Appearance appearance) {
        state.appearance(appearance);
        current.set(appearance);
    }

    /**
     * Changes the appearance, on the UI thread. An appearance equal to the current one changes nothing.
     *
     * @param appearance the new appearance
     * @param install installs the look and feel of the new appearance, once the resources read it
     */
    public void change(Appearance appearance, Runnable install) {
        if (appearance == current.get()) {
            return;
        }
        state.appearance(appearance);
        install.run();
        refresher.refresh();
        current.set(appearance);
    }
}
