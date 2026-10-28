package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessorOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/** What the steps of the processor share: the resource files, and the classes and packages with resources. */
final class Shared {

    /** The option that adds a directory to look for resources in, for IDEs that compile without copying them. */
    static final ProcessorOption<String> RESOURCES = ProcessorOption.ofString("be.imgn.swing.resources", "");

    private final ProcessingContext context;
    private final ResourceFiles files;
    private final Reporter reporter;
    private final Set<String> pendingNames = new LinkedHashSet<>();
    private final Set<String> checked = new HashSet<>();
    private final Map<String, PackageEntry> packages = new LinkedHashMap<>();
    private final Set<String> generatedCommandNames = new HashSet<>();

    Shared(ProcessingContext context) {
        this.context = context;
        String directory = context.option(RESOURCES);
        this.files =
                new ResourceFiles(context, directory.isEmpty() ? Optional.empty() : Optional.of(Path.of(directory)));
        this.reporter = new Reporter(context);
    }

    ProcessingContext context() {
        return context;
    }

    ResourceFiles files() {
        return files;
    }

    Reporter reporter() {
        return reporter;
    }

    /** Records a class whose resources the resources step checks at the end of the round. */
    void pending(TypeElement type) {
        String name = type.getQualifiedName().toString();
        if (!checked.contains(name)) {
            pendingNames.add(name);
        }
    }

    /** Returns the classes recorded in this round, without forgetting them. */
    List<TypeElement> peekPending() {
        List<TypeElement> pending = new ArrayList<>();
        for (String name : pendingNames) {
            if (!checked.contains(name)) {
                TypeElement type = context.elements().getTypeElement(name);
                if (type != null) {
                    pending.add(type);
                }
            }
        }
        return pending;
    }

    /** Returns the classes recorded in this round, and forgets them. */
    List<TypeElement> takePending() {
        List<TypeElement> pending = new ArrayList<>();
        for (String name : pendingNames) {
            if (checked.add(name)) {
                TypeElement type = context.elements().getTypeElement(name);
                if (type != null) {
                    pending.add(type);
                }
            }
        }
        pendingNames.clear();
        return pending;
    }

    /** Records that a package has resources, with the icons they name. */
    void resources(PackageElement packageElement, Set<String> icons, TypeElement origin) {
        PackageEntry entry = packages.computeIfAbsent(
                packageElement.getQualifiedName().toString(), name -> new PackageEntry(name, packageElement));
        entry.origins.add(origin);
        for (String icon : icons) {
            if (entry.icons.add(icon) && entry.written) {
                reporter.error(
                        origin,
                        "The icon " + icon + " of " + origin.getQualifiedName() + " is named after the package's"
                                + " $$Resources was generated: compile the class with the other classes of its"
                                + " package");
            }
        }
    }

    /** Returns the packages whose opener is not generated yet, and marks them generated. */
    List<PackageEntry> takeUnwrittenPackages() {
        List<PackageEntry> unwritten = new ArrayList<>();
        for (PackageEntry entry : packages.values()) {
            if (!entry.written) {
                entry.written = true;
                unwritten.add(entry);
            }
        }
        return unwritten;
    }

    /**
     * Claims the name of a generated commands class in this compilation.
     *
     * @return {@code false} if another class claimed it
     */
    boolean claimCommandsName(String qualifiedName) {
        return generatedCommandNames.add(qualifiedName);
    }

    /** A package with resources: its icons and the classes it has resources for. */
    static final class PackageEntry {
        private final String name;
        private final PackageElement element;
        private final Set<String> icons = new TreeSet<>();
        private final List<TypeElement> origins = new ArrayList<>();
        private boolean written;

        PackageEntry(String name, PackageElement element) {
            this.name = name;
            this.element = element;
        }

        String name() {
            return name;
        }

        PackageElement element() {
            return element;
        }

        Set<String> icons() {
            return icons;
        }

        List<TypeElement> origins() {
            return origins;
        }
    }
}
