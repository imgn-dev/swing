package be.imgn.swing.flatlaf;

import be.imgn.gaia.core.platform.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Reads whether the operating system uses a dark theme, once: on macOS, {@code defaults read -g AppleInterfaceStyle};
 * on Windows, the {@code AppsUseLightTheme} registry value through {@code reg query}; on Linux, {@code gsettings get
 * org.gnome.desktop.interface color-scheme}. Any failure means light.
 */
public final class DarkMode {

    private static final long TIMEOUT_SECONDS = 5;

    private final Function<List<String>, Optional<String>> commands;

    /**
     * Creates the detection, running commands with a function, for tests.
     *
     * @param commands runs a command and returns its standard output, or empty if it fails
     */
    public DarkMode(Function<List<String>, Optional<String>> commands) {
        this.commands = commands;
    }

    /**
     * Returns the detection that runs the commands in other processes.
     *
     * @return the detection
     */
    public static DarkMode system() {
        return new DarkMode(DarkMode::run);
    }

    /**
     * Returns whether the operating system uses a dark theme.
     *
     * @param platform the platform
     * @return {@code true} for a dark theme; {@code false} for a light one, an unknown platform, or any failure
     */
    public boolean isDark(Platform platform) {
        if (platform.isMacOS()) {
            return commands.apply(List.of("defaults", "read", "-g", "AppleInterfaceStyle"))
                    .map(output -> output.strip().equalsIgnoreCase("Dark"))
                    .orElse(false);
        }
        if (platform.isWindows()) {
            return commands.apply(List.of(
                            "reg",
                            "query",
                            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                            "/v",
                            "AppsUseLightTheme"))
                    .map(output -> output.contains("AppsUseLightTheme")
                            && output.strip().endsWith("0x0"))
                    .orElse(false);
        }
        if (platform.isLinux()) {
            return commands.apply(List.of("gsettings", "get", "org.gnome.desktop.interface", "color-scheme"))
                    .map(output -> output.toLowerCase(Locale.ROOT).contains("dark"))
                    .orElse(false);
        }
        return false;
    }

    private static Optional<String> run(List<String> command) {
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            try (InputStream in = process.getInputStream()) {
                String output = new String(in.readAllBytes(), Charset.forName(System.getProperty("native.encoding")));
                return process.exitValue() == 0 ? Optional.of(output) : Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
