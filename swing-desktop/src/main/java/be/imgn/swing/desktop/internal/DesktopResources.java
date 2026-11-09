package be.imgn.swing.desktop.internal;

import be.imgn.swing.ResourceOpener;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/** Opens the resources of the package {@code be.imgn.swing.desktop}: the commands of documents. */
public final class DesktopResources implements ResourceOpener {

    private static final String PACKAGE = "be.imgn.swing.desktop";

    /** Creates the opener; the service loader does. */
    public DesktopResources() {}

    @Override
    public String packageName() {
        return PACKAGE;
    }

    @Override
    public Optional<InputStream> open(String fileName) {
        return Optional.ofNullable(
                DesktopResources.class.getResourceAsStream("/" + PACKAGE.replace('.', '/') + "/" + fileName));
    }

    @Override
    public Set<String> icons() {
        return Set.of();
    }
}
