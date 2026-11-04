package be.imgn.swing;

import java.util.List;
import java.util.Optional;

/**
 * The status of every requirement of a value, met or not, so that a view can show each one and not only the first
 * error.
 *
 * @param checks the checks, one per requirement, in order
 */
public record Validation(List<Check> checks) {

    /**
     * Creates a validation of a copy of the checks.
     *
     * @param checks the checks, one per requirement, in order
     * @throws NullPointerException if a check is {@code null}
     */
    public Validation {
        checks = List.copyOf(checks);
    }

    /**
     * Returns whether every check is {@link Check.Status#MET}.
     *
     * @return whether the value is valid
     */
    public boolean valid() {
        return checks.stream().allMatch(check -> check.status() == Check.Status.MET);
    }

    /**
     * Returns the requirement of the first check that isn't {@link Check.Status#MET}.
     *
     * @return the requirement of the first check that isn't met, if any
     */
    public Optional<String> error() {
        return checks.stream()
                .filter(check -> check.status() != Check.Status.MET)
                .map(Check::requirement)
                .findFirst();
    }
}
