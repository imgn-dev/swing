package be.imgn.swing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the application class: its names, as gaia's {@code ApplicationDirectories} takes them, and its version.
 *
 * <p>The processor generates an {@link be.imgn.swing.spi.ApplicationDescriptor} for the annotated class, which
 * {@code Swing.launch} finds through the {@link java.util.ServiceLoader}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Application {

    /**
     * Returns the application's name, as gaia's {@code ApplicationDirectories} takes it, and the displayed name unless
     * the class's resources give a {@code name} key.
     *
     * @return the application's name, such as {@code Counter}
     */
    String name();

    /**
     * Returns the organization or person that makes the application.
     *
     * @return the vendor, such as {@code imgn}, or an empty string for none
     */
    String vendor() default "";

    /**
     * Returns the vendor's top-level domain; only with a {@link #vendor()}.
     *
     * @return the qualifier, such as {@code be}, or an empty string for none
     */
    String qualifier() default "";

    /**
     * Returns the version, shown in the About dialog.
     *
     * @return the version, such as {@code 1.0}, or an empty string for none
     */
    String version() default "";
}
