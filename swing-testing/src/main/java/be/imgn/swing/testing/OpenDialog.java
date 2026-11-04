package be.imgn.swing.testing;

import be.imgn.inject.Injector;
import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JRootPane;

/**
 * A dialog view the tester shows, as an answer registered with {@link FakeDialogs#answerDialog} sees it. The answer
 * runs on the EDT, so it changes the dialog's components directly, and its methods are called on the EDT.
 *
 * @param <V> the dialog view class
 */
public final class OpenDialog<V> {

    private final V view;
    private final Injector injector;
    private final JRootPane rootPane;
    private final HeadlessWindows.HeadlessWindow window;
    private final TestExecutor executor;

    OpenDialog(
            V view,
            Injector injector,
            JRootPane rootPane,
            HeadlessWindows.HeadlessWindow window,
            TestExecutor executor) {
        this.view = view;
        this.injector = injector;
        this.rootPane = rootPane;
        this.window = window;
        this.executor = executor;
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
     * Resolves a bean through the dialog's view injector, so that beans of every scope of the dialog are reachable,
     * such as the dialog's {@code Form}.
     *
     * @param type the bean's class
     * @param <B> the bean's class
     * @return the bean
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <B> B bean(Class<B> type) {
        Objects.requireNonNull(type, "type");
        return injector.getInstance(type);
    }

    /**
     * Returns whether the dialog is open: {@code false} once {@code ok()} or {@code cancel()} closed it.
     *
     * @return {@code true} until the dialog closes
     */
    public boolean isOpen() {
        return window.isShown();
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
     * Returns the one component of a type in the dialog whose accessible name is {@code name}: the text of the label
     * whose {@code labelFor} it is, or the {@code accessible-name} of its resources.
     *
     * @param type the type
     * @param name the accessible name
     * @param <T> the type
     * @return the component
     * @throws IllegalStateException if there is none or more than one
     */
    public <T> T component(Class<T> type, String name) {
        return Components.named(
                rootPane, type, name, "the dialog " + view.getClass().getName());
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

    /**
     * Settles from within the answer, as every tester call does: runs the updates posted on the EDT so far, such as
     * those of the dialog's {@code Ui.bind} targets, which would otherwise wait for the answer to return, and the
     * periodic actions that are due, and waits for the tasks of commands, the {@code @OnVirtualThread} deliveries and
     * the pending settings saves.
     *
     * @throws AssertionError if a task or a save doesn't end in time
     */
    public void settle() {
        executor.settleOnEdt();
    }

    /**
     * Returns the last component focused through the dialog's {@code Window.focus}.
     *
     * @return the component, empty if none was
     */
    public Optional<Component> focused() {
        return window.focused();
    }
}
