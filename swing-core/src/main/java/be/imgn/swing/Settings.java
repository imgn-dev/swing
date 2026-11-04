package be.imgn.swing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a settings record: what the user chooses and keeps between runs, saved in {@code <id>.toml} in the
 * application's config directory. The record is also annotated {@code @TomlConfig}, and its public constructor with no
 * parameter gives the defaults. The {@link SettingsStore} holds its value.
 *
 * <pre>{@code
 * @Settings("editor")
 * @TomlConfig
 * public record EditorSettings(int fontSize, boolean wordWrap) {
 *
 *     public EditorSettings() {
 *         this(14, true);
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Settings {

    /**
     * Returns the record's id, the name of its file: lower-case kebab case, one or more groups of {@code a}-{@code z}
     * and {@code 0}-{@code 9} separated by single {@code -}.
     *
     * @return the id
     */
    String value();
}
