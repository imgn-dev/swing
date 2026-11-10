package be.imgn.swing.docking.internal;

import be.imgn.swing.docking.DockLayout;
import be.imgn.swing.docking.DockView;
import be.imgn.swing.ui.internal.SavedLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Converts a dock's arrangement to the layout its window state saves, and a saved layout back to the views to open:
 * splits with {@code split}, {@code proportion}, {@code first} and {@code second}; tab groups with {@code tabs}, the
 * binary names of their views' classes, and {@code selected}.
 */
public final class SavedLayouts {

    private static final System.Logger LOGGER = System.getLogger(SavedLayouts.class.getName());
    private static final String HORIZONTAL = "horizontal";
    private static final String VERTICAL = "vertical";

    private SavedLayouts() {}

    /**
     * Returns the layout an arrangement saves: the entries {@code saved} rejects, the views opened through a factory,
     * are left out, a tab group left with none is left out, and a split left with one part is replaced with that part;
     * a group's {@code selected} then points to its selected entry when it is kept, else to its first.
     *
     * @param arrangement the arrangement
     * @param saved whether an entry is saved
     * @return the layout, empty when no entry is left
     */
    public static Optional<SavedLayout> save(DockNode arrangement, Predicate<DockEntry> saved) {
        return switch (arrangement) {
            case DockNode.Split split -> {
                Optional<SavedLayout> first = save(split.first(), saved);
                Optional<SavedLayout> second = save(split.second(), saved);
                if (first.isEmpty() || second.isEmpty()) {
                    yield first.or(() -> second);
                }
                yield Optional.of(new SavedLayout(
                        split.orientation() == DockLayout.Orientation.HORIZONTAL ? HORIZONTAL : VERTICAL,
                        split.proportion(),
                        first.get(),
                        second.get(),
                        null,
                        null));
            }
            case DockNode.Tabs tabs -> {
                List<DockEntry> kept = tabs.entries().stream().filter(saved).toList();
                if (kept.isEmpty()) {
                    yield Optional.empty();
                }
                int selected = Math.max(0, kept.indexOf(tabs.selected()));
                yield Optional.of(new SavedLayout(
                        null,
                        null,
                        null,
                        null,
                        kept.stream()
                                .map(entry -> entry.host().type().getName())
                                .toList(),
                        selected));
            }
        };
    }

    /**
     * Reads a saved layout. A table that is neither a split nor a tab group as described, a proportion not strictly
     * between 0 and 1, a {@code selected} out of range, or a class named twice, discards the whole layout, with a
     * {@code WARNING}. A name that matches no view class, or a class that isn't a dock view class, is logged at
     * {@code WARNING}, naming it, and left out, as views opened through a factory are when saving.
     *
     * @param saved the saved layout
     * @param classes finds a view class by its binary name
     * @return the views to open and how to arrange them, empty when the layout is discarded or no view is left
     */
    public static Optional<Plan> read(SavedLayout saved, Function<String, Optional<Class<?>>> classes) {
        Optional<String> problem = check(saved, new HashSet<>());
        if (problem.isPresent()) {
            LOGGER.log(System.Logger.Level.WARNING, "Discarded the saved layout of a dock: " + problem.get());
            return Optional.empty();
        }
        return plan(saved, classes);
    }

    // The first reason the layout is not one, or empty when it is.
    private static Optional<String> check(SavedLayout node, Set<String> names) {
        boolean split =
                node.split() != null || node.proportion() != null || node.first() != null || node.second() != null;
        boolean tabs = node.tabs() != null || node.selected() != null;
        if (split == tabs) {
            return Optional.of("a table is neither a split nor a tab group");
        }
        return split ? checkSplit(node, names) : checkTabs(node, names);
    }

    private static Optional<String> checkTabs(SavedLayout node, Set<String> names) {
        List<String> views = node.tabs();
        Integer selected = node.selected();
        if (views == null || views.isEmpty() || selected == null) {
            return Optional.of("a tab group needs tabs and selected");
        }
        if (selected < 0 || selected >= views.size()) {
            return Optional.of("the selected index " + selected + " is out of range");
        }
        return views.stream()
                .filter(name -> !names.add(name))
                .findFirst()
                .map(name -> "the class " + name + " is named twice");
    }

    private static Optional<String> checkSplit(SavedLayout node, Set<String> names) {
        String orientation = node.split();
        Double proportion = node.proportion();
        SavedLayout first = node.first();
        SavedLayout second = node.second();
        if (orientation == null || proportion == null || first == null || second == null) {
            return Optional.of("a split needs split, proportion, first and second");
        }
        if (!orientation.equals(HORIZONTAL) && !orientation.equals(VERTICAL)) {
            return Optional.of("the split " + orientation + " is neither horizontal nor vertical");
        }
        if (!(proportion > 0 && proportion < 1)) {
            return Optional.of("the proportion " + proportion + " is not strictly between 0 and 1");
        }
        return check(first, names).or(() -> check(second, names));
    }

    // The views of a checked layout, left out when no dock view class has their name, and pruned as saving prunes.
    private static Optional<Plan> plan(SavedLayout node, Function<String, Optional<Class<?>>> classes) {
        SavedLayout first = node.first();
        SavedLayout second = node.second();
        String orientation = node.split();
        Double proportion = node.proportion();
        if (first == null || second == null || orientation == null || proportion == null) {
            return planTabs(node, classes);
        }
        Optional<Plan> one = plan(first, classes);
        Optional<Plan> other = plan(second, classes);
        if (one.isEmpty() || other.isEmpty()) {
            return one.or(() -> other);
        }
        return Optional.of(new Plan.Split(
                orientation.equals(HORIZONTAL) ? DockLayout.Orientation.HORIZONTAL : DockLayout.Orientation.VERTICAL,
                proportion,
                one.get(),
                other.get()));
    }

    private static Optional<Plan> planTabs(SavedLayout node, Function<String, Optional<Class<?>>> classes) {
        List<String> names = node.tabs();
        Integer selected = node.selected();
        if (names == null || selected == null) {
            return Optional.empty();
        }
        List<Class<? extends DockView>> types = new ArrayList<>();
        @Nullable Class<? extends DockView> chosen = null;
        for (int index = 0; index < names.size(); index++) {
            Optional<Class<? extends DockView>> type = dockView(names.get(index), classes);
            if (type.isPresent()) {
                types.add(type.get());
                if (index == selected) {
                    chosen = type.get();
                }
            }
        }
        if (types.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Plan.Tabs(types, chosen == null ? 0 : types.indexOf(chosen)));
    }

    private static Optional<Class<? extends DockView>> dockView(
            String name, Function<String, Optional<Class<?>>> classes) {
        Optional<Class<?>> found = classes.apply(name);
        if (found.isEmpty() || !DockView.class.isAssignableFrom(found.get())) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Left out " + name + " of the saved layout of a dock: it names no dock view class that a dock opens"
                            + " by its class");
            return Optional.empty();
        }
        return Optional.of(found.get().asSubclass(DockView.class));
    }

    /** The views a saved layout opens, and how to arrange them. */
    public sealed interface Plan {

        /**
         * A split.
         *
         * @param orientation where {@code first} is
         * @param proportion {@code first}'s share of the space
         * @param first the first part
         * @param second the second part
         */
        record Split(DockLayout.Orientation orientation, double proportion, Plan first, Plan second) implements Plan {}

        /**
         * A tab group.
         *
         * @param types the classes of its views, in tab order
         * @param selected the index of its selected view
         */
        record Tabs(List<Class<? extends DockView>> types, int selected) implements Plan {

            /**
             * Copies the classes.
             *
             * @param types the classes of its views, in tab order
             * @param selected the index of its selected view
             */
            public Tabs {
                types = List.copyOf(types);
            }
        }
    }
}
