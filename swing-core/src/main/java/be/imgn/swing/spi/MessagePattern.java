package be.imgn.swing.spi;

import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The pattern of one message, with its placeholders rewritten from parameter names to indices, formatted with
 * {@link MessageFormat} for the display locale. The implementations of {@code @Messages} interfaces hold one per
 * method.
 */
public final class MessagePattern {

    private final String pattern;
    private final Locale locale;

    private MessagePattern(String pattern, Locale locale) {
        this.pattern = pattern;
        this.locale = locale;
    }

    /**
     * Reads the pattern of a message and rewrites its placeholders from names to indices, once.
     *
     * @param messages the merged table of the messages, in the display locale
     * @param key the key of the message, the method name
     * @param parameters the parameter names, by index
     * @param locale the display locale
     * @return the pattern
     * @throws IllegalStateException if the table has no string for the key
     */
    public static MessagePattern of(TomlTable messages, String key, List<String> parameters, Locale locale) {
        if (!(messages.entries().get(key) instanceof TomlString pattern)) {
            throw new IllegalStateException("No message pattern for the key " + key);
        }
        return new MessagePattern(rewrite(pattern.value(), parameters), locale);
    }

    /**
     * Rewrites the placeholders of a pattern from parameter names to their indices, following {@link MessageFormat}'s
     * quoting rules: a single {@code '} starts or ends a quoted section, and {@code ''} is a literal apostrophe.
     *
     * @param pattern the pattern, such as {@code The count is: {count}.}
     * @param parameters the parameter names, by index
     * @return the rewritten pattern, such as {@code The count is: {0}.}
     */
    public static String rewrite(String pattern, List<String> parameters) {
        Objects.requireNonNull(parameters, "parameters");
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

    /**
     * Formats the message.
     *
     * @param arguments the arguments, by parameter index
     * @return the formatted message
     */
    public String format(Object... arguments) {
        return new MessageFormat(pattern, locale).format(arguments);
    }
}
