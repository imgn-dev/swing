package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.testing.AppTester;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LocaleExamplesTest {

    @Test
    void showsEachPaneInItsOwnLocaleWhateverTheDisplayLocale() {
        for (Locale display : List.of(Locale.ENGLISH, Locale.FRENCH)) {
            try (AppTester<LocaleExample3> app = AppTester.start(LocaleExample3.class, display)) {
                LocaleExample3 example = app.application();

                assertThat(app.call(() -> texts(example.en))).containsExactly("text", "What ?");
                assertThat(app.call(() -> texts(example.fr))).containsExactly("texte", "Quoi ?");
                assertThat(app.call(() -> texts(example.es))).containsExactly("texto", "Qué ?");
            }
        }
    }

    @Test
    void keepsEachPaneInItsLocaleWhenTheDisplayLocaleChanges() {
        try (AppTester<LocaleExample3> app = AppTester.start(LocaleExample3.class)) {
            app.locale(Locale.FRENCH);

            assertThat(app.call(() -> texts(app.application().en))).containsExactly("text", "What ?");
            assertThat(app.title()).isEqualTo("ExitListener Demo");
        }
    }

    @Test
    void asksBeforeExitingFromAPanesButton() {
        try (AppTester<LocaleExample3> app = AppTester.start(LocaleExample3.class)) {
            app.dialogs().answerConfirm(false);

            app.run(() -> app.application().es.button().doClick());

            assertThat(app.dialogs().confirmed()).containsExactly("Stop ?");
            assertThat(app.isRunning()).isTrue();
        }
    }

    @Test
    void asksBeforeExitingInTheDisplayLocale() {
        try (AppTester<LocaleExample3> app = AppTester.start(LocaleExample3.class, Locale.FRENCH)) {
            app.dialogs().answerConfirm(true);

            app.run(() -> app.application().en.button().doClick());

            assertThat(app.dialogs().confirmed()).containsExactly("Sûr ?");
            assertThat(app.isRunning()).isFalse();
        }
    }

    @Test
    void changesTheGlobalPaneAndNotThePartOneWhenTheDisplayLocaleIsChosen() {
        try (AppTester<LocaleExample4> app = AppTester.start(LocaleExample4.class, Locale.ENGLISH)) {
            LocaleExample4 example = app.application();

            app.run(() -> example.globalChoice.setSelectedItem(Locale.FRENCH));

            assertThat(app.call(() -> texts(example.global))).containsExactly("texte", "Quoi ?");
            assertThat(app.call(() -> texts(example.part))).containsExactly("text", "What ?");
        }
    }

    @Test
    void changesOnlyThePartPaneWhenItsLocaleIsChosen() {
        try (AppTester<LocaleExample4> app = AppTester.start(LocaleExample4.class, Locale.ENGLISH)) {
            LocaleExample4 example = app.application();

            app.run(() -> example.partChoice.setSelectedItem(LocaleExample3.SPANISH));

            assertThat(app.call(() -> texts(example.part))).containsExactly("texto", "Qué ?");
            assertThat(app.call(() -> texts(example.global))).containsExactly("text", "What ?");
        }
    }

    @Test
    void startsInTheDisplayLocaleChosenInAnEarlierRun() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<LocaleExample4> app = AppTester.builder(LocaleExample4.class)
                    .fileSystem(fileSystem)
                    .locale(Locale.ENGLISH)
                    .start()) {
                app.run(() -> app.application().globalChoice.setSelectedItem(Locale.FRENCH));
            }
            try (AppTester<LocaleExample4> app = AppTester.builder(LocaleExample4.class)
                    .fileSystem(fileSystem)
                    .locale(Locale.ENGLISH)
                    .start()) {
                LocaleExample4 example = app.application();

                assertThat(app.call(() -> texts(example.global))).containsExactly("texte", "Quoi ?");
                assertThat(app.call(() -> example.globalChoice.getSelectedItem()))
                        .isEqualTo(Locale.FRENCH);
            }
        }
    }

    private static List<String> texts(LocalePane pane) {
        return List.of(pane.label().getText(), pane.button().getText());
    }
}
