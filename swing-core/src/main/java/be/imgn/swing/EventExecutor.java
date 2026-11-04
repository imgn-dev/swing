package be.imgn.swing;

import java.lang.annotation.Annotation;
import java.util.concurrent.Executor;

/**
 * The executor of a subscribing annotation on the application's event bus, contributed with
 * {@code @IntoSet(EventExecutor.class)}. It replaces, on that bus, the executor the annotation declares with
 * {@code @SubscribingAnnotation(executor = ...)}, which a bus the framework didn't build uses.
 *
 * <p>Core contributes the executor of {@code @OnVirtualThread}, which runs each delivery with
 * {@link TaskService#run(Task, Scope)} in the application scope. The UI module contributes the executor of
 * {@code @OnEdt}, which hands each delivery to the {@link UiExecutor}.
 */
public interface EventExecutor {

    /**
     * Returns the subscribing annotation whose deliveries this executor runs.
     *
     * @return the annotation, meta-annotated {@code @SubscribingAnnotation}
     */
    Class<? extends Annotation> annotation();

    /**
     * Returns the executor that runs the deliveries to the methods with the annotation.
     *
     * @return the executor
     */
    Executor executor();
}
