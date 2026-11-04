package be.imgn.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The rules of a value, declared once and tested in a form, a command-line tool or a service.
 *
 * <p>Requirements are texts of the display locale, so a validator is built from typed messages, by a factory rather
 * than as a constant: {@code static Validator<String> password(AccountMessages messages)}.
 *
 * @param rules the rules, in order
 * @param <T> the type of the value
 */
public record Validator<T>(List<Rule<? super T>> rules) {

    /**
     * Creates a validator of a copy of the rules.
     *
     * @param rules the rules, in order
     * @throws NullPointerException if a rule is {@code null}
     */
    public Validator {
        rules = List.copyOf(rules);
    }

    /**
     * Returns a validator of rules.
     *
     * @param rules the rules, in order
     * @param <T> the type of the value
     * @return the validator
     * @throws NullPointerException if a rule is {@code null}
     */
    @SafeVarargs
    public static <T> Validator<T> of(Rule<? super T>... rules) {
        // Read element by element: passing the array on is an unsafe use of a generic varargs parameter for javac.
        var list = new ArrayList<Rule<? super T>>(rules.length);
        for (Rule<? super T> rule : rules) {
            list.add(rule);
        }
        return new Validator<>(list);
    }

    /**
     * Tests a value against every rule, in order, whether or not an earlier one fails.
     *
     * @param value the value
     * @return one check per rule, in order: {@link Check.Status#MET} or {@link Check.Status#UNMET}
     * @throws NullPointerException if the value is {@code null}
     */
    public Validation validate(T value) {
        Objects.requireNonNull(value, "value");
        return new Validation(rules.stream()
                .map(rule -> new Check(rule.requirement(), rule.test(value) ? Check.Status.MET : Check.Status.UNMET))
                .toList());
    }
}
