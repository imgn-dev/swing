package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Property;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.DynamicEntry;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.Ui;
import java.awt.EventQueue;
import java.util.List;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/** What the tester checks of its own, on the counter. */
class AppTesterTest {

    @Test
    void duplicateMnemonicsInARebuiltDynamicSectionFailTheNextCall() throws Exception {
        Property<List<DynamicEntry>> entries = Property.of(List.of(DynamicEntry.item("&Alpha", () -> {})));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Ui ui = app.commands(Ui.class);
            JLabel label = app.component(JLabel.class);
            EventQueue.invokeAndWait(() -> ui.popupMenu(label, MenuEntry.dynamic(entries)));
            assertThat(app.isRunning()).isTrue();

            entries.set(List.of(DynamicEntry.item("&Alpha", () -> {}), DynamicEntry.item("&Apple", () -> {})));

            assertThatThrownBy(app::isRunning)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Duplicate mnemonic")
                    .hasMessageContaining("Alpha and Apple");
        }
    }
}
