package be.imgn.swing.internal;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Injector;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.spi.ApplicationDescriptor;
import be.imgn.toml.TomlString;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** The application's information, from its descriptor and its platform. */
public final class DefaultApplicationInfo implements ApplicationInfo {

    private final ApplicationDescriptor descriptor;
    private volatile @Nullable Injector injector;

    /**
     * Creates the information of an application.
     *
     * @param descriptor the application's descriptor
     */
    public DefaultApplicationInfo(ApplicationDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    /**
     * Gives the root injector, which the displayed name, the platform and the directories are read from, once it is
     * built.
     *
     * @param root the root injector
     */
    public void attach(Injector root) {
        this.injector = root;
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
    public Class<?> type() {
        return descriptor.type();
    }

    @Override
    public String name() {
        return descriptor.name();
    }

    @Override
    public String displayedName() {
        Injector root = injector;
        if (root == null) {
            return name();
        }
        return root.getInstance(Resources.class)
                                .table(descriptor.type())
                                .entries()
                                .get("name")
                        instanceof TomlString name
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
        return root().getInstance(Platform.class);
    }

    @Override
    public ApplicationDirectories directories() {
        return root().getInstance(ApplicationDirectories.class);
    }

    private Injector root() {
        Injector root = injector;
        if (root == null) {
            throw new IllegalStateException("The platform and the directories are known once the injector is built");
        }
        return root;
    }
}
