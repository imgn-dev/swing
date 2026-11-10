package be.imgn.swing.spi;

import be.imgn.swing.Resources;
import java.util.Locale;

/**
 * Builds the implementation of a {@code @Messages} interface for a fixed locale. The processor generates one for each
 * {@code @Messages} interface and registers it as a service, which {@link be.imgn.swing.Locales#messages(Class,
 * Locale)} finds through the {@link java.util.ServiceLoader}.
 */
public interface MessagesDescriptor {

    /**
     * Returns the {@code @Messages} interface.
     *
     * @return the interface
     */
    Class<?> type();

    /**
     * Builds an implementation of the interface that reads the files of a locale.
     *
     * @param resources the resources
     * @param locale the locale of the messages
     * @return the implementation
     */
    Object create(Resources resources, Locale locale);
}
