package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.swing.internal.DisposalScope;
import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class InspectorOverlayTest {

    private static final KeyStroke SHORTCUT = KeyStroke.getKeyStroke("ctrl alt shift I");

    private final JRootPane rootPane = new JRootPane();
    private final ShortcutBindings shortcuts = new ShortcutBindings(rootPane, new UiMode(true));
    private final DisposalScope scope = DisposalScope.root("window");
    private final InspectorOverlay overlay =
            InspectorOverlay.install(rootPane, shortcuts, scope, component -> List.of("inspected"), false);

    private void pressShortcut() {
        Object key = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(SHORTCUT);
        rootPane.getActionMap().get(key).actionPerformed(new ActionEvent(rootPane, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void liesHiddenInTheDragLayerAndLetsTheMouseThrough() {
        assertThat(JLayeredPane.getLayer(overlay)).isEqualTo(JLayeredPane.DRAG_LAYER);
        assertThat(overlay.getParent()).isEqualTo(rootPane.getLayeredPane());
        assertThat(overlay.isVisible()).isFalse();
        assertThat(overlay.contains(0, 0)).isFalse();
    }

    @Test
    void togglesWithItsShortcutAndHidesWhenTheWindowCloses() {
        pressShortcut();
        assertThat(overlay.isVisible()).isTrue();
        pressShortcut();
        assertThat(overlay.isVisible()).isFalse();

        pressShortcut();
        scope.close();

        assertThat(overlay.isVisible()).isFalse();
    }

    @Test
    void reportsACommandWithItsShortcutWhateverItsPriority() {
        AbstractAction action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {}
        };

        assertThatIllegalStateException()
                .isThrownBy(() -> shortcuts.register(
                        SHORTCUT, "p.View#inspect", action, ShortcutBindings.Priority.KEYMAP, () -> {}))
                .withMessageContaining("have the same shortcut");
        assertThat(shortcuts.commandOf(SHORTCUT)).isEqualTo("be.imgn.swing.inspector");
    }
}
