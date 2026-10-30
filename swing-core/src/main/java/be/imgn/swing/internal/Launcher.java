package be.imgn.swing.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.ApplicationDescriptor;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Arguments;
import be.imgn.swing.DisplayLocale;
import be.imgn.swing.Scope;
import be.imgn.swing.StartupStep;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

/** The steps of the startup that need no user interface: the descriptor, the root injector and the startup steps. */
public final class Launcher {

    private Launcher() {}

    /**
     * Returns the descriptor of an application class, found through the {@link ServiceLoader}.
     *
     * @param type the application class
     * @return its descriptor
     * @throws IllegalArgumentException if no descriptor is registered for the class
     */
    public static ApplicationDescriptor descriptor(Class<?> type) {
        for (ApplicationDescriptor descriptor : ServiceLoader.load(ApplicationDescriptor.class)) {
            if (descriptor.type() == type) {
                return descriptor;
            }
        }
        throw new IllegalArgumentException("No application descriptor for " + type.getName()
                + ": annotate it with @Application, compile it with swing's processor, and on the module path,"
                + " register the services with gaia's register-services goal");
    }

    /**
     * Returns a builder of the root injector, binding the application's information, its arguments, its display locale
     * and its scope.
     *
     * @param info the application's information
     * @param arguments the arguments given to the launch
     * @param locale the display locale
     * @param scope the application's scope
     * @return the builder
     */
    public static Injector.Builder rootBuilder(
            DefaultApplicationInfo info, List<String> arguments, Locale locale, DisposalScope scope) {
        return Injector.builder()
                .bind(ApplicationInfo.class, info)
                .bind(Arguments.class, new Arguments(arguments))
                .bind(DisplayLocale.class, new DisplayLocale(locale))
                .bind(Scope.class, scope);
    }

    /**
     * Builds the root injector, and gives the application's information its resources.
     *
     * @param builder the builder of the root injector
     * @param info the application's information
     * @return the root injector
     */
    public static Injector build(Injector.Builder builder, DefaultApplicationInfo info) {
        Injector injector = builder.build();
        info.attach(injector);
        return injector;
    }

    /**
     * Runs every startup step, sorted by order then by class name.
     *
     * @param injector the root injector
     */
    public static void runStartupSteps(Injector injector) {
        for (StartupStep step : Contributions.sorted(injector, StartupStep.class, StartupStep::order)) {
            step.run();
        }
    }
}
