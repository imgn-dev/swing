package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Run on the class path, where the service loader finds the binder of SavedLocale in META-INF/services.
class LocaleFileTest {

    @TempDir
    Path config;

    private final List<LocaleFile.Write> writes = new ArrayList<>();

    private LocaleFile file() {
        return new LocaleFile(config.resolve("locale.toml"), writes::add);
    }

    private void runWrites() throws IOException {
        for (LocaleFile.Write write : writes) {
            write.run();
        }
        writes.clear();
    }

    @Test
    void savesTheLanguageTagOfTheChoice() throws IOException {
        LocaleFile file = file();

        file.save(Locale.of("fr", "BE"));
        runWrites();

        assertThat(config.resolve("locale.toml")).content().contains("locale = \"fr-BE\"");
        assertThat(file().load()).contains(Locale.of("fr", "BE"));
    }

    @Test
    void deletesTheSavedChoiceForNone() throws IOException {
        LocaleFile file = file();
        file.save(Locale.FRENCH);
        runWrites();

        file.save(null);
        runWrites();

        assertThat(config.resolve("locale.toml")).doesNotExist();
        assertThat(file().load()).isEmpty();
    }

    @Test
    void neverReplacesANewerChoice() throws IOException {
        LocaleFile file = file();
        file.save(Locale.FRENCH);
        file.save(Locale.GERMAN);
        LocaleFile.Write older = writes.getFirst();
        writes.getLast().run();

        older.run();

        assertThat(file().load()).contains(Locale.GERMAN);
    }

    @Test
    void flushesTheLastChoice() {
        LocaleFile file = file();
        file.save(Locale.ITALIAN);

        file.flush();

        assertThat(file().load()).contains(Locale.ITALIAN);
    }

    @Test
    void ignoresAFileThatDoesNotBindOrATagWithNoLanguage() throws IOException {
        Files.writeString(config.resolve("locale.toml"), "locale = 3\n");
        assertThat(file().load()).isEmpty();

        Files.writeString(config.resolve("locale.toml"), "locale = \"\"\n");
        assertThat(file().load()).isEmpty();

        Files.writeString(config.resolve("locale.toml"), "locale = \"en\"\nunknown = true\n");
        assertThat(file().load()).contains(Locale.ENGLISH);
    }
}
