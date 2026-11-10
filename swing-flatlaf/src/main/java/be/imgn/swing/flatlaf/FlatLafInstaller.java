package be.imgn.swing.flatlaf;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.swing.Appearance;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.ui.LookAndFeelInstaller;
import be.imgn.toml.TomlBoolean;
import be.imgn.toml.TomlInteger;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Installs FlatLaf, light or dark: the {@code be.imgn.swing.theme} system property, else the {@code theme} key of the
 * application's resources, selects {@code system} (the default), {@code light} or {@code dark}.
 */
public final class FlatLafInstaller implements LookAndFeelInstaller {

    private static final System.Logger LOGGER = System.getLogger(FlatLafInstaller.class.getName());

    private final ApplicationInfo application;
    private final Platform platform;
    private final Resources resources;
    private final DarkMode darkMode;
    private final Function<String, String> properties;

    /**
     * Creates the installer.
     *
     * @param application the application's information
     * @param platform the platform, whose dark mode {@link Theme#SYSTEM} follows
     * @param resources the resources, for the application class's {@code theme} key
     * @param darkMode reads the operating system's setting
     * @param properties reads system properties
     */
    public FlatLafInstaller(
            ApplicationInfo application,
            Platform platform,
            Resources resources,
            DarkMode darkMode,
            Function<String, String> properties) {
        this.application = application;
        this.platform = platform;
        this.resources = resources;
        this.darkMode = darkMode;
        this.properties = properties;
    }

    /**
     * Returns the theme to install.
     *
     * @return the theme of the system property, else of the resources, else {@link Theme#SYSTEM}
     */
    public Theme theme() {
        String property = properties.apply(Theme.PROPERTY);
        if (property != null) {
            Optional<Theme> theme = Theme.of(property);
            if (theme.isPresent()) {
                return theme.get();
            }
            LOGGER.log(System.Logger.Level.WARNING, "Unknown theme " + property + " in -D" + Theme.PROPERTY);
        }
        if (resources.table(application.type()).entries().get("theme") instanceof TomlString resource) {
            Optional<Theme> theme = Theme.of(resource.value());
            if (theme.isPresent()) {
                return theme.get();
            }
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Unknown theme " + resource.value() + " in the resources of "
                            + application.type().getName());
        }
        return Theme.SYSTEM;
    }

    /**
     * Returns whether the dark theme is installed.
     *
     * @return {@code true} for the dark theme
     */
    public boolean isDark() {
        return switch (theme()) {
            case LIGHT -> false;
            case DARK -> true;
            case SYSTEM -> darkMode.isDark(platform);
        };
    }

    @Override
    public void install() {
        install(isDark() ? Appearance.DARK : Appearance.LIGHT);
    }

    /**
     * Installs FlatLaf in an appearance, with the tokens of the application class's {@code [flatlaf]} table, read in
     * that appearance: they are given to {@link FlatLaf#setGlobalExtraDefaults(Map)} before the look and feel is set
     * up.
     *
     * @param appearance the appearance
     * @throws NullPointerException if {@code appearance} is {@code null}
     */
    public void install(Appearance appearance) {
        Objects.requireNonNull(appearance, "appearance");
        FlatLaf.setGlobalExtraDefaults(tokens());
        if (appearance == Appearance.DARK) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }

    /**
     * Returns the tokens of the application class's {@code [flatlaf]} table, in the current appearance of the
     * resources: each value as the string that FlatLaf's properties files would hold.
     *
     * @return the tokens, by FlatLaf key or variable
     */
    public Map<String, String> tokens() {
        return tokens(resources.table(application.type()), application.type());
    }

    // The [flatlaf] table of the merged resources of the application class, as strings.
    static Map<String, String> tokens(TomlTable resources, Class<?> owner) {
        Map<String, String> tokens = new LinkedHashMap<>();
        if (resources.entries().get("flatlaf") instanceof TomlTable table) {
            table.entries().forEach((key, value) -> {
                switch (value) {
                    case TomlString string -> tokens.put(key, string.value());
                    case TomlInteger integer -> tokens.put(key, Long.toString(integer.value()));
                    case TomlBoolean bool -> tokens.put(key, Boolean.toString(bool.value()));
                    default ->
                        LOGGER.log(
                                System.Logger.Level.WARNING,
                                "Ignored the FlatLaf token " + key + " of " + owner.getName()
                                        + ": not a string, an integer or a boolean");
                }
            });
        }
        return tokens;
    }
}
