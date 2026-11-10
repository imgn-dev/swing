package be.imgn.swing.docking.internal;

import be.imgn.swing.docking.DockRegion;
import java.util.Optional;
import javax.swing.JComponent;
import org.jspecify.annotations.Nullable;

/**
 * What draws a dock: ModernDocking in a running application, the headless surface in the test harness. A dock decides
 * the target of each placement; a surface docks, undocks and selects as it is told, and reports what the user does.
 * Used on the EDT.
 */
public interface DockSurface {

    /**
     * Returns the dock's area, the component its frame view places.
     *
     * @return the area
     */
    JComponent area();

    /**
     * Docks an entry: fills an empty surface; adds it as the last tab of the target's tab group, selected, for
     * {@code CENTER}; else splits the target's tab group, or the whole arrangement when the target is {@code null}, in
     * two, the entry taking {@code proportion} of the space on the region's side.
     *
     * @param entry the entry
     * @param target the target's entry, or {@code null} for the whole arrangement
     * @param region where the entry goes
     * @param proportion the entry's share of the space, for a region other than {@code CENTER}
     */
    void dock(DockEntry entry, @Nullable DockEntry target, DockRegion region, double proportion);

    /**
     * Holds an entry that a restored arrangement will place, without showing it yet.
     *
     * @param entry the entry
     */
    void stage(DockEntry entry);

    /**
     * Arranges the staged entries as a restored layout says.
     *
     * @param arrangement the arrangement, whose entries are those staged
     */
    void arrange(DockNode arrangement);

    /**
     * Undocks an entry: a tab group left with none leaves, and the split that held it is replaced with its other part.
     *
     * @param entry the entry
     */
    void undock(DockEntry entry);

    /**
     * Selects an entry's tab in its tab group, without moving the focus.
     *
     * @param entry the entry
     */
    void select(DockEntry entry);

    /**
     * Shows an entry's title and icon again, after either changed.
     *
     * @param entry the entry
     */
    void changed(DockEntry entry);

    /**
     * Returns the arrangement of the entries.
     *
     * @return the arrangement, empty when no entry is docked
     */
    Optional<DockNode> layout();

    /** Releases what the surface holds, when its frame closes. */
    void dispose();

    /** What a surface reports to its dock. */
    interface Listener {

        /**
         * Reports that an entry's tab became the selected one of its group.
         *
         * @param entry the entry
         */
        void selected(DockEntry entry);

        /**
         * Reports that the user asked to close an entry, through its header's close button.
         *
         * @param entry the entry
         */
        void closeRequested(DockEntry entry);
    }
}
