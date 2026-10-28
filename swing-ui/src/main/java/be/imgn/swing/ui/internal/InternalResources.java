package be.imgn.swing.ui.internal;

import be.imgn.swing.ResourceOpener;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/** Opens the resources of the package {@code be.imgn.swing.ui.internal}: the framework's own texts. */
public final class InternalResources implements ResourceOpener {

    /** Creates the opener; the service loader does. */
    public InternalResources() {}

    @Override
    public String packageName() {
        return InternalResources.class.getPackageName();
    }

    @Override
    public Optional<InputStream> open(String fileName) {
        return Optional.ofNullable(InternalResources.class.getResourceAsStream(fileName));
    }

    @Override
    public Set<String> icons() {
        return Set.of();
    }
}
