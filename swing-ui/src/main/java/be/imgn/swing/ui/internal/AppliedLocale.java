package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The locale that Swing's defaults and the windows' component orientation took last: at startup step 4, then at each
 * refresh that follows a change of the display locale.
 */
@Singleton
public final class AppliedLocale {

    private final SwingLocale swing;
    private @Nullable Locale applied;

    @Inject
    AppliedLocale(SwingLocale swing) {
        this.swing = swing;
    }

    /**
     * Gives Swing the locale, on the EDT, unless it already has it.
     *
     * @param locale the display locale
     * @return {@code true} if the locale differs from the one applied last
     */
    public boolean apply(Locale locale) {
        if (Objects.equals(locale, applied)) {
            return false;
        }
        applied = locale;
        swing.apply(locale);
        return true;
    }
}
