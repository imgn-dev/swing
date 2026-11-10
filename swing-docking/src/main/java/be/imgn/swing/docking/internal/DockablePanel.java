package be.imgn.swing.docking.internal;

import io.github.andrewauclair.moderndocking.Dockable;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.jspecify.annotations.Nullable;

/**
 * The panel that holds a dock view's content in ModernDocking, which takes its dockables for components. It may not
 * float, auto-hide, be maximized or minimized, and is limited to its frame; ModernDocking doesn't wrap it in a scroll
 * pane, since a view puts its content in one itself when it needs one. Its header's close button closes it through the
 * dock, which undocks it when no veto keeps it open.
 */
final class DockablePanel extends JPanel implements Dockable {

    private static final long serialVersionUID = 1L;

    private final transient DockEntry entry;
    private final transient DockSurface.Listener listener;

    DockablePanel(DockEntry entry, DockSurface.Listener listener) {
        super(new BorderLayout());
        this.entry = entry;
        this.listener = listener;
        add(entry.content(), BorderLayout.CENTER);
        // A tab that becomes the selected one of its group shows.
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                listener.selected(entry);
            }
        });
    }

    DockEntry entry() {
        return entry;
    }

    @Override
    public String getPersistentID() {
        return entry.id();
    }

    @Override
    public String getTabText() {
        return entry.title();
    }

    @Override
    public @Nullable Icon getIcon() {
        return entry.icon();
    }

    @Override
    public boolean isFloatingAllowed() {
        return false;
    }

    @Override
    public boolean isLimitedToWindow() {
        return true;
    }

    @Override
    public boolean isClosable() {
        return true;
    }

    @Override
    public boolean requestClose() {
        // The dock closes it, asking its vetoes: ModernDocking mustn't undock it a second time.
        listener.closeRequested(entry);
        return false;
    }

    @Override
    public boolean isAutoHideAllowed() {
        return false;
    }

    @Override
    public boolean isMinMaxAllowed() {
        return false;
    }

    @Override
    public boolean isWrappableInScrollpane() {
        return false;
    }

    @Override
    public boolean hasMoreMenuOptions() {
        return false;
    }
}
