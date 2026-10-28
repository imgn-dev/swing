package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.ResourceOpener;
import be.imgn.swing.Resources;
import be.imgn.swing.internal.Contributions;
import be.imgn.swing.ui.IconLoader;
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
