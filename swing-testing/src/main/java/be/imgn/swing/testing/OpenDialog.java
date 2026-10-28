package be.imgn.swing.testing;

import be.imgn.inject.Injector;
import java.util.List;
import javax.swing.JRootPane;

/**
 * A dialog view the tester shows, as an answer registered with {@link FakeDialogs#answerDialog} sees it. Its methods
 * are called on the EDT.
 *
 * @param <V> the dialog view class
 */
public final class OpenDialog<V> {

    private final V view;
    private final Injector injector;
    private final JRootPane rootPane;

    OpenDialog(V view, Injector injector, JRootPane rootPane) {
        this.view = view;
        this.injector = injector;
        this.rootPane = rootPane;
    }

    /**
     * Returns the dialog view.
     *
     * @return the view
     */
    public V view() {
        return view;
    }

    /**
     * Returns a bean of the dialog view's scope, such as its generated commands or its {@code StandardCommands}.
     *
     * @param type the bean's class
     * @param <C> the bean's class
     * @return the bean
     */
    public <C> C commands(Class<C> type) {
        return injector.getInstance(type);
    }

    /**
     * Returns the one component of a type in the dialog.
     *
     * @param type the type
     * @param <T> the type
     * @return the component
     * @throws IllegalStateException if there is none or more than one
     */
    public <T> T component(Class<T> type) {
        return Components.single(rootPane, type, "the dialog " + view.getClass().getName());
    }

    /**
     * Returns the components of a type in the dialog, in depth-first order.
     *
     * @param type the type
     * @param <T> the type
     * @return the components
     */
    public <T> List<T> components(Class<T> type) {
        return Components.find(rootPane, type);
    }
}
