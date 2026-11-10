package be.imgn.swing.wizard.internal;

import be.imgn.swing.ResourceOpener;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/** The resources of the wizard's commands, the texts of Previous and Next. Found through the {@code ServiceLoader}. */
public final class WizardResources implements ResourceOpener {

    private static final String PACKAGE = "be.imgn.swing.wizard";

    /** Creates the opener, as the {@code ServiceLoader} does. */
    public WizardResources() {}

    @Override
    public String packageName() {
        return PACKAGE;
    }

    @Override
    public Optional<InputStream> open(String fileName) {
        return Optional.ofNullable(
                WizardResources.class.getResourceAsStream("/" + PACKAGE.replace('.', '/') + "/" + fileName));
    }

    @Override
    public Set<String> icons() {
        return Set.of();
    }
}
