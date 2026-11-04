package be.imgn.swing.examples.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Property;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.ui.AboutDialog;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The OK, Cancel and Apply commands of a dialog view on a form, on the notes' {@link PreferencesDialog}, whose form is
 * valid for a font size from 6 to 72, and whose {@code commit()} sets the {@link EditorSettings}.
 */
class PreferencesDialogTest {

    private static final EditorSettings DEFAULTS = new EditorSettings();

    private static Optional<EditorSettings> showPreferencesDialog(AppTester<NotesApp> app) {
        Ui ui = app.commands(Ui.class);
        return app.call(() -> ui.showDialog(PreferencesDialog.class));
    }

    private static Property<EditorSettings> settings(AppTester<NotesApp> app) {
        return app.bean(SettingsStore.class).get(EditorSettings.class);
    }

    private static Property<EditorSettings> settings(OpenDialog<?> dialog) {
        return dialog.bean(SettingsStore.class).get(EditorSettings.class);
    }

    private static JTextField field(OpenDialog<?> dialog) {
        return field(dialog, "Font size");
    }

    private static JTextField field(OpenDialog<?> dialog, String name) {
        return dialog.component(JTextField.class, name);
    }

    private static StandardCommands commands(OpenDialog<?> dialog) {
        return dialog.commands(StandardCommands.class);
    }

    private static boolean dirty(OpenDialog<?> dialog) {
        return dialog.bean(Form.class).dirty().get();
    }

    private static Optional<String> toolTip(JComponent component) {
        return Optional.ofNullable(component.getToolTipText());
    }

    private static List<String> texts(List<? extends AbstractButton> buttons) {
        return buttons.stream().map(AbstractButton::getText).toList();
    }

    // Runs the action that a key bound in the dialog's root pane runs, as the user's key press would.
    private static void press(OpenDialog<?> dialog, int keyCode) {
        JRootPane rootPane = SwingUtilities.getRootPane(field(dialog));
        Object key = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(keyCode, 0));
        Action action = rootPane.getActionMap().get(key);
        action.actionPerformed(new ActionEvent(rootPane, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void okWithAnErrorKeepsTheDialogOpenShowingTheErrorAndFocusesItsField() {
        List<Object> seen = new ArrayList<>();
        AtomicReference<@Nullable Component> focused = new AtomicReference<>();
        AtomicReference<JTextField> fontSizeField = new AtomicReference<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                BoundCommand ok = commands(dialog).ok();
                JTextField field = field(dialog);
                fontSizeField.set(field);
                field.setText("5");

                seen.add(ok.execute());
                seen.add(dialog.isOpen());
                seen.add(toolTip(field));
                focused.set(dialog.focused().orElse(null));

                field.setText("five");
                seen.add(ok.execute());
                seen.add(dialog.isOpen());
                seen.add(toolTip(field));
            });

            Optional<EditorSettings> result = showPreferencesDialog(app);

            // OK stays enabled while the form is invalid: it runs, and shows what is wrong.
            assertThat(seen)
                    .containsExactly(
                            true, true, Optional.of("From 6 to 72"), true, true, Optional.of("A whole number"));
            assertThat(focused.get()).isSameAs(fontSizeField.get());
            // The answer left the dialog open: it was cancelled.
            assertThat(result).isEmpty();
            assertThat(settings(app).get()).isEqualTo(DEFAULTS);
        }
    }

    @Test
    void okCommitsADirtyFormThenClosesTheDialogWithItsResult() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                field(dialog).setText("20");
                dialog.component(JCheckBox.class).setSelected(false);
                seen.add(commands(dialog).ok().execute());
                seen.add(dialog.isOpen());
            });

            Optional<EditorSettings> result = showPreferencesDialog(app);

            assertThat(seen).containsExactly(true, false);
            assertThat(result).contains(new EditorSettings(20, false));
            assertThat(settings(app).get()).isEqualTo(new EditorSettings(20, false));
        }
    }

    @Test
    void okOnACleanFormClosesTheDialogWithoutCommitting() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                // A commit would set the settings back to the dialog's values, the defaults.
                settings(dialog).set(new EditorSettings(30, false));
                seen.add(dirty(dialog));
                seen.add(commands(dialog).ok().execute());
                seen.add(dialog.isOpen());
            });

            Optional<EditorSettings> result = showPreferencesDialog(app);

            assertThat(seen).containsExactly(false, true, false);
            assertThat(result).contains(DEFAULTS);
            assertThat(settings(app).get()).isEqualTo(new EditorSettings(30, false));
        }
    }

    @Test
    void applyIsEnabledOnlyWhileTheFormIsDirty() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                BoundCommand apply = commands(dialog).apply();
                JTextField field = field(dialog);
                JCheckBox wordWrap = dialog.component(JCheckBox.class);
                seen.add(apply.execute());

                field.setText("16");
                field.setText("14");
                // Edited back to its original value, the form is clean again.
                seen.add(apply.execute());

                wordWrap.setSelected(false);
                wordWrap.setSelected(true);
                seen.add(apply.execute());

                field.setText("big");
                // A text the converter rejects leaves the font size unchanged, so the form clean.
                seen.add(apply.execute());

                seen.add(dialog.isOpen());
                commands(dialog).cancel().execute();
            });

            showPreferencesDialog(app);

            assertThat(seen).containsExactly(false, false, false, false, true);
            assertThat(settings(app).get()).isEqualTo(DEFAULTS);
        }
    }

    @Test
    void applyCommitsTheFormThenMakesItCleanAndKeepsTheDialogOpen() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                BoundCommand apply = commands(dialog).apply();
                field(dialog).setText("16");
                seen.add(dirty(dialog));

                seen.add(apply.execute());
                seen.add(settings(dialog).get().fontSize());
                seen.add(dirty(dialog));
                seen.add(dialog.isOpen());
                // Clean once applied, the form disables Apply until it changes again.
                seen.add(apply.execute());

                field(dialog).setText("18");
                seen.add(apply.execute());
                seen.add(settings(dialog).get().fontSize());

                seen.add(commands(dialog).cancel().execute());
                seen.add(dialog.isOpen());
            });

            Optional<EditorSettings> result = showPreferencesDialog(app);

            assertThat(seen).containsExactly(true, true, 16, false, true, false, true, 18, true, false);
            // Cancel closes with an empty result, and keeps what Apply committed.
            assertThat(result).isEmpty();
            assertThat(settings(app).get()).isEqualTo(new EditorSettings(18, true));
        }
    }

    @Test
    void applyWithAnErrorCommitsNothing() {
        List<Object> seen = new ArrayList<>();
        AtomicReference<@Nullable Component> focused = new AtomicReference<>();
        AtomicReference<JTextField> fontSizeField = new AtomicReference<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                JTextField field = field(dialog);
                fontSizeField.set(field);
                field.setText("73");

                seen.add(commands(dialog).apply().execute());
                seen.add(dirty(dialog));
                seen.add(dialog.isOpen());
                seen.add(toolTip(field));
                focused.set(dialog.focused().orElse(null));
            });

            showPreferencesDialog(app);

            assertThat(seen).containsExactly(true, true, true, Optional.of("From 6 to 72"));
            assertThat(focused.get()).isSameAs(fontSizeField.get());
            assertThat(settings(app).get()).isEqualTo(DEFAULTS);
        }
    }

    @Test
    void cancelClosesTheDialogWithAnEmptyResultWhateverTheForm() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                field(dialog).setText("20");
                seen.add(commands(dialog).cancel().execute());
                seen.add(dialog.isOpen());
            });
            Optional<EditorSettings> valid = showPreferencesDialog(app);

            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                field(dialog).setText("twenty");
                seen.add(commands(dialog).cancel().execute());
                seen.add(dialog.isOpen());
            });
            Optional<EditorSettings> invalid = showPreferencesDialog(app);

            assertThat(seen).containsExactly(true, false, true, false);
            assertThat(valid).isEmpty();
            assertThat(invalid).isEmpty();
            assertThat(settings(app).get()).isEqualTo(DEFAULTS);
        }
    }

    @Test
    void enterActivatesOkAndEscapeCancel() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                field(dialog).setText("5");
                press(dialog, KeyEvent.VK_ENTER);
                seen.add(dialog.isOpen());

                field(dialog).setText("16");
                press(dialog, KeyEvent.VK_ENTER);
                seen.add(dialog.isOpen());
            });
            Optional<EditorSettings> entered = showPreferencesDialog(app);

            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                field(dialog).setText("18");
                press(dialog, KeyEvent.VK_ESCAPE);
                seen.add(dialog.isOpen());
            });
            Optional<EditorSettings> escaped = showPreferencesDialog(app);

            assertThat(seen).containsExactly(true, false, false);
            assertThat(entered).contains(new EditorSettings(16, true));
            assertThat(escaped).isEmpty();
            assertThat(settings(app).get()).isEqualTo(new EditorSettings(16, true));
        }
    }

    @Test
    void listsApplyAmongTheButtonsWithTheTextsOfTheResources() {
        List<String> buttons = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> mnemonics = new ArrayList<>();
        AtomicReference<Object> defaultAction = new AtomicReference<>();
        AtomicReference<Object> okAction = new AtomicReference<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                List<JButton> found = dialog.components(JButton.class);
                JCheckBox wordWrap = dialog.component(JCheckBox.class);
                JLabel fontSize = dialog.component(JLabel.class);
                buttons.addAll(texts(found));
                labels.add(fontSize.getText());
                labels.add(wordWrap.getText());
                mnemonics.add(found.getLast().getMnemonic());
                mnemonics.add(fontSize.getDisplayedMnemonic());
                mnemonics.add(wordWrap.getMnemonic());
                defaultAction.set(SwingUtilities.getRootPane(field(dialog))
                        .getDefaultButton()
                        .getAction());
                okAction.set(commands(dialog).ok().asAction());
            });

            showPreferencesDialog(app);

            assertThat(buttons).containsExactly("OK", "Cancel", "Apply");
            assertThat(labels).containsExactly("Font size", "Wrap lines");
            assertThat(mnemonics).containsExactly(KeyEvent.VK_A, KeyEvent.VK_F, KeyEvent.VK_W);
            assertThat(defaultAction.get()).isSameAs(okAction.get());
        }
    }

    @Test
    void theViewsApplyTableOverridesTheStandardResourcesKeyByKeyAndKeepsTheStandardHandler() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                JButton apply = dialog.components(JButton.class).getLast();
                // The view's table sets only the tool tip: the text and mnemonic stay the framework's.
                seen.add(apply.getText());
                seen.add(apply.getMnemonic());
                seen.add(apply.getToolTipText());

                field(dialog).setText("16");
                seen.add(commands(dialog).apply().execute());
                seen.add(settings(dialog).get().fontSize());
                seen.add(dialog.isOpen());
                commands(dialog).cancel().execute();
            });

            showPreferencesDialog(app);

            assertThat(seen)
                    .containsExactly(
                            "Apply",
                            KeyEvent.VK_A,
                            "Applies the preferences without closing the dialog",
                            true,
                            16,
                            true);
        }
    }

    @Test
    void takesItsTextsAndThoseOfTheButtonsFromTheFrenchResources() {
        List<String> buttons = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> mnemonics = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class, Locale.FRENCH)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                List<JButton> found = dialog.components(JButton.class);
                JCheckBox wordWrap = dialog.component(JCheckBox.class);
                JLabel fontSize = dialog.component(JLabel.class);
                buttons.addAll(texts(found));
                labels.add(fontSize.getText());
                labels.add(wordWrap.getText());
                mnemonics.add(found.getLast().getMnemonic());
                mnemonics.add(fontSize.getDisplayedMnemonic());
                mnemonics.add(wordWrap.getMnemonic());
                field(dialog, "Taille de police").setText("five");
                commands(dialog).ok().execute();
                labels.add(toolTip(field(dialog, "Taille de police")).orElseThrow());
            });

            showPreferencesDialog(app);

            assertThat(buttons).containsExactly("OK", "Annuler", "Appliquer");
            assertThat(labels).containsExactly("Taille de police", "Renvoi à la ligne", "Un nombre entier");
            assertThat(mnemonics).containsExactly(KeyEvent.VK_A, KeyEvent.VK_T, KeyEvent.VK_R);
        }
    }

    @Test
    void applyExistsInDialogViewsOnly() {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            StandardCommands commands = app.commands(StandardCommands.class);

            assertThatThrownBy(commands::apply)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("apply");
        }
    }

    @Test
    void aDialogThatBindsNothingHasAnEmptyFormValidAndClean() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                Form form = dialog.bean(Form.class);
                seen.add(form.valid().get());
                seen.add(form.dirty().get());
                seen.add(commands(dialog).apply().execute());
                seen.add(commands(dialog).ok().execute());
                seen.add(dialog.isOpen());
            });

            app.commands(StandardCommands.class).about().execute();

            assertThat(seen).containsExactly(true, false, false, true, false);
        }
    }

    @Test
    void preferencesShowsTheHandlersDialogThroughTheUiOfTheViewWhosePreferencesRun() {
        AtomicInteger answers = new AtomicInteger();
        List<Object> nested = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                if (answers.incrementAndGet() == 1) {
                    // Run from the dialog view, preferences opens the next dialog through that view's Ui.
                    nested.add(commands(dialog).preferences().execute());
                    nested.add(dialog.isOpen());
                    nested.add(settings(dialog).get().fontSize());
                    field(dialog).setText("16");
                    commands(dialog).ok().execute();
                } else {
                    field(dialog).setText("20");
                    commands(dialog).ok().execute();
                }
            });

            boolean ran = app.commands(StandardCommands.class).preferences().execute();

            assertThat(ran).isTrue();
            assertThat(app.dialogs().shown()).containsExactly(PreferencesDialog.class, PreferencesDialog.class);
            assertThat(nested).containsExactly(true, true, 20);
            assertThat(settings(app).get().fontSize()).isEqualTo(16);
        }
    }

    @Test
    void preferencesHandsTheHandlerTheUiOfTheViewWhosePreferencesRun() {
        List<Ui> received = new ArrayList<>();
        AtomicReference<Ui> dialogUi = new AtomicReference<>();
        AtomicInteger answers = new AtomicInteger();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.bean(NotesPreferences.class).onShowPreferences(received::add);
            app.dialogs().answerDialog(PreferencesDialog.class, dialog -> {
                if (answers.incrementAndGet() == 1) {
                    dialogUi.set(dialog.bean(Ui.class));
                    commands(dialog).preferences().execute();
                }
                commands(dialog).cancel().execute();
            });

            app.commands(StandardCommands.class).preferences().execute();

            Ui mainUi = app.bean(Ui.class);
            assertThat(received).hasSize(2);
            assertThat(received.get(0)).isSameAs(mainUi);
            assertThat(received.get(1)).isSameAs(dialogUi.get()).isNotSameAs(mainUi);
        }
    }
}
