package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.Appearance;
import be.imgn.swing.DisplayLocale;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SwitchableAppearancesTest {

    private final ResourceState state = new ResourceState(new DisplayLocale(Locale.ROOT));
    private final List<String> events = new ArrayList<>();
    private final SwitchableAppearances appearances = new SwitchableAppearances(
            Appearance.LIGHT,
            state,
            () -> events.add("refresh in " + state.snapshot().appearance()));
    private final DisposalScope scope = DisposalScope.root("test");

    @Test
    void switchesTheResourcesThenInstallsThenRefreshesThenChangesTheAppearance() {
        appearances.current().subscribe(scope, appearance -> events.add("current " + appearance));

        appearances.change(
                Appearance.DARK,
                () -> events.add("install in " + state.snapshot().appearance()));

        assertThat(events).containsExactly("install in DARK", "refresh in DARK", "current DARK");
    }

    @Test
    void changesNothingForTheCurrentAppearance() {
        appearances.change(Appearance.LIGHT, () -> events.add("install"));

        assertThat(events).isEmpty();
    }

    @Test
    void startsWithoutRefreshing() {
        appearances.start(Appearance.DARK);

        assertThat(appearances.current().get()).isEqualTo(Appearance.DARK);
        assertThat(state.snapshot().appearance()).isEqualTo(Appearance.DARK);
        assertThat(events).isEmpty();
    }
}
