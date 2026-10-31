package be.imgn.swing.ui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the enum, nested in a dialog view, whose constants name the variants of that dialog: dialogs of one class that
 * differ only by their resources, opened with {@link Ui#showDialog(Class, Enum)}.
 *
 * <p>Each constant has a table {@code [variants.<constant in kebab case>]} in the dialog's resources, laid out like the
 * base table, its title and its component tables included, which overrides the base table key by key. Commands and
 * menus stay in the base table, for every variant. The processor checks that every constant has a table in the base
 * file, that every table names a constant, and that each table follows the base table's rules.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Variants {}
