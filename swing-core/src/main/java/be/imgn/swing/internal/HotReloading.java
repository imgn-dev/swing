package be.imgn.swing.internal;

/**
 * Whether the resources are reloaded when their files change: in development mode, {@code -Dbe.imgn.swing.dev=true},
 * and never under the test harness, which binds its own.
 *
 * @param enabled whether the files are watched
 */
public record HotReloading(boolean enabled) {

    /** The system property of development mode. */
    public static final String DEV_PROPERTY = "be.imgn.swing.dev";
}
