package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

/** {@code &} marks the mnemonic, {@code &&} is a literal ampersand. */
class MnemonicsTest {

    @Test
    void anAmpersandMarksTheMnemonic() {
        Mnemonics mnemonics = Mnemonics.parse("É&dition");

        assertThat(mnemonics.text()).isEqualTo("Édition");
        assertThat(mnemonics.index()).isEqualTo(1);
        assertThat(mnemonics.hasMnemonic()).isTrue();
        assertThat(mnemonics.character()).isEqualTo('D');
        assertThat(mnemonics.keyCode()).isEqualTo(KeyEvent.VK_D);
    }

    @Test
    void twoAmpersandsAreALiteralAmpersand() {
        Mnemonics mnemonics = Mnemonics.parse("Save && &Quit");

        assertThat(mnemonics.text()).isEqualTo("Save & Quit");
        assertThat(mnemonics.index()).isEqualTo(7);
        assertThat(mnemonics.character()).isEqualTo('Q');
    }

    @Test
    void theFirstMarkerWins() {
        Mnemonics mnemonics = Mnemonics.parse("&Save &As");

        assertThat(mnemonics.text()).isEqualTo("Save As");
        assertThat(mnemonics.index()).isZero();
    }

    @Test
    void aTextWithoutMarkerHasNoMnemonic() {
        Mnemonics mnemonics = Mnemonics.parse("Save");

        assertThat(mnemonics.text()).isEqualTo("Save");
        assertThat(mnemonics.index()).isEqualTo(-1);
        assertThat(mnemonics.hasMnemonic()).isFalse();
        assertThatThrownBy(mnemonics::character).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(mnemonics::keyCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTrailingAmpersandIsLiteralAndMarksNothing() {
        Mnemonics mnemonics = Mnemonics.parse("Save&");

        assertThat(mnemonics.text()).isEqualTo("Save&");
        assertThat(mnemonics.hasMnemonic()).isFalse();
    }
}
