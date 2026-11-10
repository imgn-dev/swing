package be.imgn.swing.internal;

import be.imgn.swing.ResourceOpener;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/** The resources of the tests' package, as swing's processor generates the opener of a package. */
public final class TestResources implements ResourceOpener {

    /** Creates the opener; the service loader does. */
    public TestResources() {}

    @Override
    public String packageName() {
        return "be.imgn.swing.internal";
    }

    @Override
    public Optional<InputStream> open(String fileName) {
        return Optional.ofNullable(TestResources.class.getResourceAsStream(fileName));
    }

    @Override
    public Set<String> icons() {
        return Set.of();
    }
}
