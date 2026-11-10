package be.imgn.swing.internal;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Scope;
import be.imgn.swing.Task;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlMappingException;
import be.imgn.toml.TomlParseException;
import be.imgn.toml.UnknownKeys;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The display locale the user chose, saved to {@code locale.toml} in the config directory, in tasks, which the test
 * harness waits for as for a command's.
 */
@Singleton
public final class LocaleFile {

    private static final String FILE_NAME = "locale.toml";
    private static final System.Logger LOGGER = System.getLogger(LocaleFile.class.getName());

    private final Path file;
    private final Consumer<Write> writes;
    private final Object writing = new Object();
    // The version of the choice, increased at each save, with its locale, null for none; and the last version written;
    // all under writing.
    private long version;
    private @Nullable Locale chosen;
    private long written;

    @Inject
    LocaleFile(ApplicationDirectories directories, TaskRunner tasks, Scope application) {
        this(
                directories.config().resolve(FILE_NAME),
                write -> tasks.start(
                        Task.of(() -> {
                            write.run();
                            return write;
                        }),
                        application,
                        "locale",
                        true));
    }

    LocaleFile(Path file, Consumer<Write> writes) {
        this.file = file;
        this.writes = writes;
    }

    /**
     * Saves a choice, on the UI thread. The writes are ordered: one that would replace a newer choice writes nothing.
     *
     * @param choice the locale, or {@code null} to delete the saved choice
     */
    void save(@Nullable Locale choice) {
        long saved;
        synchronized (writing) {
            saved = ++version;
            chosen = choice;
        }
        writes.accept(() -> write(saved, choice));
    }

    private void write(long saved, @Nullable Locale choice) throws IOException {
        synchronized (writing) {
            if (saved <= written) {
                return;
            }
            if (choice == null) {
                Files.deleteIfExists(file);
            } else {
                Path directory = file.getParent();
                if (directory != null) {
                    Files.createDirectories(directory);
                }
                Toml.writeAtomically(Toml.toTable(new SavedLocale(choice.toLanguageTag())), file);
            }
            written = saved;
        }
    }

    /** Writes the last choice, if a task didn't, since the shutdown cancels the tasks still running. */
    void flush() {
        long last;
        @Nullable Locale choice;
        synchronized (writing) {
            last = version;
            choice = chosen;
        }
        if (last == 0) {
            return;
        }
        try {
            write(last, choice);
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Could not save the locale to " + file, e);
        }
    }

    /**
     * Reads the saved choice.
     *
     * @return the locale, or empty if none is saved, or the file is invalid, which is logged as a warning
     */
    Optional<Locale> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            SavedLocale saved = Toml.bind(Toml.parse(file).root(), SavedLocale.class, UnknownKeys.IGNORE);
            Locale locale = Locale.forLanguageTag(saved.locale());
            if (locale.getLanguage().isEmpty()) {
                LOGGER.log(
                        Level.WARNING, "Ignored the saved locale " + file + ": " + saved.locale() + " has no language");
                return Optional.empty();
            }
            return Optional.of(locale);
        } catch (IOException | TomlParseException | TomlMappingException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Ignored the saved locale " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @FunctionalInterface
    interface Write {
        void run() throws IOException;
    }
}
