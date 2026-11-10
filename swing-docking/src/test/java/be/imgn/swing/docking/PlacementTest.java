package be.imgn.swing.docking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/** The rules of placements and layouts, which their constructors check. */
class PlacementTest {

    private static final DockView ONE = new Stub();
    private static final DockView TWO = new Stub();

    @Test
    void givesItsFactoriesTheirTargetsAndRegions() {
        assertThat(Placement.tab()).isEqualTo(new Placement(null, DockRegion.CENTER, 0.5));
        assertThat(Placement.of(DockRegion.WEST, 0.3)).isEqualTo(new Placement(null, DockRegion.WEST, 0.3));
        assertThat(Placement.tabWith(AlphaView.class))
                .isEqualTo(new Placement(AlphaView.class, DockRegion.CENTER, 0.5));
        assertThat(Placement.beside(AlphaView.class, DockRegion.SOUTH, 0.4))
                .isEqualTo(new Placement(AlphaView.class, DockRegion.SOUTH, 0.4));
    }

    @Test
    void rejectsAProportionNotStrictlyBetweenZeroAndOne() {
        for (double proportion : new double[] {0, 1, -0.1, 1.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatIllegalArgumentException().isThrownBy(() -> Placement.of(DockRegion.EAST, proportion));
        }
    }

    @Test
    void rejectsATabGroupWithNoViewOrWhoseSelectedViewIsNotOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DockLayout.Tabs(List.of(), ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new DockLayout.Tabs(List.of(ONE), TWO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DockLayout.Split(
                        DockLayout.Orientation.VERTICAL,
                        1,
                        new DockLayout.Tabs(List.of(ONE), ONE),
                        new DockLayout.Tabs(List.of(TWO), TWO)));
    }

    @Test
    void copiesTheViewsOfATabGroupUnmodifiable() {
        List<DockView> views = new ArrayList<>(List.of(ONE, TWO));
        DockLayout.Tabs tabs = new DockLayout.Tabs(views, ONE);
        views.clear();

        assertThat(tabs.views()).containsExactly(ONE, TWO);
        assertThatThrownBy(() -> tabs.views().add(ONE)).isInstanceOf(UnsupportedOperationException.class);
    }

    /** A dock view that no dock opens. */
    static final class Stub implements DockView {
        @Override
        public JComponent content() {
            return new JPanel();
        }
    }
}
