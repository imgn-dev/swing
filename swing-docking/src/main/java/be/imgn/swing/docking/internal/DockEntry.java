package be.imgn.swing.docking.internal;

import be.imgn.swing.ui.internal.ViewHost;
import java.awt.Component;
import java.util.function.Supplier;
import javax.swing.Icon;
import org.jspecify.annotations.Nullable;

/**
 * A view open in a dock, as its surface sees it: its host, its content, and the title and icon its tab and header show.
 */
public final class DockEntry {

    private final ViewHost host;
    private final Component content;
    private final Supplier<String> title;
    private final String id;
    private @Nullable Icon icon;

    /**
     * Creates the entry of a view.
     *
     * @param host the view's host
     * @param content the view's content
     * @param title gives the view's title, as it is now
     * @param id the view's persistent id: the binary name of its class, or, for a view opened through a factory, the
     *     binary name of the factory class, {@code #}, and its key
     */
    public DockEntry(ViewHost host, Component content, Supplier<String> title, String id) {
        this.host = host;
        this.content = content;
        this.title = title;
        this.id = id;
    }

    /**
     * Returns the persistent id of a view opened by its class: the binary name of its class.
     *
     * @param type the view class
     * @return the id
     */
    public static String idOf(Class<?> type) {
        return type.getName();
    }

    /**
     * Returns the persistent id of a view opened through an assisted factory: the binary name of the factory class,
     * {@code #}, and its key.
     *
     * @param factory the factory class
     * @param key what tells the views of the factory apart
     * @return the id
     */
    public static String idOf(Class<?> factory, String key) {
        return factory.getName() + "#" + key;
    }

    /**
     * Returns the view's host.
     *
     * @return the host
     */
    public ViewHost host() {
        return host;
    }

    /**
     * Returns the view's content.
     *
     * @return the content
     */
    public Component content() {
        return content;
    }

    /**
     * Returns the view's title, as it is now.
     *
     * @return the title
     */
    public String title() {
        return title.get();
    }

    /**
     * Returns the view's persistent id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Returns the view's icon.
     *
     * @return the icon, or {@code null} for none
     */
    public @Nullable Icon icon() {
        return icon;
    }

    /**
     * Sets the view's icon.
     *
     * @param value the icon, or {@code null} for none
     */
    public void icon(@Nullable Icon value) {
        this.icon = value;
    }
}
