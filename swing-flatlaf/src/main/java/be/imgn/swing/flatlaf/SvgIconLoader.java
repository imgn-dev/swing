package be.imgn.swing.flatlaf;

import be.imgn.swing.ResourceOpener;
import be.imgn.swing.ui.IconLoader;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import javax.swing.Icon;

/** Loads SVG icons, through {@link FlatSVGIcon}: they stay sharp on HiDPI screens. */
public final class SvgIconLoader implements IconLoader {

    /** Creates the loader. */
    public SvgIconLoader() {}

    @Override
    public boolean supports(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".svg");
    }

    @Override
    public Icon load(ResourceOpener opener, String path) {
        InputStream stream = opener.open(path)
                .orElseThrow(() -> new IllegalArgumentException("No icon " + path + " in " + opener.packageName()));
        try (InputStream in = stream) {
            return new FlatSVGIcon(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the icon " + path, e);
        }
    }
}
