package be.imgn.swing;

import java.util.Objects;

/**
 * The status of one requirement of a value.
 *
 * @param requirement the requirement, as its rule names it
 * @param status whether the value meets it
 */
public record Check(String requirement, Status status) {

    /**
     * Creates a check.
     *
     * @param requirement the requirement
     * @param status whether the value meets it
     */
    public Check {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(status, "status");
    }

    /** Whether a value meets a requirement. */
    public enum Status {
        /** The value meets the requirement. */
        MET,
        /** The value doesn't meet the requirement. */
        UNMET,
        /** The requirement couldn't be tested. */
        UNKNOWN
    }
}
