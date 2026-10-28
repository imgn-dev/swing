package be.imgn.swing.internal;

import be.imgn.inject.Injector;
import be.imgn.inject.Key;
import be.imgn.inject.ScopingAnnotation;

/** Tells the instances of a window or view scope among those the construction hook reports. */
public final class ScopedInstances {

    private ScopedInstances() {}

    /**
     * Returns whether an instance reported with a host belongs to the host's scope, rather than being an unscoped bean
     * built through it. An instance with no definition under its own class, such as an assisted product, counts as
     * scoped.
     *
     * @param instance the instance
     * @param host the host it was reported with
     * @return {@code true} if the instance is scoped
     */
    public static boolean isScoped(Object instance, Injector host) {
        return host.getBeanDefinition(Key.of(instance.getClass()))
                .map(definition -> definition.annotationMetadata().hasStereotype(ScopingAnnotation.class))
                .orElse(true);
    }
}
