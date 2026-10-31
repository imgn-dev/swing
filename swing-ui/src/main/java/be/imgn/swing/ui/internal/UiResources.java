package be.imgn.swing.ui.internal;

import be.imgn.swing.ResourceOpener;
import be.imgn.swing.ui.StandardCommands;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/** Opens the resources of the package {@code be.imgn.swing.ui}: the standard commands' and menus'. */
public final class UiResources implements ResourceOpener {

    /** Creates the opener; the service loader does. */
    public UiResources() {}

    @Override
    public String packageName() {
        return StandardCommands.class.getPackageName();
    }

    @Override
    public Optional<InputStream> open(String fileName) {
        return Optional.ofNullable(StandardCommands.class.getResourceAsStream(fileName));
    }

    @Override
    public Set<String> icons() {
        return Set.of();
    }
}
