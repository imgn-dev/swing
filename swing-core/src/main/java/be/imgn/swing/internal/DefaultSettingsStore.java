package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.spi.SettingsDescriptor;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlMappingException;
import be.imgn.toml.TomlParseException;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import be.imgn.toml.UnknownKeys;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * The settings store: loads every settings record from the config directory, and saves a record each time its property
 * changes, on a virtual thread named {@code settings}, one save at a time per record.
 */
@Singleton
public final class DefaultSettingsStore implements SettingsStore {

    /** The name of the threads that save. */
    public static final String THREAD_NAME = "settings";

    private static final System.Logger LOGGER = System.getLogger(DefaultSettingsStore.class.getName());

    private final FailureHandler failures;
    private final Scope scope;
    private final System.Logger logger;
    private final Object lock = new Object();
    private volatile @Nullable Map<Class<?>, Slot<?>> slots;
    // Guarded by lock.
    private int pending;
    private boolean closed;

    /**
     * Creates the store.
     *
     * @param failures where failed saves go
     * @param scope the application scope, which the subscriptions to the properties belong to
     */
    @Inject
    public DefaultSettingsStore(FailureHandler failures, Scope scope) {
        this(failures, scope, LOGGER);
    }

    /**
     * Creates the store with the logger of its warnings, which tests read through their own.
     *
     * @param failures where failed saves go
     * @param scope the application scope, which the subscriptions to the properties belong to
     * @param logger the logger of the warnings
     */
    public DefaultSettingsStore(FailureHandler failures, Scope scope, System.Logger logger) {
        this.failures = failures;
        this.scope = scope;
        this.logger = logger;
    }

    /**
     * Returns the descriptors that the {@link ServiceLoader} finds.
     *
     * @return the descriptors
     */
    public static List<SettingsDescriptor<?>> descriptors() {
        List<SettingsDescriptor<?>> descriptors = new ArrayList<>();
        for (SettingsDescriptor<?> descriptor : ServiceLoader.load(SettingsDescriptor.class)) {
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    /**
     * Returns the file of a settings record.
     *
     * @param config the config directory
     * @param id the record's id
     * @return the file
     */
    public static Path file(Path config, String id) {
        return config.resolve(id + ".toml");
    }

    /**
     * Writes a settings record to its file: every component, defaults included, with toml's writer, atomically,
     * creating the config directory when it is missing.
     *
     * @param config the config directory
     * @param id the record's id
     * @param value the record
     * @throws IOException if the file can't be written
     */
    public static void write(Path config, String id, Record value) throws IOException {
        TomlTable table = Toml.toTable(value);
        Files.createDirectories(config);
        Toml.writeAtomically(table, file(config, id));
    }

    /**
     * Returns the table a settings file is bound from: the file's table merged over the defaults' table, at the level
     * of the record's components only, without the {@code @Nullable} components of the defaults.
     *
     * @param defaults the table of the defaults
     * @param nullable the keys of the {@code @Nullable} components
     * @param file the table of the file
     * @return the merged table
     */
    static TomlTable merge(TomlTable defaults, Set<String> nullable, TomlTable file) {
        Map<String, TomlValue> entries = new LinkedHashMap<>(defaults.entries());
        entries.keySet().removeAll(nullable);
        entries.putAll(file.entries());
        return new TomlTable(entries);
    }

    /**
     * Loads every settings record from its file in the config directory.
     *
     * @param config the config directory
     * @param descriptors the descriptors of the records
     * @throws IllegalStateException if two records have the same id
     * @throws IllegalArgumentException if a record has no toml binder, which also writes it
     */
    public void load(Path config, Collection<? extends SettingsDescriptor<?>> descriptors) {
        Map<String, SettingsDescriptor<?>> byId = new LinkedHashMap<>();
        for (SettingsDescriptor<?> descriptor : descriptors) {
            SettingsDescriptor<?> previous = byId.putIfAbsent(descriptor.id(), descriptor);
            if (previous != null) {
                throw new IllegalStateException(
                        "The settings records " + previous.type().getName() + " and "
                                + descriptor.type().getName() + " have the same id " + descriptor.id()
                                + ": give each its own id in @Settings");
            }
        }
        Map<Class<?>, Slot<?>> loaded = new LinkedHashMap<>();
        for (SettingsDescriptor<?> descriptor : byId.values()) {
            loaded.put(descriptor.type(), slot(config, descriptor));
        }
        slots = Map.copyOf(loaded);
    }

    private <T> Slot<T> slot(Path config, SettingsDescriptor<T> descriptor) {
        Path file = file(config, descriptor.id());
        T defaults = descriptor.defaults();
        // Outside the try, whatever the file: a record without binder or writer is a packaging error, which fails
        // startup, rather than an invalid file, which would be moved aside.
        TomlTable defaultsTable = Toml.toTable((Record) defaults);
        T value = defaults;
        boolean saved = true;
        try {
            TomlTable table = Toml.parse(file).root();
            value = Toml.bind(
                    merge(defaultsTable, descriptor.nullable(), table), descriptor.type(), UnknownKeys.IGNORE);
        } catch (NoSuchFileException e) {
            // A missing file gives the defaults, and no file is written.
        } catch (TomlParseException | TomlMappingException e) {
            Path invalid = file.resolveSibling(file.getFileName() + ".invalid");
            try {
                Files.move(file, invalid, StandardCopyOption.REPLACE_EXISTING);
                logger.log(
                        Level.WARNING,
                        "The settings file " + file + " is invalid, so the defaults are used, and the file is moved to "
                                + invalid + ": " + e.getMessage());
            } catch (IOException moving) {
                saved = false;
                logger.log(
                        Level.WARNING,
                        "The settings file " + file + " is invalid, so the defaults are used, and it isn't saved"
                                + " during this run, since it can't be moved to " + invalid + ": " + e.getMessage()
                                + "; " + moving);
            }
        } catch (IOException e) {
            saved = false;
            logger.log(
                    Level.WARNING,
                    "The settings file " + file + " can't be read, so the defaults are used, and it isn't saved during"
                            + " this run: " + e);
        }
        Slot<T> slot = new Slot<>(descriptor, config, Property.of(value));
        if (saved) {
            slot.property.subscribe(scope, changed -> slot.changed());
        }
        return slot;
    }

    @Override
    public <T extends Record> Property<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Map<Class<?>, Slot<?>> loaded = slots;
        if (loaded == null) {
            throw new IllegalStateException("The settings are not loaded yet: they are loaded by a startup step of"
                    + " order 150, so a startup step that runs before it can't read them");
        }
        Slot<?> slot = loaded.get(type);
        if (slot == null) {
            throw new IllegalArgumentException("No settings descriptor for " + type.getName()
                    + ": annotate it with @Settings, compile it with swing's processor, and on the module path,"
                    + " register the services with gaia's register-services goal");
        }
        return property(slot);
    }

    // The slot of a type holds a property of that type: load keys each slot by its descriptor's type.
    @SuppressWarnings("unchecked")
    private static <T> Property<T> property(Slot<?> slot) {
        return (Property<T>) slot.property;
    }

    /**
     * Waits for the pending saves.
     *
     * @param timeout how long to wait at most
     * @return the names of the records still being saved when the timeout elapsed, empty if every save ended
     * @throws InterruptedException if interrupted while waiting
     */
    public List<String> awaitSaves(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (lock) {
            while (pending > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                TimeUnit.NANOSECONDS.timedWait(lock, remaining);
            }
            List<String> unsaved = new ArrayList<>();
            Map<Class<?>, Slot<?>> loaded = slots;
            if (loaded != null) {
                for (Slot<?> slot : loaded.values()) {
                    if (slot.running) {
                        unsaved.add(slot.descriptor.type().getName());
                    }
                }
            }
            return unsaved;
        }
    }

    /**
     * Returns whether a save is pending.
     *
     * @return {@code true} while a record is being saved
     */
    public boolean hasPendingSaves() {
        synchronized (lock) {
            return pending > 0;
        }
    }

    /** Stops saving: a value set from now on is not saved, nor is a value set while a save runs saved again. */
    public void stopSaving() {
        synchronized (lock) {
            closed = true;
        }
    }

    /**
     * A settings record: its descriptor and its property, and whether a save runs.
     *
     * @param <T> the settings record
     */
    private final class Slot<T> {

        private final SettingsDescriptor<T> descriptor;
        private final Path config;
        private final Property<T> property;
        // Guarded by lock.
        private boolean running;
        private boolean again;

        Slot(SettingsDescriptor<T> descriptor, Path config, Property<T> property) {
            this.descriptor = descriptor;
            this.config = config;
            this.property = property;
        }

        void changed() {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                if (running) {
                    again = true;
                    return;
                }
                running = true;
                pending++;
            }
            Thread.ofVirtual().name(THREAD_NAME).start(this::save);
        }

        private void save() {
            boolean ended = false;
            try {
                while (!ended) {
                    saveOnce();
                    ended = next();
                }
            } finally {
                if (!ended) {
                    end();
                }
            }
        }

        // toml's writer and the file system throw unchecked exceptions too, and every failed save goes to the
        // FailureHandler.
        @SuppressWarnings("checkstyle:IllegalCatch")
        private void saveOnce() {
            try {
                write(config, descriptor.id(), (Record) property.get());
            } catch (IOException | RuntimeException e) {
                failures.failed(e);
            }
        }

        // Ends the save unless a value was set while it ran, in the same lock as changed(), so that no value is lost.
        private boolean next() {
            synchronized (lock) {
                if (again && !closed) {
                    again = false;
                    return false;
                }
                end();
                return true;
            }
        }

        private void end() {
            synchronized (lock) {
                running = false;
                again = false;
                pending--;
                lock.notifyAll();
            }
        }
    }
}
