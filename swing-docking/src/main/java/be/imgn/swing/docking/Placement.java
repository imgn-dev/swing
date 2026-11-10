package be.imgn.swing.docking;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where {@link Dock#open} docks a view: against a target, the open view of the target class opened last, or, when
 * {@code target} is {@code null} or no view of it is open, the whole dock.
 *
 * <ul>
 *   <li>{@link DockRegion#CENTER} adds the view as the last tab of the target's tab group, and selects it. Against the
 *       whole dock, the target is the active view's tab group; in an empty dock, the view fills it.
 *   <li>The other regions split the target, a tab group or the whole dock's arrangement, in two: the view, in a tab
 *       group of its own, takes {@code proportion} of the space, on that side, and the target the rest. In an empty
 *       dock, the view fills it.
 * </ul>
 *
 * @param target the class of the target view, or {@code null} for the whole dock
 * @param region where the view goes
 * @param proportion the view's share of the space, strictly between 0 and 1; a {@code CENTER} placement doesn't read it
 */
public record Placement(@Nullable Class<? extends DockView> target, DockRegion region, double proportion) {

    /** The proportion of {@link #tab()} and {@link #tabWith}, which {@code CENTER} doesn't read. */
    private static final double HALF = 0.5;

    /**
     * Checks the placement.
     *
     * @throws NullPointerException if {@code region} is {@code null}
     * @throws IllegalArgumentException if {@code proportion} is not finite or not strictly between 0 and 1
     */
    public Placement {
        Objects.requireNonNull(region, "region");
        if (!Double.isFinite(proportion) || proportion <= 0 || proportion >= 1) {
            throw new IllegalArgumentException("The proportion " + proportion + " is not strictly between 0 and 1");
        }
    }

    /**
     * Returns a placement against the whole dock.
     *
     * @param region where the view goes
     * @param proportion the view's share of the space
     * @return the placement
     * @throws NullPointerException if {@code region} is {@code null}
     * @throws IllegalArgumentException if {@code proportion} is not finite or not strictly between 0 and 1
     */
    public static Placement of(DockRegion region, double proportion) {
        return new Placement(null, region, proportion);
    }

    /**
     * Returns a placement as a tab of the active view's tab group: {@code of(CENTER, 0.5)}.
     *
     * @return the placement
     */
    public static Placement tab() {
        return of(DockRegion.CENTER, HALF);
    }

    /**
     * Returns a placement against the open view of a class opened last.
     *
     * @param target the class of the target view
     * @param region where the view goes
     * @param proportion the view's share of the space
     * @return the placement
     * @throws NullPointerException if {@code target} or {@code region} is {@code null}
     * @throws IllegalArgumentException if {@code proportion} is not finite or not strictly between 0 and 1
     */
    public static Placement beside(Class<? extends DockView> target, DockRegion region, double proportion) {
        return new Placement(Objects.requireNonNull(target, "target"), region, proportion);
    }

    /**
     * Returns a placement as a tab of the tab group of the open view of a class opened last: {@code beside(target,
     * CENTER, 0.5)}.
     *
     * @param target the class of the target view
     * @return the placement
     * @throws NullPointerException if {@code target} is {@code null}
     */
    public static Placement tabWith(Class<? extends DockView> target) {
        return beside(target, DockRegion.CENTER, HALF);
    }
}
