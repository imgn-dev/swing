package be.imgn.swing.internal;

import be.imgn.swing.Rule;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The rule of {@link Rule#of(String, Predicate)}.
 *
 * @param requirement the requirement
 * @param predicate whether a value meets the requirement
 * @param <T> the type of the value
 */
public record PredicateRule<T>(String requirement, Predicate<? super T> predicate) implements Rule<T> {

    /**
     * Creates the rule.
     *
     * @param requirement the requirement
     * @param predicate whether a value meets the requirement
     */
    public PredicateRule {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(predicate, "test");
    }

    @Override
    public boolean test(T value) {
        return predicate.test(value);
    }
}
