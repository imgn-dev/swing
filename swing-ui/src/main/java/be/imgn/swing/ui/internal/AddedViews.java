package be.imgn.swing.ui.internal;

import be.imgn.inject.Injector;
import be.imgn.toml.TomlTable;
import java.awt.Component;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.Icon;

/**
 * Opens and closes the added views of open windows, views opened while their window is open, and gives the modules that
 * do, swing-docking and swing-wizard, what they read of views. Used on the EDT. Not public API.
 */
public interface AddedViews {

    /**
     * Opens an added view in an open window: builds it as a window's own view is built, but for its content, which
     * {@code place} places and returns, then restores its component state.
     *
     * @param window the window
     * @param type the view class, empty for a view created through an assisted factory
     * @param resolve resolves the view through its child injector
     * @param place places the view's content and returns it
     * @return the view
     * @throws IllegalStateException while a view of any window is being built, or if the window is closed or closing
     */
    ViewHost open(
            WindowHost window,
            Optional<? extends Class<?>> type,
            Function<Injector, ?> resolve,
            Function<ViewHost, Component> place);

    /**
     * Closes an added view: asks the exit vetoes of its view scope, then runs {@code leave}, which takes its content
     * out of the window, keeps its component state, closes its injector and scope, and removes it from its window's
     * views.
     *
     * @param view the view
     * @param leave takes the view's content out of the window, once no veto kept it open
     * @return {@code false} if a veto kept it open
     * @throws IllegalArgumentException if the view is not an added view
     */
    boolean close(ViewHost view, Runnable leave);

    /**
     * Returns whether a view of any window is being built, during which no view opens.
     *
     * @return {@code true} during a build
     */
    boolean isBuilding();

    /**
     * Returns the resources of a class, in the current locale and appearance.
     *
     * @param type the class
     * @return its merged table
     */
    TomlTable resources(Class<?> type);

    /**
     * Loads an icon named by a class's resources.
     *
     * @param type the class
     * @param path the path, relative to the class's package
     * @return the icon
     */
    Icon icon(Class<?> type, String path);

    /**
     * Returns the path of the {@code @Exposed} component field of a view that holds a component, nested ones included.
     *
     * @param view the view
     * @param component the component
     * @return the path, empty if no such field holds it
     */
    Optional<String> fieldOf(ViewHost view, Component component);

    /**
     * Returns a string property of the resources of the field of a view that holds a component.
     *
     * @param view the view
     * @param component the component
     * @param property the property, such as {@code step-title}
     * @return the value, empty if no field holds the component or its resources lack the property
     */
    Optional<String> fieldString(ViewHost view, Component component, String property);

    /**
     * Runs the accessibility checks over a component of a view, in development mode and tests only.
     *
     * @param view the view
     * @param component the component
     */
    void check(ViewHost view, Component component);

    /**
     * Finds a view class by its binary name, among those whose generated injector the {@code ServiceLoader} finds,
     * which are all the view classes the processor saw: no class is loaded by name.
     *
     * @param name the binary name
     * @return the class, empty if no view class has the name
     */
    Optional<Class<?>> viewClass(String name);
}
