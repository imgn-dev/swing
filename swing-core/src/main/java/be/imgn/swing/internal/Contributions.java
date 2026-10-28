package be.imgn.swing.internal;

import be.imgn.inject.Injector;
import be.imgn.inject.Key;
import be.imgn.inject.Types;
import be.imgn.inject.UnsatisfiedDependencyException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Reads the contributions to a set: inject leaves the key of a set with no contribution unbound. */
public final class Contributions {

    private Contributions() {}

    /**
     * Returns the contributions to {@code Set<type>}.
     *
     * @param injector the injector to resolve the set through
     * @param type the element type
     * @param <T> the element type
     * @return the contributions, empty if there is none
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<T> of(Injector injector, Class<T> type) {
        try {
            return (Set<T>) injector.getInstance(Key.of(Types.setOf(type)));
        } catch (UnsatisfiedDependencyException e) {
            return Set.of();
        }
    }

    /**
     * Returns the contributions to {@code Set<type>}, sorted by order then by class name.
     *
     * @param injector the injector to resolve the set through
     * @param type the element type
     * @param order the order of a contribution
     * @param <T> the element type
     * @return the sorted contributions
     */
    public static <T> List<T> sorted(Injector injector, Class<T> type, ToIntFunction<? super T> order) {
        return of(injector, type).stream()
                .sorted(Comparator.<T>comparingInt(order)
                        .thenComparing(contribution -> contribution.getClass().getName()))
                .toList();
    }
}
