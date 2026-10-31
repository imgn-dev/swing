package be.imgn.swing;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.core.platform.Platform;
import java.util.Optional;

/** What the application is and where it runs, bound in the root injector. */
public interface ApplicationInfo {

    /**
     * Returns the application class, {@link be.imgn.swing.spi.ApplicationDescriptor#type()}, whose resources are the
     * application's.
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
     * Returns the {@code name} key of the application class's resources, else {@link #name()}.
     *
     * @return the displayed name
     */
    String displayedName();

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

    /**
     * Returns {@code qualifier.vendor.name}, with the parts that are present.
     *
     * @return the identifier, such as {@code be.imgn.Counter}
     */
    String identifier();

    /**
     * Returns the platform the application runs on: the {@code Platform} bean, which this method is a shortcut to.
     * Core's default is {@code Platform.current()}; a bean of an application, or a binding, replaces it.
     *
     * @return the platform
     * @throws IllegalStateException if called before the root injector is built
     */
    Platform platform();

    /**
     * Returns the application's directories: the {@code ApplicationDirectories} bean, which this method is a shortcut
     * to. Core's default derives them from the {@code Platform} bean and the application's names; a bean of an
     * application, or a binding, replaces it.
     *
     * @return the directories
     * @throws IllegalStateException if called before the root injector is built
     */
    ApplicationDirectories directories();
}
