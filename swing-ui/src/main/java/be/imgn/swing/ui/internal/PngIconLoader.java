package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ResourceOpener;
import be.imgn.swing.ui.IconLoader;
import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Loads PNG icons, with their {@code @2x} variant as a {@link java.awt.image.MultiResolutionImage}. */
@Singleton
@IntoSet(IconLoader.class)
public final class PngIconLoader implements IconLoader {

    private static final String EXTENSION = ".png";

    @Inject
    PngIconLoader() {}

    @Override
    public boolean supports(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    @Override
    public Icon load(ResourceOpener opener, String path) {
        BufferedImage base = read(opener, path)
                .orElseThrow(() -> new IllegalArgumentException("No icon " + path + " in " + opener.packageName()));
        String doubled = path.substring(0, path.length() - EXTENSION.length()) + "@2x" + EXTENSION;
        Optional<BufferedImage> twice = read(opener, doubled);
        Image image = twice.<Image>map(variant -> new BaseMultiResolutionImage(base, variant))
                .orElse(base);
        return new ImageIcon(image);
    }

    private static Optional<BufferedImage> read(ResourceOpener opener, String path) {
        Optional<InputStream> stream = opener.open(path);
        if (stream.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = stream.get()) {
            return Optional.ofNullable(ImageIO.read(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the icon " + path, e);
        }
    }
}
