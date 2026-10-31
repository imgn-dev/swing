package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlTable;
import java.awt.Color;
import java.awt.EventQueue;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class ComponentExamplesTest {

    @Test
    void singleFrameExample4BuildsItsMenusAndToolbar() {
        try (AppTester<SingleFrameExample4> app = AppTester.start(SingleFrameExample4.class)) {
            assertThat(app.title()).isEqualTo("Textual actions demo");
            assertThat(app.components(JMenu.class)).extracting(JMenu::getText).containsExactly("File", "Edit");
            assertThat(app.components(JMenuItem.class))
                    .filteredOn(item -> !(item instanceof JMenu))
                    .extracting(JMenuItem::getText)
                    .containsExactly("Open...", "Close", "Exit", "Cut", "Copy", "Paste");
            assertThat(app.components(JButton.class))
                    .filteredOn(button -> button.getClass() == JButton.class)
                    .extracting(JButton::getText)
                    .containsExactly("Cut", "Copy", "Paste");
            StandardCommands standard = app.commands(StandardCommands.class);
            assertThat(standard.cut().asAction().isEnabled()).isFalse();

            SingleFrameExample4 example = app.application();
            example.textPane().setText("changed");
            app.commands(SingleFrameExample4Commands.class).close().execute();

            assertThat(example.textPane().getText()).startsWith("Load a text file with the open File menu item.");
        }
    }

    @Test
    void selectedPropertyExampleSharesTheSelectedState() {
        try (AppTester<SelectedPropertyExample> app = AppTester.start(SelectedPropertyExample.class)) {
            SelectedPropertyExampleCommands commands = app.commands(SelectedPropertyExampleCommands.class);

            commands.toggleAction().execute();

            assertThat(app.application().selected.get()).isTrue();
            assertThat(app.component(JCheckBox.class).isSelected()).isTrue();
            assertThat(app.component(JRadioButton.class).isSelected()).isTrue();

            commands.buttonAction().execute();

            assertThat(app.component(JCheckBox.class).isSelected()).isFalse();
            assertThat(app.component(JTextArea.class).getText())
                    .contains("selected true, toggle action true, check box true, radio button true")
                    .contains("selected false, toggle action false, check box false, radio button false");
        }
    }

    @Test
    void sessionStorageExample1SavesTheStateOfItsComponents() throws IOException {
        try (AppTester<SessionStorageExample1> app = AppTester.start(SessionStorageExample1.class)) {
            SessionStorageExample1 example = app.application();
            Path file = app.commands(ApplicationDirectories.class).state().resolve("window-state.toml");
            assertThat(titles(example.colors))
                    .containsExactly("red", "blue", "green", "yellow", "orange", "pink", "all colors");
            assertThat(example.all.getColumnModel().getColumn(0).getHeaderValue())
                    .isEqualTo("Name");
            assertThat(example.redDarker.getText()).isEqualTo("red.darker()");
            assertThat(example.redDarker.getForeground()).isEqualTo(Color.WHITE);
            assertThat(example.redDarker.getFont().getSize()).isEqualTo(24);
            assertThat(example.explanation.getText()).startsWith("Tab selection and split divider locations");
            EventQueue.invokeLater(() -> {
                example.colors.setSelectedIndex(2);
                example.red.setDividerLocation(42);
            });

            app.commands(StandardCommands.class).exit().execute();

            TomlTable components =
                    Toml.parse(file).root().getTable("windows").getTable("main").getTable("components");
            assertThat(components.getLong("colors.selected-index")).isEqualTo(2);
            assertThat(components.getLong("red.divider-location")).isEqualTo(42);
            assertThat(components.getTable("all").getArray("columns").values()).hasSize(4);
        }
    }

    @Test
    void tabbedClosesATabFromTheButtonOfItsHeader() throws Exception {
        try (AppTester<Tabbed> app = AppTester.start(Tabbed.class)) {
            JTabbedPane tabs = app.component(JTabbedPane.class);
            assertThat(titles(tabs)).containsExactly("One", "Two", "Three");
            assertThat(app.components(JLabel.class))
                    .extracting(JLabel::getText)
                    .containsExactlyInAnyOrder(
                            "The first tab", "The second tab", "The third tab", "One", "Two", "Three");
            JButton close = (JButton) ((JPanel) tabs.getTabComponentAt(1)).getComponent(1);
            assertThat(close.getToolTipText()).isEqualTo("close this tab");

            onEdt(close::doClick);

            assertThat(titles(tabs)).containsExactly("One", "Three");
        }
    }

    @Test
    void singleFrameExample6ShowsItsSecondaryWindowsAsDialogs() {
        try (AppTester<SingleFrameExample6> app = AppTester.start(SingleFrameExample6.class)) {
            app.dialogs().answerDialog(SecondaryWindow.class, dialog -> {
                assertThat(dialog.component(JLabel.class).getText()).isEqualTo("Label One");
                assertThat(dialog.component(JButton.class).getText()).isEqualTo("Hide Window");
                dialog.component(JButton.class).doClick();
            });

            app.commands(SingleFrameExample6Commands.class).showWindow1().execute();

            assertThat(app.dialogs().shown()).containsExactly(SecondaryWindow.class);
            assertThat(app.component(JLabel.class).getText()).isEqualTo("Main Label");
        }
    }

    private static List<String> titles(JTabbedPane tabs) {
        return IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt).toList();
    }

    private static void onEdt(Runnable action) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(action);
    }
}
