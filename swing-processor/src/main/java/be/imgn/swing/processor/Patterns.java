package be.imgn.swing.processor;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Checks message patterns: their placeholders name parameters, and they parse as {@link MessageFormat}s. */
final class Patterns {

    private Patterns() {}

    /**
     * Returns the problem of a pattern, if any.
     *
     * @param pattern the pattern, with placeholders that name parameters
     * @param parameters the parameter names
     * @return the problem, or empty if the pattern is valid
     */
    static Optional<String> problem(String pattern, List<String> parameters) {
        for (String placeholder : placeholders(pattern)) {
            if (!parameters.contains(placeholder)) {
                return Optional.of("The placeholder {" + placeholder + "} names no parameter: "
                        + (parameters.isEmpty() ? "the method has none" : "the parameters are " + parameters));
            }
        }
        try {
            new MessageFormat(rewrite(pattern, parameters));
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of("The pattern does not parse as a MessageFormat: " + e.getMessage());
        }
    }

    /** Returns the names of the placeholders of a pattern, outside quoted sections, in order. */
    static List<String> placeholders(String pattern) {
        List<String> names = new ArrayList<>();
        boolean quoted = false;
        int index = 0;
        while (index < pattern.length()) {
            char c = pattern.charAt(index);
            if (c == '\'') {
                if (index + 1 < pattern.length() && pattern.charAt(index + 1) == '\'') {
                    index += 2;
                    continue;
                }
                quoted = !quoted;
            } else if (c == '{' && !quoted) {
                int end = index + 1;
                while (end < pattern.length() && Character.isJavaIdentifierPart(pattern.charAt(end))) {
                    end++;
                }
                names.add(pattern.substring(index + 1, end));
            }
            index++;
        }
        return names;
    }

    /** Rewrites the placeholders of a pattern from names to indices, as the generated code does at run time. */
    static String rewrite(String pattern, List<String> parameters) {
        StringBuilder rewritten = new StringBuilder(pattern.length());
        boolean quoted = false;
        int index = 0;
        while (index < pattern.length()) {
            char c = pattern.charAt(index);
            if (c == '\'') {
                if (index + 1 < pattern.length() && pattern.charAt(index + 1) == '\'') {
                    rewritten.append("''");
                    index += 2;
                    continue;
                }
                quoted = !quoted;
            } else if (c == '{' && !quoted) {
                int end = index + 1;
                while (end < pattern.length() && Character.isJavaIdentifierPart(pattern.charAt(end))) {
                    end++;
                }
                int parameter = parameters.indexOf(pattern.substring(index + 1, end));
                if (parameter >= 0) {
                    rewritten.append('{').append(parameter);
                    index = end;
                    continue;
                }
            }
            rewritten.append(c);
            index++;
        }
        return rewritten.toString();
    }
}
