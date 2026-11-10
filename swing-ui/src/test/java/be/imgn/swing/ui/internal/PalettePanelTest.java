package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class PalettePanelTest {

    private static final List<String> ENTRIES = List.of("Save", "Save As…", "Select All");

    private final List<String> closed = new ArrayList<>();
    private final PalettePanel<String> panel = new PalettePanel<>(
            String.class,
            query -> ENTRIES.stream()
                    .filter(entry -> entry.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                    .toList(),
            entry -> entry,
            entry -> entry.equals("Save") ? KeyStroke.getKeyStroke("ctrl S") : null,
            "Type a command",
            "No matching command",
            entry -> closed.add(entry == null ? "cancel" : entry));

    // As the key press the field's input map binds.
    private void press(String key) {
        JTextField field = panel.field();
        Object action = field.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key));
        field.getActionMap().get(action).actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, key));
    }

    @Test
    void filtersAsTheUserTypesTheFirstEntrySelected() {
        assertThat(panel.shown()).containsExactlyElementsOf(ENTRIES);

        panel.field().setText("sav");

        assertThat(panel.shown()).containsExactly("Save", "Save As…");
        assertThat(panel.selected()).isEqualTo("Save");
        assertThat(panel.field().getAccessibleContext().getAccessibleName()).isEqualTo("Type a command");
    }

    @Test
    void movesTheSelectionAndChoosesWithEnter() {
        press("DOWN");
        press("DOWN");
        assertThat(panel.selected()).isEqualTo("Select All");
        press("UP");

        press("ENTER");

        assertThat(closed).containsExactly("Save As…");
    }

    @Test
    void cancelsWithEscape() {
        press("ESCAPE");

        assertThat(closed).containsExactly("cancel");
    }

    @Test
    void showsNoneAndChoosesNothingWithoutAMatch() {
        panel.field().setText("zzz");

        assertThat(panel.shown()).isEmpty();
        assertThat(panel.selected()).isNull();
        press("ENTER");
        assertThat(closed).isEmpty();
    }
}
