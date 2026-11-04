package be.imgn.swing;

import java.util.List;
import java.util.Objects;

/**
 * A change of an {@link ObservableList}, with indices valid for the list just before it.
 *
 * <p>The lists of elements of the changes an observable list gives are unmodifiable.
 *
 * @param <E> the type of the elements
 */
public sealed interface ListChange<E> {

    /**
     * Elements added at an index.
     *
     * @param index the index of the first element added
     * @param elements the elements added, in order
     * @param <E> the type of the elements
     */
    record Added<E>(int index, List<E> elements) implements ListChange<E> {

        /**
         * Creates the change.
         *
         * @param index the index of the first element added
         * @param elements the elements added, in order
         * @throws NullPointerException if the elements are {@code null}
         */
        public Added {
            Objects.requireNonNull(elements, "elements");
        }
    }

    /**
     * Elements removed from an index.
     *
     * @param index the index of the first element removed
     * @param elements the elements removed, in order
     * @param <E> the type of the elements
     */
    record Removed<E>(int index, List<E> elements) implements ListChange<E> {

        /**
         * Creates the change.
         *
         * @param index the index of the first element removed
         * @param elements the elements removed, in order
         * @throws NullPointerException if the elements are {@code null}
         */
        public Removed {
            Objects.requireNonNull(elements, "elements");
        }
    }

    /**
     * Elements replaced from an index by others, not necessarily as many.
     *
     * @param index the index of the first element replaced
     * @param removed the elements replaced, in order
     * @param added the elements that replace them, in order
     * @param <E> the type of the elements
     */
    record Replaced<E>(int index, List<E> removed, List<E> added) implements ListChange<E> {

        /**
         * Creates the change.
         *
         * @param index the index of the first element replaced
         * @param removed the elements replaced, in order
         * @param added the elements that replace them, in order
         * @throws NullPointerException if the elements replaced or those that replace them are {@code null}
         */
        public Replaced {
            Objects.requireNonNull(removed, "removed");
            Objects.requireNonNull(added, "added");
        }
    }
}
