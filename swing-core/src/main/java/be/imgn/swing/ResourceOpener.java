package be.imgn.swing;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/**
 * Opens the resources of one package, from the package's own module, so that reading them needs no open package. The
 * processor generates one, {@code $$Resources}, for each package that holds a class with resources, and registers it as
 * a service.
 */
public interface ResourceOpener {

    /**
     * Returns the package whose resources this opener reads.
     *
     * @return the package name, such as {@code com.example}
     */
    String packageName();

    /**
     * Opens a resource of the package.
     *
     * @param fileName the name of the resource, relative to the package, such as {@code NotesView.toml}
     * @return the stream of the resource, or empty if the package has none with that name
     */
    Optional<InputStream> open(String fileName);

    /**
     * Returns the icon paths that the package's resources name, relative to the package.
     *
     * @return the icon paths, such as {@code icons/save.svg}
     */
    Set<String> icons();
}
