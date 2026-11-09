package be.imgn.swing.testing;

import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.internal.ViewHost;
import java.awt.Component;
import java.awt.Image;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JRootPane;

/**
 * A frame the application opened, as the tester sees it: the main frame of a single-frame application, or a frame that
 * {@code Frames} opened. Its methods run on the EDT and settle, as the tester's do.
 *
 * @param <V> the frame view class
 */
public final class OpenFrame<V> {

    private final AppTester<?> tester;
    private final V view;
    private final ViewHost host;

    OpenFrame(AppTester<?> tester, V view, ViewHost host) {
        this.tester = tester;
        this.view = view;
        this.host = host;
    }

    /**
     * Returns the frame view.
     *
     * @return the view
     */
    public V view() {
        return view;
    }

    /**
     * Returns a bean of the frame view's scope, such as its generated {@code <Class>Commands}.
     *
     * @param type the bean's class
     * @param <C> the bean's class
     * @return the bean
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <C> C commands(Class<C> type) {
        Objects.requireNonNull(type, "type");
        return tester.call(() -> host.injector().getInstance(type));
    }

    /**
     * Resolves a bean through the frame view's injector, so that beans of every scope of the frame are reachable.
     *
     * @param type the bean's class
     * @param <B> the bean's class
     * @return the bean
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <B> B bean(Class<B> type) {
        Objects.requireNonNull(type, "type");
        return tester.call(() -> host.injector().getInstance(type));
    }

    /**
     * Returns the one component of a type in the frame.
     *
     * @param type the type
     * @param <C> the type
     * @return the component
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalStateException if there is none or more than one
     */
    public <C> C component(Class<C> type) {
        Objects.requireNonNull(type, "type");
        return tester.call(() -> Components.single(rootPane(), type, description()));
    }

    /**
     * Returns the one component of a type in the frame whose accessible name is {@code name}: the text of the label
     * whose {@code labelFor} it is, or the {@code accessible-name} of its resources.
     *
     * @param type the type
     * @param name the accessible name
     * @param <C> the type
     * @return the component
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if there is none or more than one
     */
    public <C> C component(Class<C> type, String name) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        return tester.call(() -> Components.named(rootPane(), type, name, description()));
    }

    /**
     * Returns the components of a type in the frame, in depth-first order.
     *
     * @param type the type
     * @param <C> the type
     * @return the components
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <C> List<C> components(Class<C> type) {
        Objects.requireNonNull(type, "type");
        return tester.call(() -> Components.find(rootPane(), type));
    }

    /**
     * Returns the title of the frame.
     *
     * @return the title
     */
    public String title() {
        return tester.call(() -> window().title());
    }

    /**
     * Returns the icon image set on the frame.
     *
     * @return the image, empty if none was set
     */
    public Optional<Image> iconImage() {
        return tester.call(() -> window().iconImage());
    }

    /**
     * Returns the last component focused through the frame's {@code Window.focus}.
     *
     * @return the component, empty if none was
     */
    public Optional<Component> focused() {
        return tester.call(() -> window().focused());
    }

    /**
     * Returns how many times the frame was brought to the front, through {@code Window.toFront()}.
     *
     * @return the count
     */
    public int toFrontCount() {
        return tester.call(() -> window().toFrontCount());
    }

    /**
     * Returns whether the frame is open: {@code false} once it closed.
     *
     * @return {@code true} until the frame closes
     */
    public boolean isOpen() {
        return tester.call(() -> window().isShown());
    }

    /**
     * Runs the frame view's {@code closeWindow()} command on the EDT, settles, and returns whether the frame closed.
     *
     * @return whether the frame closed
     */
    public boolean close() {
        tester.run(() -> host.injector()
                .getInstance(StandardCommands.class)
                .closeWindow()
                .execute());
        return !isOpen();
    }

    private HeadlessWindows.HeadlessWindow window() {
        return (HeadlessWindows.HeadlessWindow) host.window().window();
    }

    private JRootPane rootPane() {
        return host.window().window().rootPane();
    }

    private String description() {
        return "the frame " + view.getClass().getName();
    }
}
