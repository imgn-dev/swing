package be.imgn.swing.docking;

/** Where a {@link Placement} docks a view against its target: in its tab group, or on one of its sides. */
public enum DockRegion {
    /** As the last tab of the target's tab group. */
    CENTER,
    /** Above the target. */
    NORTH,
    /** Right of the target. */
    EAST,
    /** Below the target. */
    SOUTH,
    /** Left of the target. */
    WEST
}
