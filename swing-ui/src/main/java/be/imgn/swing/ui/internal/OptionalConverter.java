package be.imgn.swing.ui.internal;

import be.imgn.swing.ui.Converter;
import java.util.Objects;
import java.util.Optional;

/**
 * The converter of {@link Converter#optional(Converter)}.
 *
 * @param converter the converter of present values
 * @param <T> the type of a present value
 */
public record OptionalConverter<T>(Converter<T> converter) implements Converter<Optional<T>> {

    /**
     * Creates the converter.
     *
     * @param converter the converter of present values
     */
    public OptionalConverter {
        Objects.requireNonNull(converter, "converter");
    }

    @Override
    public Optional<T> parse(String text) {
        return text.isBlank() ? Optional.empty() : Optional.of(converter.parse(text));
    }

    @Override
    public String format(Optional<T> value) {
        return value.isEmpty() ? "" : converter.format(value.get());
    }

    @Override
    public String requirement() {
        return converter.requirement();
    }
}
