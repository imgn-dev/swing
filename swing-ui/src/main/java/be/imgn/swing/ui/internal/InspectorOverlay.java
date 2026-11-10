package be.imgn.swing.ui.internal;

import be.imgn.swing.Scope;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;

/**
 * The inspector overlay of a window, in development mode: a transparent component over the root pane's layered pane,
 * which the mouse and the keyboard go through, and which outlines the component under the mouse and shows what it is.
 * Its shortcut toggles it.
 */
public final class InspectorOverlay extends JComponent {

    /** The shortcut that toggles the overlay of the window that has the focus. */
    public static final String SHORTCUT = "shortcut alt shift I";

    private static final long serialVersionUID = 1L;
    private static final String IDENTITY = "be.imgn.swing.inspector";
    private static final int PADDING = 4;
    // A light yellow, as tool tips often are.
    private static final Color BOX = new Color(0xffffe0);

    private final JRootPane rootPane;
    private final transient Function<Component, List<String>> describe;
    private final transient AWTEventListener motion = this::moved;
    private transient @Nullable Component target;
    private transient List<String> lines = List.of();

    private InspectorOverlay(JRootPane rootPane, Function<Component, List<String>> describe) {
        this.rootPane = rootPane;
        this.describe = describe;
        setOpaque(false);
        setVisible(false);
    }

    /**
     * Installs the overlay of a window, hidden, with its shortcut, until the window closes.
     *
     * @param rootPane the window's root pane
     * @param shortcuts the window's shortcuts
     * @param scope the window's scope
     * @param describe the lines that tell what a component is
     * @param macOS whether the shortcut's menu modifier is Meta
     * @return the overlay
     */
    public static InspectorOverlay install(
            JRootPane rootPane,
            ShortcutBindings shortcuts,
            Scope scope,
            Function<Component, List<String>> describe,
            boolean macOS) {
        InspectorOverlay overlay = new InspectorOverlay(rootPane, describe);
        JLayeredPane layered = rootPane.getLayeredPane();
        layered.add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, layered.getWidth(), layered.getHeight());
        layered.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                overlay.setBounds(0, 0, layered.getWidth(), layered.getHeight());
            }
        });
        // Not a command: neither the palette nor the keymap lists it, and a command with its shortcut is reported.
        shortcuts.register(
                KeyStrokes.parse(SHORTCUT, macOS),
                IDENTITY,
                new AbstractAction() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void actionPerformed(ActionEvent event) {
                        overlay.toggle();
                    }
                },
                ShortcutBindings.Priority.INSPECTOR,
                () -> {});
        scope.onClose(overlay::close);
        return overlay;
    }

    /** Shows the overlay if it is hidden, else hides it. */
    public void toggle() {
        if (isVisible()) {
            close();
        } else {
            setVisible(true);
            Toolkit.getDefaultToolkit().addAWTEventListener(motion, AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }
    }

    // Also when the window closes, so that the listener leaves with it.
    private void close() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(motion);
        setVisible(false);
        target = null;
        lines = List.of();
    }

    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    // The deepest visible component under the mouse in this window, the overlay excluded since it contains no point.
    private void moved(AWTEvent event) {
        if (!(event instanceof MouseEvent mouse) || !(mouse.getSource() instanceof Component source)) {
            return;
        }
        // The mouse in another window leaves nothing outlined in this one.
        Component found = null;
        if (rootPane.equals(SwingUtilities.getRootPane(source))) {
            JLayeredPane layered = rootPane.getLayeredPane();
            Point point = SwingUtilities.convertPoint(source, mouse.getPoint(), layered);
            found = SwingUtilities.getDeepestComponentAt(layered, point.x, point.y);
        }
        if (!Objects.equals(found, target)) {
            target = found;
            // Once per target, since an inspection lists the view's fields.
            lines = found == null ? List.of() : describe.apply(found);
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Component inspected = target;
        // A target hidden or removed since is not drawn where it no longer is.
        if (inspected == null
                || inspected.getParent() == null
                || !inspected.isShowing()
                || !SwingUtilities.isDescendingFrom(inspected, rootPane)) {
            return;
        }
        Rectangle bounds = SwingUtilities.convertRectangle(inspected.getParent(), inspected.getBounds(), this);
        graphics.setColor(Color.RED);
        graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        FontMetrics metrics = graphics.getFontMetrics();
        int width = lines.stream().mapToInt(metrics::stringWidth).max().orElse(0) + 2 * PADDING;
        int height = lines.size() * metrics.getHeight() + 2 * PADDING;
        // Beside the component, below it when it fits, and always inside the window.
        int x = Math.max(0, Math.min(bounds.x, getWidth() - width));
        int y = bounds.y + bounds.height + height <= getHeight()
                ? bounds.y + bounds.height
                : Math.max(0, bounds.y - height);
        graphics.setColor(BOX);
        graphics.fillRect(x, y, width, height);
        graphics.setColor(Color.DARK_GRAY);
        graphics.drawRect(x, y, width - 1, height - 1);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(
                    lines.get(index), x + PADDING, y + PADDING + metrics.getAscent() + index * metrics.getHeight());
        }
    }
}
