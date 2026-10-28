package be.imgn.swing.flatlaf;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The theme of FlatLaf, which the {@code be.imgn.swing.theme} system property selects, else the {@code theme} key of
 * the application's resources.
 */
public enum Theme {
    /** Follows the operating system's setting, read once at startup. */
    SYSTEM,
    /** Light. */
    LIGHT,
    /** Dark. */
    DARK;

    /** The system property that selects the theme, and wins over the application's resources. */
    public static final String PROPERTY = "be.imgn.swing.theme";

    /**
     * Returns the theme of a name.
     *
     * @param name {@code system}, {@code light} or {@code dark}
     * @return the theme, or empty for another name
     */
    public static Optional<Theme> of(String name) {
        return Arrays.stream(values())
                .filter(theme -> theme.name()
                        .toLowerCase(Locale.ROOT)
                        .equals(name.strip().toLowerCase(Locale.ROOT)))
                .findFirst();
    }
}
