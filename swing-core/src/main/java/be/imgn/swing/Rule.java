package be.imgn.swing;

import be.imgn.swing.internal.PredicateRule;
import java.util.function.Predicate;

/**
 * A requirement of a value, and the test of whether a value meets it.
 *
 * <p>The requirement names what the value must be, not what is wrong with it, so that the same message reads right
 * whether the value meets it or not. It is a text of the display locale, from typed messages.
 *
 * @param <T> the type of the value
 */
public interface Rule<T> {

    /**
     * Returns the requirement, a message from typed messages, such as "At least 8 characters".
     *
     * @return the requirement
     */
    String requirement();

    /**
     * Returns whether a value meets the requirement.
     *
     * @param value the value
     * @return whether the value meets the requirement
     */
    boolean test(T value);

    /**
     * Returns a rule of a requirement and its test.
     *
     * @param requirement the requirement
     * @param test whether a value meets the requirement
     * @param <T> the type of the value
     * @return the rule
     * @throws NullPointerException if the requirement or the test is {@code null}
     */
    static <T> Rule<T> of(String requirement, Predicate<? super T> test) {
        return new PredicateRule<>(requirement, test);
    }

    /**
     * Returns a rule met by a text that isn't blank.
     *
     * @param requirement the requirement
     * @return the rule
     * @throws NullPointerException if the requirement is {@code null}
     */
    static Rule<String> required(String requirement) {
        return of(requirement, text -> !text.isBlank());
    }
}
