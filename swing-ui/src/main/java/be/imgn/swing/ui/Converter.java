package be.imgn.swing.ui;

import be.imgn.swing.ui.internal.FunctionConverter;
import be.imgn.swing.ui.internal.OptionalConverter;
import java.util.Optional;
import java.util.function.Function;

/**
 * Converts the text of a field to a value and back, for {@link Form#bind(javax.swing.text.JTextComponent,
 * be.imgn.swing.Property, Converter)}.
 *
 * <p>{@link #parse(String)} rejects a text by throwing {@link IllegalArgumentException} or
 * {@link java.time.DateTimeException}, which {@link NumberFormatException} and
 * {@link java.time.format.DateTimeParseException} extend: the binding then leaves its property unchanged, and the
 * converter's check, the first of the binding, is unmet. Any other exception is a bug in the converter.
 *
 * @param <T> the type of the value
 */
public interface Converter<T> {

    /**
     * Converts a text to a value.
     *
     * @param text the text
     * @return the value
     * @throws IllegalArgumentException if the text doesn't have the format the converter requires
     * @throws java.time.DateTimeException if the text doesn't have the format the converter requires
     */
    T parse(String text);

    /**
     * Converts a value to a text, which {@link #parse(String)} converts back to an equal value.
     *
     * @param value the value
     * @return the text
     */
    String format(T value);

    /**
     * Returns the requirement of the text, a message from typed messages that names the format the text must have, such
     * as "A whole number".
     *
     * @return the requirement
     */
    String requirement();

    /**
     * Returns a converter of two functions.
     *
     * @param parse converts a text to a value, throwing {@link IllegalArgumentException} or
     *     {@link java.time.DateTimeException} for a text it rejects
     * @param format converts a value to a text
     * @param requirement the requirement of the text
     * @param <T> the type of the value
     * @return the converter
     * @throws NullPointerException if an argument is {@code null}
     */
    static <T> Converter<T> of(
            Function<String, ? extends T> parse, Function<? super T, String> format, String requirement) {
        return new FunctionConverter<>(parse, format, requirement);
    }

    /**
     * Returns a converter of optional values: it converts an empty or blank text to {@link Optional#empty()}, and any
     * other text with {@code converter}; it formats {@link Optional#empty()} as an empty text. Its requirement is
     * {@code converter}'s.
     *
     * @param converter the converter of present values
     * @param <T> the type of a present value
     * @return the converter
     * @throws NullPointerException if {@code converter} is {@code null}
     */
    static <T> Converter<Optional<T>> optional(Converter<T> converter) {
        return new OptionalConverter<>(converter);
    }
}
