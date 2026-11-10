package be.imgn.swing.examples.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.Appearance;
import be.imgn.swing.Appearances;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.Inspection;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.Window;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Locale;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class RefreshTest {

    private static String text(AppTester<RefreshApp> app, Function<RefreshApp, String> read) {
        return app.call(() -> read.apply(app.application()));
    }

    @Test
    void givesEveryPartItsResourcesAndMessagesInTheNewLocale() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            assertThat(text(app, view -> view.label.getText())).isEqualTo("Hello");
            assertThat(app.title()).isEqualTo("Refresh");

            app.locale(Locale.FRENCH);

            assertThat(text(app, view -> view.label.getText())).isEqualTo("Bonjour");
            assertThat(app.title()).isEqualTo("Rafraîchir");
            assertThat(text(app, view -> view.tabs.getTitleAt(0))).isEqualTo("Premier");
            assertThat(text(
                            app,
                            view -> view.table
                                    .getColumnModel()
                                    .getColumn(0)
                                    .getHeaderValue()
                                    .toString()))
                    .isEqualTo("Nom");
            assertThat(text(
                            app,
                            view -> view.table
                                    .getColumnModel()
                                    .getColumn(1)
                                    .getHeaderValue()
                                    .toString()))
                    .isEqualTo("Fixed");
            assertThat(text(app, view -> view.status.getText())).isEqualTo("Compte : 1");
            JButton button = app.component(JButton.class);
            assertThat(button.getText()).isEqualTo("Saluer");
            assertThat(button.getMnemonic()).isEqualTo(KeyEvent.VK_S);
            assertThat(button.getToolTipText()).isEqualTo("Salue");
            assertThat(button.getAccessibleContext().getAccessibleDescription()).isEqualTo("Salue encore");
            JMenu menu = app.component(JMenuBar.class).getMenu(0);
            assertThat(menu.getText()).isEqualTo("Choses");
            assertThat(menu.getMnemonic()).isEqualTo(KeyEvent.VK_C);
            assertThat(menu.getItem(0).getText()).isEqualTo("Saluer");
        }
    }

    @Test
    void registersTheShortcutOfTheNewLocale() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            JRootPane rootPane = app.component(JRootPane.class);
            KeyStroke english = KeyStroke.getKeyStroke("ctrl G");
            KeyStroke french = KeyStroke.getKeyStroke("ctrl S");
            assertThat(rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(english))
                    .isNotNull();

            app.locale(Locale.FRENCH);

            assertThat(rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(english))
                    .isNull();
            assertThat(rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(french))
                    .isNotNull();
            assertThat(app.component(JButton.class).getAction().getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(french);
        }
    }

    @Test
    void rendersAgainWhenASourceChanges() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.commands(RefreshAppCommands.class).greet().execute();

            assertThat(text(app, view -> view.status.getText())).isEqualTo("Count: 2");
        }
    }

    @Test
    void leavesWhatTheCodeOwnsOnceTheViewIsBuilt() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            assertThat(app.call(() -> app.application().label.isEnabled())).isFalse();
            app.run(() -> {
                app.application().label.setEnabled(true);
                app.bean(Window.class).setTitle("Mine");
            });

            app.locale(Locale.FRENCH);

            assertThat(app.call(() -> app.application().label.isEnabled())).isTrue();
            assertThat(app.title()).isEqualTo("Mine");
        }
    }

    @Test
    void titlesATabAddedAfterTheBuildAtTheNextRefresh() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.run(() -> app.application().tabs.add(app.application().later));
            assertThat(text(app, view -> view.tabs.getTitleAt(1))).isEmpty();

            app.locale(Locale.FRENCH);

            assertThat(text(app, view -> view.tabs.getTitleAt(1))).isEqualTo("Plus tard");
        }
    }

    @Test
    void ordersTheComponentsAsTheLocaleWrites() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            assertThat(app.call(() ->
                            app.application().label.getComponentOrientation().isLeftToRight()))
                    .isTrue();

            app.locale(Locale.of("ar"));

            assertThat(app.call(() -> app.application().label.getComponentOrientation()))
                    .isEqualTo(ComponentOrientation.RIGHT_TO_LEFT);
        }
    }

    @Test
    void leavesSwingsDefaultLocaleToTheTestsOwn() {
        Locale before = JComponent.getDefaultLocale();
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.locale(Locale.FRENCH);

            assertThat(JComponent.getDefaultLocale()).isEqualTo(before);
        }
    }

    @Test
    void startsInTheLocaleChosenInAnEarlierRun() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<RefreshApp> app =
                    AppTester.builder(RefreshApp.class).fileSystem(fileSystem).start()) {
                app.locale(Locale.FRENCH);
            }
            try (AppTester<RefreshApp> app =
                    AppTester.builder(RefreshApp.class).fileSystem(fileSystem).start()) {
                assertThat(text(app, view -> view.label.getText())).isEqualTo("Bonjour");
                assertThat(app.title()).isEqualTo("Rafraîchir");
            }
        }
    }

    @Test
    void switchesBackFromTheLocaleChosenInAnEarlierRun() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<RefreshApp> app =
                    AppTester.builder(RefreshApp.class).fileSystem(fileSystem).start()) {
                app.locale(Locale.of("ar"));
            }
            try (AppTester<RefreshApp> app =
                    AppTester.builder(RefreshApp.class).fileSystem(fileSystem).start()) {
                assertThat(app.call(() -> app.application().label.getComponentOrientation()))
                        .isEqualTo(ComponentOrientation.RIGHT_TO_LEFT);

                app.locale(Locale.ROOT);

                assertThat(app.call(() -> app.application().label.getComponentOrientation()))
                        .isEqualTo(ComponentOrientation.LEFT_TO_RIGHT);
            }
        }
    }

    @Test
    void readsTheDarkVariantWhileTheAppearanceIsDark() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            assertThat(app.call(() -> app.application().label.getForeground())).isEqualTo(new Color(0x202124));

            app.appearance(Appearance.DARK);

            assertThat(app.call(() -> app.application().label.getForeground())).isEqualTo(new Color(0xe8eaed));
            assertThat(app.call(() -> app.bean(Appearances.class).current().get()))
                    .isEqualTo(Appearance.DARK);

            app.appearance(Appearance.LIGHT);

            assertThat(app.call(() -> app.application().label.getForeground())).isEqualTo(new Color(0x202124));
        }
    }

    @Test
    void readsTheDarkVariantOverTheLanguageFile() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class, Locale.FRENCH)) {
            app.appearance(Appearance.DARK);

            assertThat(app.call(() -> app.application().label.getForeground())).isEqualTo(new Color(0xe8eaed));
            assertThat(app.call(() -> app.application().label.getText())).isEqualTo("Bonjour");
        }
    }

    @Test
    void titlesATabAddedWithTheUiAtOnce() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.run(() -> app.bean(Ui.class).addTab(app.application().tabs, app.application().later));

            assertThat(text(app, view -> view.tabs.getTitleAt(1))).isEqualTo("Later");
            app.run(() -> assertThatIllegalArgumentException()
                    .isThrownBy(() -> app.bean(Ui.class).addTab(app.application().tabs, new JPanel()))
                    .withMessageContaining("No @Exposed component field of be.imgn.swing.examples.refresh.RefreshApp"));
        }
    }

    @Test
    void inspectsAFieldAndACommandsButton() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            JLabel label = app.call(() -> app.application().label);

            assertThat(app.inspect(label)).isEqualTo(new Inspection(JLabel.class, RefreshApp.class, "label", null));
            assertThat(app.inspect(app.component(JButton.class)))
                    .isEqualTo(new Inspection(
                            JButton.class, RefreshApp.class, null, "be.imgn.swing.examples.refresh.RefreshApp#greet"));
        }
    }

    @Test
    void failsTheNextCallWhenADialogHasAComponentWithNoAccessibleName() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            // The dialog fails to open, and the tester holds the failure for its next call.
            assertThatThrownBy(() -> app.run(() -> app.bean(Ui.class).showDialog(UnnamedDialog.class)))
                    .hasMessageContaining("has no accessible name");

            assertThatThrownBy(app::isRunning)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("of be.imgn.swing.examples.refresh.UnnamedDialog has no accessible name");
        }
    }

    @Test
    void inspectsTheMenuItemOfACommand() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            JMenuItem item =
                    app.call(() -> app.component(JMenuBar.class).getMenu(0).getItem(0));

            assertThat(app.inspect(item).command()).isEqualTo("be.imgn.swing.examples.refresh.RefreshApp#greet");
        }
    }
}
