package be.imgn.swing.flatlaf;

import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.internal.DefaultApplicationInfo;
import be.imgn.swing.ui.LookAndFeelInstaller;
import be.imgn.toml.TomlString;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.Optional;
import java.util.function.Function;

/**
 * Installs FlatLaf, light or dark: the {@code be.imgn.swing.theme} system property, else the {@code theme} key of the
 * application's resources, selects {@code system} (the default), {@code light} or {@code dark}.
 */
public final class FlatLafInstaller implements LookAndFeelInstaller {

    private static final System.Logger LOGGER = System.getLogger(FlatLafInstaller.class.getName());

    private final ApplicationInfo application;
    private final Resources resources;
    private final DarkMode darkMode;
    private final Function<String, String> properties;

    /**
     * Creates the installer.
     *
     * @param application the application's information
     * @param resources the resources, for the application class's {@code theme} key
     * @param darkMode reads the operating system's setting
     * @param properties reads system properties
     */
    public FlatLafInstaller(
            ApplicationInfo application, Resources resources, DarkMode darkMode, Function<String, String> properties) {
        this.application = application;
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
        if (application instanceof DefaultApplicationInfo info
                && resources.table(info.descriptor().type()).entries().get("theme") instanceof TomlString theme) {
            return Theme.of(theme.value()).orElse(Theme.SYSTEM);
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
            case SYSTEM -> darkMode.isDark(application.platform());
        };
    }

    @Override
    public void install() {
        if (isDark()) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }
}
