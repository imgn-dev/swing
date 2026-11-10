package be.imgn.swing.examples.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.CommandPalette;
import be.imgn.swing.ui.PaletteEntry;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Trigger;
import java.util.List;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class PaletteTest {

    private static List<String> search(AppTester<RefreshApp> app, String query) {
        return app.call(() -> app.bean(CommandPalette.class).search(query).stream()
                .map(PaletteEntry::text)
                .toList());
    }

    @Test
    void findsACommandAndRunsItWithThePalettesTrigger() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.dialogs().answerCommand("gre");

            app.commands(StandardCommands.class).commandPalette().execute();

            assertThat(app.call(() -> app.application().lastTrigger())).isEqualTo(new Trigger.Palette());
            assertThat(app.call(() -> app.application().status.getText())).isEqualTo("Count: 2");
            assertThat(app.dialogs().commandQueries()).containsExactly("gre");
        }
    }

    @Test
    void ranksAPrefixFirstAndListsTheLastCommandRunFirst() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            assertThat(search(app, "gre")).startsWith("Greet");
            assertThat(search(app, "lea")).startsWith("Clear");
            app.dialogs().answerCommand("clear");

            app.commands(StandardCommands.class).commandPalette().execute();

            assertThat(search(app, "")).startsWith("Clear");
        }
    }

    @Test
    void listsNeitherDisabledCommandsNorItself() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            assertThat(search(app, "")).contains("Greet", "Clear").doesNotContain("Copy", "Command Palette…");
        }
    }

    @Test
    void hasItsShortcutInAFrameWhoseMenusDontShowItAndRunsNothingOnACancel() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            assertThat(app.call(() -> app.commands(StandardCommands.class)
                            .commandPalette()
                            .asAction()
                            .getValue(Action.ACCELERATOR_KEY)))
                    .isEqualTo(KeyStroke.getKeyStroke("ctrl shift P"));

            app.commands(StandardCommands.class).commandPalette().execute();

            assertThat(app.dialogs().commandQueries()).containsExactly("");
            assertThat(app.call(() -> app.application().status.getText())).isEqualTo("Count: 1");
        }
    }

    @Test
    void bindsItsShortcutInAFrameWhoseViewHasNoCommand() {
        try (AppTester<PlainApp> app = AppTester.start(PlainApp.class)) {
            assertThat(app.call(() -> app.component(JRootPane.class)
                            .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                            .get(KeyStroke.getKeyStroke("ctrl shift P"))))
                    .isEqualTo("be.imgn.swing.command:be.imgn.swing.ui.StandardCommands#commandPalette");
        }
    }

    @Test
    void runsNothingForACancelOrAQueryThatMatchesNothing() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            app.dialogs().cancelCommand();
            app.dialogs().answerCommand("zzz");

            app.commands(StandardCommands.class).commandPalette().execute();
            app.commands(StandardCommands.class).commandPalette().execute();

            assertThat(app.dialogs().commandQueries()).containsExactly("", "zzz");
            assertThat(app.call(() -> app.application().status.getText())).isEqualTo("Count: 1");
        }
    }

    @Test
    void ranksTheCommandRunLastFirstWithinARank() {
        try (AppTester<RefreshApp> app = AppTester.start(RefreshApp.class)) {
            // Exit starts with the query; the others contain it, in the order of their texts.
            assertThat(search(app, "e")).startsWith("Exit", "Clear", "Close", "Greet");
            app.dialogs().answerCommand("greet");

            app.commands(StandardCommands.class).commandPalette().execute();

            assertThat(search(app, "e")).startsWith("Exit", "Greet", "Clear", "Close");
        }
    }
}
