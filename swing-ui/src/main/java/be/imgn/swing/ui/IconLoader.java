package be.imgn.swing.ui;

import be.imgn.swing.ResourceOpener;
import javax.swing.Icon;

/**
 * Loads icons of one kind, contributed with {@code @IntoSet(IconLoader.class)}. {@code swing-ui} contributes the PNG
 * loader, {@code swing-flatlaf} the SVG loader.
 */
public interface IconLoader {

    /**
     * Returns whether this loader loads an icon, by its extension.
     *
     * @param path the icon's path, relative to its package
     * @return {@code true} if this loader loads it
     */
    boolean supports(String path);

    /**
     * Loads an icon.
     *
     * @param opener the opener of the icon's package
     * @param path the icon's path, relative to its package
     * @return the icon
     * @throws IllegalArgumentException if the package has no such icon
     */
    Icon load(ResourceOpener opener, String path);
}
