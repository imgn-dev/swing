package be.imgn.swing.spi;

import be.imgn.swing.Settings;
import java.util.Set;

/**
 * The description of a {@link Settings} record, which the processor generates and registers as a service.
 *
 * @param <T> the settings record
 */
public interface SettingsDescriptor<T> {

    /**
     * Returns the settings record.
     *
     * @return the record class
     */
    Class<T> type();

    /**
     * Returns {@link Settings#value()}, the name of the record's file.
     *
     * @return the id
     */
    String id();

    /**
     * Returns the defaults: the record built with its constructor with no parameter.
     *
     * @return a new record holding the defaults
     */
    T defaults();

    /**
     * Returns the TOML keys of the record's {@code @Nullable} components.
     *
     * @return the keys
     */
    Set<String> nullable();
}
