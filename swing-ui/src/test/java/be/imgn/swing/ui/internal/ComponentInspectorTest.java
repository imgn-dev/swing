package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JButton;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class ComponentInspectorTest {

    @Test
    void showsTheTypeTheFieldAndItsTableTheCommandTheViewAndTheWindow() {
        assertThat(new ComponentInspector.Inspected(
                                JButton.class, String.class, null, "p.MemoView#save", WindowHost.Kind.FRAME)
                        .lines())
                .containsExactly(
                        "javax.swing.JButton",
                        "field none",
                        "command p.MemoView#save  [commands.save]",
                        "view java.lang.String",
                        "window frame");
        assertThat(new ComponentInspector.Inspected(JTextArea.class, String.class, "editor", null, WindowHost.Kind.MAIN)
                        .lines())
                .containsExactly(
                        "javax.swing.JTextArea",
                        "field editor  [editor]",
                        "view java.lang.String",
                        "window main frame");
    }
}
