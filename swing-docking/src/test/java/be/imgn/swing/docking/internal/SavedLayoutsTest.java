package be.imgn.swing.docking.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.docking.DockLayout.Orientation;
import be.imgn.swing.docking.DockView;
import be.imgn.swing.ui.internal.SavedLayout;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

/** The form of a dock's saved layout: what reading it keeps, leaves out and discards. */
class SavedLayoutsTest {

    private static final Map<String, Class<?>> KNOWN = Map.of(
            One.class.getName(), One.class,
            Two.class.getName(), Two.class,
            String.class.getName(), String.class);
    private static final Function<String, Optional<Class<?>>> CLASSES = SavedLayoutsTest::classOf;

    private static Optional<Class<?>> classOf(String name) {
        return Optional.ofNullable(KNOWN.get(name));
    }

    private static SavedLayout tabs(int selected, String... names) {
        return new SavedLayout(null, null, null, null, List.of(names), selected);
    }

    private static SavedLayout split(String orientation, double proportion, SavedLayout first, SavedLayout second) {
        return new SavedLayout(orientation, proportion, first, second, null, null);
    }

    @Test
    void readsSplitsAndTabGroups() {
        SavedLayout saved =
                split("vertical", 0.6, tabs(1, One.class.getName(), Two.class.getName()), tabs(0, "p.Gone"));

        assertThat(SavedLayouts.read(saved, CLASSES))
                .contains(new SavedLayouts.Plan.Tabs(List.of(One.class, Two.class), 1));
    }

    @Test
    void keepsASplitWhoseBothPartsAreLeft() {
        SavedLayout saved = split("horizontal", 0.2, tabs(0, One.class.getName()), tabs(0, Two.class.getName()));

        assertThat(SavedLayouts.read(saved, CLASSES))
                .contains(new SavedLayouts.Plan.Split(
                        Orientation.HORIZONTAL,
                        0.2,
                        new SavedLayouts.Plan.Tabs(List.of(One.class), 0),
                        new SavedLayouts.Plan.Tabs(List.of(Two.class), 0)));
    }

    @Test
    void leavesOutAClassThatIsNotADockView() {
        SavedLayout saved = tabs(0, String.class.getName(), One.class.getName());

        assertThat(SavedLayouts.read(saved, CLASSES)).contains(new SavedLayouts.Plan.Tabs(List.of(One.class), 0));
    }

    @Test
    void discardsALayoutThatBreaksItsForm() {
        assertThat(SavedLayouts.read(tabs(2, One.class.getName()), CLASSES)).isEmpty();
        assertThat(SavedLayouts.read(tabs(0, One.class.getName(), One.class.getName()), CLASSES))
                .isEmpty();
        assertThat(SavedLayouts.read(split("diagonal", 0.5, tabs(0, "a"), tabs(0, "b")), CLASSES))
                .isEmpty();
        assertThat(SavedLayouts.read(split("vertical", 1.5, tabs(0, "a"), tabs(0, "b")), CLASSES))
                .isEmpty();
        assertThat(SavedLayouts.read(new SavedLayout("vertical", 0.5, null, null, List.of("a"), 0), CLASSES))
                .isEmpty();
    }

    /** A dock view class. */
    static final class One implements DockView {
        @Override
        public JComponent content() {
            return new JComponent() {};
        }
    }

    /** Another dock view class. */
    static final class Two implements DockView {
        @Override
        public JComponent content() {
            return new JComponent() {};
        }
    }
}
