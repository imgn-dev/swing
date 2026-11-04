package be.imgn.swing.ui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field that generated code reads, which is therefore not private: a component field, for its resources and its
 * window state, or a field that {@link Command#enabled()} or {@link Command#selected()} names. Generated code reads no
 * other field.
 *
 * <p>A component field of a component that a view holds, such as a label of a panel that several views show, is
 * {@code @Exposed} too when the resources of those views name it in nested tables, such as {@code [home.streetLabel]}.
 * The view's generated code reads it through the enclosing fields, so it is public when its class is in another package
 * than the view's.
 *
 * <p>An exposed field is neither private nor static, and its type is a {@code java.awt.Component} or an
 * {@code Observable<Boolean>}. Checkstyle's {@code VisibilityModifier} accepts it once {@code be.imgn.swing.ui.Exposed}
 * is among its {@code ignoreAnnotationCanonicalNames}: on the imgn parent, through the property
 * {@code checkstyle.visibility.ignoredAnnotations}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Exposed {}
