package be.imgn.swing.ui.spi;

import be.imgn.swing.ui.CommandRegistry;

/**
 * Writes a view class's resources to the components its fields hold, with plain setter calls. The processor generates
 * one, {@code $X$$Injector}, for each view class, and registers it as a service.
 *
 * @param <V> the view class
 */
public interface ComponentInjector<V> {

    /**
     * Returns the view class.
     *
     * @return the class
     */
    Class<V> type();

    /**
     * Applies every property except {@code tab-title}: after the view's constructor, before {@code content()}.
     *
     * @param view the view
     * @param resources the view class's merged resources
     */
    void inject(V view, ComponentResources resources);

    /**
     * Applies the {@code tab-title} properties: after {@code content()} returns, once tabs exist.
     *
     * @param view the view
     * @param resources the view class's merged resources
     */
    void injectTabTitles(V view, ComponentResources resources);

    /**
     * Registers the standard commands that the view class, or one of its superclasses, overrides, before the view is
     * resolved, so that every {@code StandardCommands} of the view returns the user-written command.
     *
     * @param registry the view's command registry
     */
    void registerOverrides(CommandRegistry registry);
}
