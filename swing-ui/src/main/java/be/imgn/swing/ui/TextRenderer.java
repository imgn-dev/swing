package be.imgn.swing.ui;

import java.awt.Component;
import java.util.function.Function;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import org.jspecify.annotations.Nullable;

/**
 * A {@link DefaultListCellRenderer} that shows a text of each element.
 *
 * <p>Swing hands a renderer its value as an {@code Object}: the renderer takes the element, typed, from the model it
 * renders, by the index Swing gives, or, at no index, by the value. A value that holds no element, such as a list's
 * prototype cell value, is shown as is.
 *
 * @param <E> the type of the elements
 */
final class TextRenderer<E> extends DefaultListCellRenderer {

    private static final long serialVersionUID = 1L;

    private final transient Function<? super E, String> text;
    private final transient Elements<E> elements;

    /**
     * Creates a renderer.
     *
     * @param text the text of an element
     * @param elements the elements, by index
     */
    TextRenderer(Function<? super E, String> text, Elements<E> elements) {
        this.text = text;
        this.elements = elements;
    }

    /**
     * Returns the elements of a feed's copy, by index.
     *
     * @param feed the feed
     * @param <E> the type of the elements
     * @return the elements
     */
    static <E> Elements<E> of(ListFeed<E> feed) {
        return (index, value) -> index >= 0 && index < feed.size() ? feed.get(index) : null;
    }

    @Override
    public Component getListCellRendererComponent(
            JList<?> list, @Nullable Object value, int index, boolean isSelected, boolean cellHasFocus) {
        E rendered = elements.at(index, value);
        return super.getListCellRendererComponent(
                list, rendered == null ? value : text.apply(rendered), index, isSelected, cellHasFocus);
    }

    /**
     * The elements a renderer renders, by index.
     *
     * @param <E> the type of the elements
     */
    @FunctionalInterface
    interface Elements<E> {

        /**
         * Returns the element rendered at an index.
         *
         * @param index the index Swing renders, -1 for a value rendered outside the model's list, such as a combo box's
         *     selected item
         * @param value the value Swing renders
         * @return the element, {@code null} for one that the elements don't hold
         */
        @Nullable
        E at(int index, @Nullable Object value);
    }
}
