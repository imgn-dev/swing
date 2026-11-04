package be.imgn.swing.ui;

import be.imgn.gaia.core.event.SubscribingAnnotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Subscribes a method to the events of its parameter's type, delivered on the EDT, in the order they were published.
 *
 * <pre>{@code
 * @OnEdt
 * void onImportProgress(ImportProgress event) { ... }
 * }</pre>
 *
 * <p>The method rules are gaia's, as for {@code @Subscribe}, and gaia-processor checks them. On a bus the framework
 * didn't build, the deliveries run through {@link EdtExecutor}; the application's bus hands them to the
 * {@code UiExecutor}, so that the test harness runs them as it runs the other posted work. A module whose subscribers
 * use this annotation requires {@code be.imgn.swing.ui}, not {@code requires static}, since the generated code creates
 * an {@link EdtExecutor}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@SubscribingAnnotation(executor = EdtExecutor.class)
public @interface OnEdt {}
