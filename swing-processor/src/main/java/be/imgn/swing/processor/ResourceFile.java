package be.imgn.swing.processor;

import be.imgn.toml.TomlDocument;
import be.imgn.toml.TomlParseException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A TOML file of a class's resources, parsed on its own.
 *
 * @param name the file name, such as {@code CounterApp_fr.toml}
 * @param language the language of a locale file, empty for the base file and its platform variants
 * @param country the country of a locale file, empty when none
 * @param platform the platform of a platform variant, empty when none
 * @param document the parsed document, if it parses
 * @param error the syntax or semantic error, if it does not
 */
record ResourceFile(
        String name,
        String language,
        String country,
        String platform,
        @Nullable TomlDocument document,
        @Nullable TomlParseException error) {

    /**
     * Returns whether this is the base file: no locale, no platform.
     *
     * @return {@code true} for the base file
     */
    boolean isBase() {
        return language.isEmpty() && platform.isEmpty();
    }

    /**
     * Returns whether this is a locale file of a language alone, which must have the keys of the base file.
     *
     * @return {@code true} for a file such as {@code CounterApp_fr.toml}
     */
    boolean isLanguage() {
        return !language.isEmpty() && country.isEmpty() && platform.isEmpty();
    }

    /**
     * Returns whether this is a locale file, of a language or of a country, without platform.
     *
     * @return {@code true} for a file such as {@code CounterApp_fr_BE.toml}
     */
    boolean isLocale() {
        return !language.isEmpty() && platform.isEmpty();
    }

    Optional<TomlDocument> parsed() {
        return Optional.ofNullable(document);
    }
}
