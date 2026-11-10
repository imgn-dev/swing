package be.imgn.swing.spi;

import be.imgn.swing.Resources;
import be.imgn.toml.TomlTable;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * The patterns of the messages of a {@code @Messages} interface, read from its merged table, and read again, all at
 * once, at the first call after the table changed: after a change of the display locale, or a reload of its files. The
 * implementations of {@code @Messages} interfaces hold one, so that no call mixes the patterns of two locales.
 *
 * @param <P> the type that holds the patterns
 */
public final class MessageSource<P> {

    private final Resources resources;
    private final Class<?> type;
    private final @Nullable Locale fixed;
    private final BiFunction<TomlTable, Locale, P> read;
    private volatile @Nullable Read<P> last;

    private MessageSource(
            Resources resources, Class<?> type, @Nullable Locale fixed, BiFunction<TomlTable, Locale, P> read) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.type = Objects.requireNonNull(type, "type");
        this.fixed = fixed;
        this.read = Objects.requireNonNull(read, "read");
    }

    /**
     * Returns a source that follows the display locale.
     *
     * @param resources the resources
     * @param type the {@code @Messages} interface
     * @param read reads the patterns from the merged table, in a locale
     * @param <P> the type that holds the patterns
     * @return the source
     * @throws NullPointerException if an argument is {@code null}
     */
    public static <P> MessageSource<P> following(
            Resources resources, Class<?> type, BiFunction<TomlTable, Locale, P> read) {
        return new MessageSource<>(resources, type, null, read);
    }

    /**
     * Returns a source that reads the files of a fixed locale.
     *
     * @param resources the resources
     * @param type the {@code @Messages} interface
     * @param locale the locale
     * @param read reads the patterns from the merged table, in a locale
     * @param <P> the type that holds the patterns
     * @return the source
     * @throws NullPointerException if an argument is {@code null}
     */
    public static <P> MessageSource<P> fixed(
            Resources resources, Class<?> type, Locale locale, BiFunction<TomlTable, Locale, P> read) {
        return new MessageSource<>(resources, type, Objects.requireNonNull(locale, "locale"), read);
    }

    /**
     * Returns the patterns of the current table, read again if the table changed since the last call.
     *
     * @return the patterns
     * @throws IllegalStateException if the table lacks a pattern
     */
    public P get() {
        Locale locale;
        TomlTable table;
        if (fixed != null) {
            locale = fixed;
            table = resources.table(type, fixed);
        } else {
            // Read again while the locale changes in between, so that the table is the locale's.
            do {
                locale = resources.locale();
                table = resources.table(type);
            } while (!locale.equals(resources.locale()));
        }
        Read<P> current = last;
        if (current != null && current.locale.equals(locale) && current.table.equals(table)) {
            return current.patterns;
        }
        P patterns = read.apply(table, locale);
        last = new Read<>(table, locale, patterns);
        return patterns;
    }

    private record Read<P>(TomlTable table, Locale locale, P patterns) {}
}
