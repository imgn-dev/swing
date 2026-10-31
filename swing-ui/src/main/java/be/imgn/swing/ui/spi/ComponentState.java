package be.imgn.swing.ui.spi;

import java.awt.Component;
import java.util.Map;

/**
 * Gives the components of a view whose state is saved with its window: its non-private {@code JSplitPane},
 * {@code JTabbedPane} and {@code JTable} fields. The processor generates one, {@code $X$$State}, for each view class,
 * and registers it as a service.
 *
 * @param <V> the view class
 */
public interface ComponentState<V> {

    /**
     * Returns the view class.
     *
     * @return the class
     */
    Class<V> type();

    /**
     * Returns the components whose state is saved.
     *
     * @param view the view
     * @return the components, by field name
     */
    Map<String, Component> components(V view);
}
