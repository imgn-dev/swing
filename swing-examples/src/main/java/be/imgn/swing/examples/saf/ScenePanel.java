package be.imgn.swing.examples.saf;

import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.jspecify.annotations.Nullable;

/**
 * A panel of {@link ActionMapExample} that holds a scene of icons, which its create and remove commands change from its
 * popup menu. Clicking an icon selects it; remove is enabled only with a selection. The title of its border and the
 * icon it draws are the {@code title} and {@code icon} properties of the example's resources.
 */
public class ScenePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int GAP = 8;

    private transient @Nullable Icon icon;
    private final transient List<Rectangle> nodes = new ArrayList<>();
    private final transient Property<Integer> selected = Property.of(-1);

    ScenePanel() {
        super(new GridBagLayout());
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    select(event.getPoint());
                }
            }
        });
    }

    /**
     * Sets the icon the scene draws for each node.
     *
     * @param icon the icon
     */
    public void setIcon(Icon icon) {
        this.icon = Objects.requireNonNull(icon, "icon");
        repaint();
    }

    /**
     * Returns the icon the scene draws for each node.
     *
     * @return the icon, or {@code null} before it is set
     */
    public @Nullable Icon getIcon() {
        return icon;
    }

    /** Adds an icon after the last one. */
    void createNode() {
        Icon node = Objects.requireNonNull(icon, "The scene has no icon");
        Insets insets = getInsets();
        int x = nodes.isEmpty() ? insets.left : nodes.getLast().x + nodes.getLast().width + GAP;
        nodes.add(new Rectangle(x, insets.top, node.getIconWidth(), node.getIconHeight()));
        repaint();
    }

    /** Removes the selected icon. */
    void removeNode() {
        int index = selected.get();
        if (index >= 0) {
            nodes.remove(index);
            selected.set(-1);
            repaint();
        }
    }

    void select(Point point) {
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).contains(point)) {
                selected.set(index);
                repaint();
                return;
            }
        }
    }

    /**
     * Returns whether an icon is selected.
     *
     * @return the observable state
     */
    Observable<Boolean> hasSelection() {
        return selected.map(index -> index >= 0);
    }

    /**
     * Returns the number of icons of the scene.
     *
     * @return the number of icons
     */
    int nodeCount() {
        return nodes.size();
    }

    /**
     * Returns the bounds of an icon of the scene.
     *
     * @param index the index of the icon
     * @return the bounds
     */
    Rectangle nodeBounds(int index) {
        return new Rectangle(nodes.get(index));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        graphics.setColor(getBackground());
        graphics.fillRect(0, 0, getWidth(), getHeight());
        Icon image = icon;
        if (image == null) {
            return;
        }
        for (int index = 0; index < nodes.size(); index++) {
            Rectangle node = nodes.get(index);
            image.paintIcon(this, graphics, node.x, node.y);
            if (index == selected.get()) {
                graphics.setColor(Color.BLACK);
                graphics.drawRect(node.x - 1, node.y - 1, node.width + 1, node.height + 1);
            }
        }
    }
}
