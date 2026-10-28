package be.imgn.swing;

import java.util.List;

/**
 * The arguments given to the launch of the application, bound in the root injector.
 *
 * @param values the arguments, in order
 */
public record Arguments(List<String> values) {

    /**
     * Creates the arguments.
     *
     * @param values the arguments, in order; copied
     */
    public Arguments {
        values = List.copyOf(values);
    }
}
