package be.imgn.swing.internal;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Provider;
import be.imgn.swing.ApplicationDescriptor;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.toml.TomlString;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** The application's information, from its descriptor and its platform. */
public final class DefaultApplicationInfo implements ApplicationInfo {

    private final ApplicationDescriptor descriptor;
    private final Platform platform;
    private final ApplicationDirectories directories;
    private volatile @Nullable Provider<Resources> resources;

    /**
     * Creates the information of an application.
     *
     * @param descriptor the application's descriptor
     * @param platform the platform it runs on
     */
    public DefaultApplicationInfo(ApplicationDescriptor descriptor, Platform platform) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.directories = directoriesOf(descriptor, platform);
    }

    private static ApplicationDirectories directoriesOf(ApplicationDescriptor descriptor, Platform platform) {
        Optional<String> vendor = descriptor.vendor();
        Optional<String> qualifier = descriptor.qualifier();
        if (vendor.isPresent() && qualifier.isPresent()) {
            return platform.applicationDirectories(qualifier.get(), vendor.get(), descriptor.name());
        }
        if (vendor.isPresent()) {
            return platform.applicationDirectories(vendor.get(), descriptor.name());
        }
        return platform.applicationDirectories(descriptor.name());
    }

    /**
     * Gives the resources the displayed name is read from, once the root injector is built.
     *
     * @param provider the provider of the resources
     */
    public void attach(Provider<Resources> provider) {
        this.resources = provider;
    }

    /**
     * Returns the application's descriptor.
     *
     * @return the descriptor
     */
    public ApplicationDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String name() {
        return descriptor.name();
    }

    @Override
    public String displayedName() {
        Provider<Resources> provider = resources;
        if (provider == null) {
            return name();
        }
        return provider.get().table(descriptor.type()).entries().get("name") instanceof TomlString name
                ? name.value()
                : name();
    }

    @Override
    public Optional<String> vendor() {
        return descriptor.vendor();
    }

    @Override
    public Optional<String> qualifier() {
        return descriptor.qualifier();
    }

    @Override
    public Optional<String> version() {
        return descriptor.version();
    }

    @Override
    public String identifier() {
        return Stream.of(qualifier(), vendor(), Optional.of(name()))
                .flatMap(Optional::stream)
                .collect(Collectors.joining("."));
    }

    @Override
    public Platform platform() {
        return platform;
    }

    @Override
    public ApplicationDirectories directories() {
        return directories;
    }
}
