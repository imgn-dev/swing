package be.imgn.swing.desktop.internal;

import be.imgn.swing.ResourceOpener;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/** Opens the resources of the package {@code be.imgn.swing.desktop.internal}: the desktop's own texts. */
public final class DesktopInternalResources implements ResourceOpener {

    private static final String PACKAGE = "be.imgn.swing.desktop.internal";

    /** Creates the opener; the service loader does. */
    public DesktopInternalResources() {}

    @Override
    public String packageName() {
        return PACKAGE;
    }

    @Override
    public Optional<InputStream> open(String fileName) {
        return Optional.ofNullable(
                DesktopInternalResources.class.getResourceAsStream("/" + PACKAGE.replace('.', '/') + "/" + fileName));
    }

    @Override
    public Set<String> icons() {
        return Set.of();
    }
}
