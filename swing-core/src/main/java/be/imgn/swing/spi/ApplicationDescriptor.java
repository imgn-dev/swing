package be.imgn.swing.spi;

import be.imgn.swing.Application;
import java.util.Optional;

/**
 * The names of an {@link Application} class, which the processor generates and registers as a service. It holds no
 * handlers: the application class is a bean.
 */
public interface ApplicationDescriptor {

    /**
     * Returns the annotated class.
     *
     * @return the application class
     */
    Class<?> type();

    /**
     * Returns {@link Application#name()}.
     *
     * @return the application's name
     */
    String name();

    /**
     * Returns {@link Application#vendor()}, if given.
     *
     * @return the vendor, if any
     */
    Optional<String> vendor();

    /**
     * Returns {@link Application#qualifier()}, if given.
     *
     * @return the qualifier, if any
     */
    Optional<String> qualifier();

    /**
     * Returns {@link Application#version()}, if given.
     *
     * @return the version, if any
     */
    Optional<String> version();
}
