package be.imgn.swing.testing;

import be.imgn.swing.Appearance;
import be.imgn.swing.Appearances;
import be.imgn.swing.Observable;
import be.imgn.swing.internal.Refresher;
import be.imgn.swing.internal.ResourceState;
import be.imgn.swing.internal.SwitchableAppearances;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The tester's appearance, which starts light and changes only when a test changes it, as the system would, so that the
 * system's own setting never reaches a test. The look and feel isn't installed again: the tester installs none.
 */
final class TesterAppearances implements Appearances {

    private @Nullable SwitchableAppearances appearances;

    // Once the root injector is built, which holds the resources and the refresh.
    void attach(ResourceState state, Refresher refresher) {
        appearances = new SwitchableAppearances(Appearance.LIGHT, state, refresher);
    }

    @Override
    public Observable<Appearance> current() {
        return attached().current();
    }

    // On the EDT.
    void change(Appearance appearance) {
        attached().change(appearance, () -> {});
    }

    private SwitchableAppearances attached() {
        return Objects.requireNonNull(appearances, "The tester's appearance is read before the injector is built");
    }
}
