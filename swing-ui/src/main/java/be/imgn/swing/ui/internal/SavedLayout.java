package be.imgn.swing.ui.internal;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The saved layout of a dock, as bound from and written to the window state: a split, with {@code split},
 * {@code proportion}, {@code first} and {@code second}, or a tab group, with {@code tabs} and {@code selected}. The
 * module that owns the dock checks which one it is; the window state only binds it.
 *
 * @param split the orientation of a split, {@code "horizontal"} or {@code "vertical"}
 * @param proportion the share of a split's first part
 * @param first a split's first part
 * @param second a split's second part
 * @param tabs the binary names of the classes of a tab group's views, in tab order
 * @param selected the index of a tab group's selected view
 */
public record SavedLayout(
        @Nullable String split,
        @Nullable Double proportion,
        @Nullable SavedLayout first,
        @Nullable SavedLayout second,
        @Nullable List<String> tabs,
        @Nullable Integer selected) {}
