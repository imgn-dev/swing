package be.imgn.swing.ui;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Opens frame views in frames of their own, and tracks them. Every method is called on the EDT.
 *
 * <p>Each frame gets a window scope and a view scope of its own, under the root injector: a frame never belongs to the
 * view that opened it. Closing a frame asks the exit vetoes of its scopes first; closing the last frame of a
 * {@link MultiFrame} application requests exit.
 */
public interface Frames {

    /**
     * Opens a frame showing a new view of a type.
     *
     * @param type the view class
     * @param <V> the view class
     * @return the view, once its frame is shown
     * @throws NullPointerException if {@code type} is {@code null}
     */
    <V extends FrameView> V open(Class<V> type);

    /**
     * Opens a frame showing a view created through the assisted factory that the processor generates for a view whose
     * constructor has {@code @Assisted} parameters, resolved through the view's child injector.
     *
     * @param factory the factory class
     * @param create creates the view with the factory
     * @param <F> the factory class
     * @param <V> the view class
     * @return the view, once its frame is shown
     * @throws NullPointerException if an argument is {@code null}
     */
    <F, V extends FrameView> V open(Class<F> factory, Function<? super F, ? extends V> create);

    /**
     * Opens a frame showing a variant of a view: its resources merge the variant's table over the base table.
     *
     * @param type the view class
     * @param variant the variant, a constant of the {@code @Variants} enum nested in the view class
     * @param <V> the view class
     * @param <E> the enum of the variants
     * @return the view, once its frame is shown
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the enum of {@code variant} is not nested in {@code type}
     * @throws IllegalStateException if the resources have no table for the variant
     */
    <V extends FrameView, E extends Enum<E>> V open(Class<V> type, E variant);

    /**
     * Returns the views of the open frames, in the order they were opened, the main view of a single-frame application
     * first.
     *
     * @return the views
     */
    List<FrameView> views();

    /**
     * Returns the view of the active frame: the frame that has the focus, else the frame that had it last, else the
     * frame opened last.
     *
     * @return the view, empty when no frame is open
     */
    Optional<FrameView> active();

    /**
     * Closes the frame of a view as its {@code closeWindow()} command does: asks the exit vetoes of its scopes, then
     * closes it. Closing the last frame of a multi-frame application requests exit instead.
     *
     * @param view the view
     * @return whether the frame closed
     * @throws NullPointerException if {@code view} is {@code null}
     * @throws IllegalArgumentException if {@code view} is not in an open frame
     */
    boolean close(FrameView view);

    /**
     * Brings the frame of a view to the front, deiconified, and focuses it.
     *
     * @param view the view
     * @throws NullPointerException if {@code view} is {@code null}
     * @throws IllegalArgumentException if {@code view} is not in an open frame
     */
    void toFront(FrameView view);
}
