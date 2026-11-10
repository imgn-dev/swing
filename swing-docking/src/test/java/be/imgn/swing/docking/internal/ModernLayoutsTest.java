package be.imgn.swing.docking.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.docking.AlphaView;
import be.imgn.swing.docking.BetaView;
import be.imgn.swing.docking.DockApp;
import be.imgn.swing.docking.DockLayout.Orientation;
import be.imgn.swing.docking.GammaView;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.layouts.DockingSimplePanelNode;
import io.github.andrewauclair.moderndocking.layouts.DockingSplitPanelNode;
import io.github.andrewauclair.moderndocking.layouts.DockingTabPanelNode;
import io.github.andrewauclair.moderndocking.layouts.EmptyPanelNode;
import io.github.andrewauclair.moderndocking.layouts.WindowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.Test;

/**
 * The real dock's conversion between an arrangement and ModernDocking's layout, its persistent ids and the answers of
 * its dockables, which need no window: the headless build tests them, where ModernDocking can't draw a dock.
 */
class ModernLayoutsTest {

    private static DockEntry entry(AppTester<DockApp> app, Class<?> type) {
        return app.call(() -> {
            ViewHost host = app.bean(WindowRegistry.class).main().orElseThrow().views().stream()
                    .filter(view -> view.type().equals(type))
                    .findFirst()
                    .orElseThrow();
            return new DockEntry(host, new JPanel(), type::getSimpleName, DockEntry.idOf(type));
        });
    }

    private static Function<String, Optional<DockEntry>> byId(DockEntry... entries) {
        Map<String, DockEntry> map = new HashMap<>();
        for (DockEntry entry : entries) {
            map.put(entry.id(), entry);
        }
        return id -> Optional.ofNullable(map.get(id));
    }

    // Runs code with a docking instance of no window, on the EDT, and uninitializes it after.
    private static <T> T withDocking(AppTester<?> app, Function<Docking, T> code) {
        return app.call(() -> {
            Docking docking = new Docking(null);
            try {
                return code.apply(docking);
            } finally {
                docking.uninitialize();
            }
        });
    }

    private static Optional<DockNode> split(AppTester<?> app, DockEntry first, String second, double proportion) {
        return withDocking(
                app,
                docking -> ModernLayouts.fromModern(
                        new DockingSplitPanelNode(
                                docking,
                                new DockingSimplePanelNode(docking, first.id(), "", "", "First", "First"),
                                new DockingSimplePanelNode(docking, second, "", "", "Second", "Second"),
                                JSplitPane.HORIZONTAL_SPLIT,
                                proportion,
                                ""),
                        id -> id.equals(first.id()) ? Optional.of(first) : Optional.empty()));
    }

    @Test
    void namesAViewByItsClassOrByItsFactoryAndKey() {
        assertThat(DockEntry.idOf(AlphaView.class)).isEqualTo("be.imgn.swing.docking.AlphaView");
        assertThat(DockEntry.idOf(String.class, "contact-7")).isEqualTo("java.lang.String#contact-7");
    }

    @Test
    void convertsAnArrangementToAWindowLayoutAndBack() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            DockEntry alpha = entry(app, AlphaView.class);
            DockEntry beta = entry(app, BetaView.class);
            DockEntry gamma = entry(app, GammaView.class);
            DockNode arrangement = new DockNode.Split(
                    Orientation.HORIZONTAL,
                    0.3,
                    new DockNode.Tabs(List.of(alpha), alpha),
                    new DockNode.Tabs(List.of(beta, gamma), gamma));

            List<Object> seen = withDocking(app, docking -> {
                WindowLayout layout = new WindowLayout(ModernLayouts.toModern(docking, arrangement));
                DockingSplitPanelNode root = (DockingSplitPanelNode) layout.getRootNode();
                DockingTabPanelNode tabs = (DockingTabPanelNode) root.getRight();
                List<Object> found = new ArrayList<>();
                found.add(root.getOrientation());
                found.add(root.getDividerProportion());
                found.add(((DockingSimplePanelNode) root.getLeft()).getPersistentID());
                found.add(tabs.getPersistentIDs().stream()
                        .map(DockingSimplePanelNode::getPersistentID)
                        .toList());
                found.add(tabs.getSelectedTabID());
                found.add(ModernLayouts.fromModern(layout.getRootNode(), byId(alpha, beta, gamma)));
                return found;
            });

            assertThat(seen)
                    .containsExactly(
                            JSplitPane.HORIZONTAL_SPLIT,
                            0.3,
                            alpha.id(),
                            List.of(beta.id(), gamma.id()),
                            gamma.id(),
                            Optional.of(arrangement));
        }
    }

    @Test
    void roundsTheProportionsModernDockingReportsAndKeepsThemWithinTheSpace() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            DockEntry alpha = entry(app, AlphaView.class);
            DockEntry beta = entry(app, BetaView.class);

            Optional<DockNode> rounded = withDocking(
                    app,
                    docking -> ModernLayouts.fromModern(
                            new DockingSplitPanelNode(
                                    docking,
                                    new DockingSimplePanelNode(docking, alpha.id(), "", "", "Alpha", "Alpha"),
                                    new DockingSimplePanelNode(docking, beta.id(), "", "", "Beta", "Beta"),
                                    JSplitPane.HORIZONTAL_SPLIT,
                                    0.33366,
                                    ""),
                            byId(alpha, beta)));
            Optional<DockNode> edge = withDocking(
                    app,
                    docking -> ModernLayouts.fromModern(
                            new DockingSplitPanelNode(
                                    docking,
                                    new DockingSimplePanelNode(docking, alpha.id(), "", "", "Alpha", "Alpha"),
                                    new DockingSimplePanelNode(docking, beta.id(), "", "", "Beta", "Beta"),
                                    JSplitPane.HORIZONTAL_SPLIT,
                                    1.0,
                                    ""),
                            byId(alpha, beta)));

            assertThat(rounded)
                    .contains(new DockNode.Split(
                            Orientation.HORIZONTAL,
                            0.334,
                            new DockNode.Tabs(List.of(alpha), alpha),
                            new DockNode.Tabs(List.of(beta), beta)));
            assertThat(edge)
                    .hasValueSatisfying(node ->
                            assertThat(((DockNode.Split) node).proportion()).isEqualTo(0.999));
        }
    }

    @Test
    void leavesOutTheViewsOfNoEntry() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            DockEntry alpha = entry(app, AlphaView.class);

            assertThat(split(app, alpha, "p.Gone", 0.5)).contains(new DockNode.Tabs(List.of(alpha), alpha));
        }
    }

    @Test
    void readsNoArrangementFromAnEmptyLayout() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            Optional<DockNode> read =
                    withDocking(app, docking -> ModernLayouts.fromModern(new EmptyPanelNode(), id -> Optional.empty()));

            assertThat(read).isEmpty();
        }
    }

    @Test
    void answersModernDockingAsTheRealDockRequires() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            DockEntry alpha = entry(app, AlphaView.class);
            List<DockEntry> closing = new ArrayList<>();
            DockSurface.Listener listener = new DockSurface.Listener() {
                @Override
                public void selected(DockEntry entry) {
                    // Never shown, so never selected.
                }

                @Override
                public void closeRequested(DockEntry entry) {
                    closing.add(entry);
                }
            };

            List<Object> seen = app.call(() -> {
                DockablePanel panel = new DockablePanel(alpha, listener);
                return List.of(
                        panel.getPersistentID(),
                        panel.getTabText(),
                        panel.isFloatingAllowed(),
                        panel.isAutoHideAllowed(),
                        panel.isMinMaxAllowed(),
                        panel.isLimitedToWindow(),
                        panel.hasMoreMenuOptions(),
                        panel.isWrappableInScrollpane(),
                        panel.isClosable(),
                        panel.requestClose());
            });

            assertThat(seen)
                    .containsExactly(
                            "be.imgn.swing.docking.AlphaView",
                            "AlphaView",
                            false,
                            false,
                            false,
                            true,
                            false,
                            false,
                            true,
                            false);
            assertThat(closing).containsExactly(alpha);
        }
    }
}
