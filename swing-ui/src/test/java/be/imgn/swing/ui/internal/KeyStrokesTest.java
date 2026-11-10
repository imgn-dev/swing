package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class KeyStrokesTest {

    @Test
    void writesTheMenuModifierAsShortcutSoThatAKeymapMovesBetweenSystems() {
        assertThat(KeyStrokes.format(KeyStrokes.parse("shortcut shift S", false), false))
                .isEqualTo("shortcut shift S");
        assertThat(KeyStrokes.format(KeyStrokes.parse("shortcut shift S", true), true))
                .isEqualTo("shortcut shift S");
    }

    @Test
    void writesTheOtherModifiersInOrder() {
        assertThat(KeyStrokes.format(KeyStroke.getKeyStroke("shift alt meta ctrl F5"), false))
                .isEqualTo("shortcut meta alt shift F5");
        assertThat(KeyStrokes.format(KeyStroke.getKeyStroke("shift alt meta ctrl F5"), true))
                .isEqualTo("shortcut ctrl alt shift F5");
        assertThat(KeyStrokes.format(KeyStroke.getKeyStroke("ENTER"), false)).isEqualTo("ENTER");
    }
}
