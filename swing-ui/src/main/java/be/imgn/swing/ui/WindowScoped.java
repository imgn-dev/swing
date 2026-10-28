package be.imgn.swing.ui;

import be.imgn.inject.ScopingAnnotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Scopes a bean to its window: one instance per window, cached in the window's child injector. */
@Documented
@ScopingAnnotation
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface WindowScoped {}
