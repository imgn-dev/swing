package be.imgn.swing.ui.internal;

import be.imgn.swing.ui.Converter;
import java.util.Objects;
import java.util.function.Function;

/**
 * The converter of {@link Converter#of(Function, Function, String)}.
 *
 * @param parser converts a text to a value
 * @param formatter converts a value to a text
 * @param requirement the requirement of the text
 * @param <T> the type of the value
 */
public record FunctionConverter<T>(
        Function<String, ? extends T> parser, Function<? super T, String> formatter, String requirement)
        implements Converter<T> {

    /**
     * Creates the converter.
     *
     * @param parser converts a text to a value
     * @param formatter converts a value to a text
     * @param requirement the requirement of the text
     */
    public FunctionConverter {
        Objects.requireNonNull(parser, "parse");
        Objects.requireNonNull(formatter, "format");
        Objects.requireNonNull(requirement, "requirement");
    }

    @Override
    public T parse(String text) {
        return Objects.requireNonNull(parser.apply(text), "The converter's parse function returned null");
    }

    @Override
    public String format(T value) {
        return Objects.requireNonNull(formatter.apply(value), "The converter's format function returned null");
    }
}
