package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.ResourceOpener;
import be.imgn.swing.Resources;
import be.imgn.swing.internal.Contributions;
import be.imgn.swing.ui.IconLoader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;
import org.jspecify.annotations.Nullable;

/** Loads icons through the {@link IconLoader}s, and caches them by package and path. */
@Singleton
public final class IconCache {

    private final Resources resources;
    private final Injector injector;
    private final Map<String, Icon> icons = new ConcurrentHashMap<>();
    private volatile @Nullable List<IconLoader> loaders;

    @Inject
    IconCache(Resources resources, Injector injector) {
        this.resources = resources;
        this.injector = injector;
    }

    private List<IconLoader> loaders() {
        List<IconLoader> current = loaders;
        if (current == null) {
            current = List.copyOf(Contributions.of(injector, IconLoader.class));
            loaders = current;
        }
        return current;
    }

    /**
     * Returns the loader of an icon.
     *
     * @param path the icon's path
     * @return the first loader that supports it, if any
     */
    public Optional<IconLoader> loaderOf(String path) {
        return loaders().stream().filter(loader -> loader.supports(path)).findFirst();
    }

    /**
     * Loads an icon of a package.
     *
     * @param packageName the package the path is relative to
     * @param path the icon's path
     * @return the icon
     * @throws IllegalArgumentException if no loader supports the icon, or the package has no such icon
     */
    public Icon load(String packageName, String path) {
        return icons.computeIfAbsent(packageName + "/" + path, key -> {
            ResourceOpener opener = resources
                    .opener(packageName)
                    .orElseThrow(() -> new IllegalArgumentException("No resources in the package " + packageName));
            IconLoader loader =
                    loaderOf(path).orElseThrow(() -> new IllegalArgumentException("No icon loader supports " + path));
            return loader.load(opener, path);
        });
    }

    /**
     * Loads an icon of a class: its path is resolved against the package of the class, then against the packages of its
     * superclasses, nearest first, since the merged resources of a class no longer tell which class's file named the
     * icon, and the processor checked it against the package of that file.
     *
     * @param type the class whose resources name the icon
     * @param path the icon's path
     * @return the icon
     * @throws IllegalArgumentException if no loader supports the icon, or no package of the hierarchy has it
     */
    public Icon load(Class<?> type, String path) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            String packageName = current.getPackageName();
            if (resources
                    .opener(packageName)
                    .filter(opener -> has(opener, path))
                    .isPresent()) {
                return load(packageName, path);
            }
        }
        throw new IllegalArgumentException(
                "No icon " + path + " in the package of " + type.getName() + " or of its superclasses");
    }

    private static boolean has(ResourceOpener opener, String path) {
        Optional<InputStream> stream = opener.open(path);
        if (stream.isEmpty()) {
            return false;
        }
        try {
            stream.get().close();
        } catch (IOException e) {
            // The loader reports a resource it cannot read.
        }
        return true;
    }

    /**
     * Checks that a loader supports every icon that the resources of every package name.
     *
     * @throws IllegalStateException if an icon has no loader, such as an SVG icon without {@code swing-flatlaf}
     */
    public void check() {
        for (ResourceOpener opener : resources.openers()) {
            for (String icon : opener.icons()) {
                if (loaderOf(icon).isEmpty()) {
                    throw new IllegalStateException("No icon loader supports the icon " + icon + " of the package "
                            + opener.packageName() + (icon.endsWith(".svg") ? ": add swing-flatlaf" : ""));
                }
            }
        }
    }
}
