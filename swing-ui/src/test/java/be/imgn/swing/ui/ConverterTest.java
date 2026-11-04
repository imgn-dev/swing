package be.imgn.swing.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Converters of two functions, and of optional values. */
class ConverterTest {

    private static final Converter<Integer> NUMBER = Converter.of(Integer::valueOf, String::valueOf, "A whole number");
    private static final Converter<LocalDate> DATE =
            Converter.of(LocalDate::parse, LocalDate::toString, "A date such as 2026-10-31");

    @Test
    void parsesAndFormatsWithItsFunctions() {
        assertThat(NUMBER.parse("42")).isEqualTo(42);
        assertThat(NUMBER.format(7)).isEqualTo("7");
        assertThat(NUMBER.requirement()).isEqualTo("A whole number");
    }

    @Test
    void rejectsATextWithTheExceptionOfItsFunction() {
        assertThatThrownBy(() -> NUMBER.parse("forty")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> DATE.parse("31/10/2026")).isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void optionalConvertsAnEmptyOrBlankTextToEmpty() {
        Converter<Optional<LocalDate>> optional = Converter.optional(DATE);

        assertThat(optional.parse("")).isEmpty();
        assertThat(optional.parse("   ")).isEmpty();
    }

    @Test
    void optionalConvertsAnyOtherTextWithItsConverter() {
        Converter<Optional<LocalDate>> optional = Converter.optional(DATE);

        assertThat(optional.parse("2026-10-31")).contains(LocalDate.of(2026, 10, 31));
        assertThatThrownBy(() -> optional.parse("soon")).isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void optionalFormatsEmptyAsAnEmptyText() {
        Converter<Optional<LocalDate>> optional = Converter.optional(DATE);

        assertThat(optional.format(Optional.empty())).isEmpty();
        assertThat(optional.format(Optional.of(LocalDate.of(2026, 1, 2)))).isEqualTo("2026-01-02");
    }

    @Test
    void optionalHasTheRequirementOfItsConverter() {
        assertThat(Converter.optional(DATE).requirement()).isEqualTo("A date such as 2026-10-31");
    }
}
