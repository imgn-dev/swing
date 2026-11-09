package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.toml.TomlString;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.lang.System.Logger.Level;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.jspecify.annotations.Nullable;

/** The image of the application's {@code icon} resource, which every window and the taskbar show. */
@Singleton
public final class ApplicationIcon {

    private static final System.Logger LOGGER = System.getLogger(ApplicationIcon.class.getName());

    private final UiServices services;
    private boolean loaded;
    private @Nullable Image image;

    @Inject
    ApplicationIcon(UiServices services) {
        this.services = services;
    }

    /**
     * Returns the image of the application's icon, loaded once. An icon that gives no image sets none, with the warning
     * logged once.
     *
     * @return the image, empty when the application has no icon or its icon gives no image
     */
    public synchronized Optional<Image> image() {
        if (!loaded) {
            loaded = true;
            Class<?> type = services.applicationType();
            if (services.resources().table(type).entries().get("icon") instanceof TomlString icon) {
                image = image(services.icons().load(type, icon.value()));
                if (image == null) {
                    LOGGER.log(
                            Level.WARNING,
                            "The icon " + icon.value() + " of " + type.getName()
                                    + " has no width or height: the windows get no icon image");
                }
            }
        }
        return Optional.ofNullable(image);
    }

    // A window takes an image, not an icon: an icon that holds no image, such as an SVG one, is painted into one,
    // unless
    // it has no size, since an image can't be empty.
    static @Nullable Image image(Icon icon) {
        if (icon instanceof ImageIcon imageIcon) {
            return imageIcon.getImage();
        }
        if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
            return null;
        }
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
