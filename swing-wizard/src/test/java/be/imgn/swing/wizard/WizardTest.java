package be.imgn.swing.wizard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Locales;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/** A wizard's steps, heading, commands, focus, checks and reveal code, against a dialog view of the tests' own. */
class WizardTest {

    private static Optional<String> show(AppTester<WizardApp> app, Consumer<OpenDialog<StepsDialog>> answer) {
        app.dialogs().answerDialog(StepsDialog.class, answer);
        return app.call(() -> app.bean(Ui.class).showDialog(StepsDialog.class));
    }

    private static String heading(OpenDialog<?> dialog) {
        return dialog.components(JLabel.class).getFirst().getText();
    }

    private static Optional<String> toolTip(JComponent component) {
        return Optional.ofNullable(component.getToolTipText());
    }

    @Test
    void headsEachStepWithItsNumberAndTitle() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            show(app, dialog -> {
                StepsDialog view = dialog.view();
                seen.add(heading(dialog));
                view.nameField().setText("Ada");
                view.wizard().next().execute();
                dialog.settle();
                seen.add(heading(dialog));
                seen.add(view.wizard().step().get());
            });
        }

        assertThat(seen).containsExactly("Step 1 of 3: Identity", "Step 2 of 3: Address", 1);
    }

    @Test
    void headsTheStepsInTheLocaleAndFollowsItsChange() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class, Locale.ENGLISH)) {
            show(app, dialog -> {
                seen.add(heading(dialog));
                dialog.bean(Locales.class).set(Locale.FRENCH);
                dialog.settle();
                seen.add(heading(dialog));
            });
        }

        assertThat(seen).containsExactly("Step 1 of 3: Identity", "Étape 1 sur 3 : Identité");
    }

    @Test
    void enablesPreviousAfterTheFirstStepAndNextBeforeTheLast() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            show(app, dialog -> {
                StepsDialog view = dialog.view();
                Wizard wizard = view.wizard();
                seen.add(wizard.previous().execute());
                view.nameField().setText("Ada");
                wizard.next().execute();
                view.cityField().setText("London");
                wizard.next().execute();
                seen.add(wizard.next().execute());
                seen.add(wizard.step().get());
                seen.add(wizard.previous().execute());
                seen.add(wizard.step().get());
            });
        }

        assertThat(seen).containsExactly(false, false, 2, true, 1);
    }

    @Test
    void staysOnAnInvalidStepShowingItsErrorAndFocusingIt() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            show(app, dialog -> {
                StepsDialog view = dialog.view();
                seen.add(view.wizard().next().execute());
                seen.add(view.wizard().step().get());
                seen.add(toolTip(view.nameField()));
                seen.add(toolTip(view.cityField()));
                seen.add(dialog.focused().filter(view.nameField()::equals).isPresent());
            });
        }

        assertThat(seen).containsExactly(true, 0, Optional.of("A name is required"), Optional.empty(), true);
    }

    @Test
    void focusesTheFirstFieldOfAStepItShows() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            show(app, dialog -> {
                StepsDialog view = dialog.view();
                view.nameField().setText("Ada");
                view.wizard().next().execute();
                seen.add(dialog.focused().filter(view.cityField()::equals).isPresent());
                view.wizard().previous().execute();
                seen.add(dialog.focused().filter(view.nameField()::equals).isPresent());
            });
        }

        assertThat(seen).containsExactly(true, true);
    }

    @Test
    void keepsWhatWasEnteredWhenGoingBack() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            show(app, dialog -> {
                StepsDialog view = dialog.view();
                view.nameField().setText("Ada");
                view.wizard().next().execute();
                view.cityField().setText("London");
                view.wizard().previous().execute();
                view.wizard().next().execute();
                seen.add(view.nameField().getText());
                seen.add(view.cityField().getText());
            });
        }

        assertThat(seen).containsExactly("Ada", "London");
    }

    @Test
    void finishesFromAnyStepAndShowsTheStepOfTheFirstError() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            Optional<String> result = show(app, dialog -> {
                StepsDialog view = dialog.view();
                view.nameField().setText("Ada");
                StandardCommands commands = dialog.commands(StandardCommands.class);
                commands.ok().execute();
                seen.add(dialog.isOpen());
                seen.add(view.wizard().step().get());
                seen.add(dialog.focused().filter(view.cityField()::equals).isPresent());
                view.cityField().setText("London");
                commands.ok().execute();
            });

            assertThat(result).contains("Ada, London");
        }

        assertThat(seen).containsExactly(true, 1, true);
    }

    @Test
    void showsPreviousNextOkAndCancelWithTheTextsOfTheModuleAndTheView() {
        List<String> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            show(
                    app,
                    dialog -> dialog.components(JButton.class).stream()
                            .map(AbstractButton::getText)
                            .forEach(seen::add));
        }

        assertThat(seen).containsExactly("< Previous", "Forward >", "OK", "Cancel");
    }

    @Test
    void namesTheCommandsInFrench() {
        List<String> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class, Locale.FRENCH)) {
            show(app, dialog -> {
                seen.add(dialog.view()
                        .wizard()
                        .previous()
                        .asAction()
                        .getValue(Action.NAME)
                        .toString());
                seen.add(dialog.view()
                        .wizard()
                        .next()
                        .asAction()
                        .getValue(Action.NAME)
                        .toString());
            });
        }

        assertThat(seen).containsExactly("< Précédent", "En avant >");
    }

    @Test
    void checksTheAccessibilityOfAStepTheFirstTimeItShows() {
        List<String> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            app.dialogs().answerDialog(UnnamedStepDialog.class, dialog -> {
                seen.add("open");
                dialog.view()
                        .buttons(dialog.commands(StandardCommands.class))
                        .get(1)
                        .execute();
                seen.add("shown");
            });

            assertThatThrownBy(() -> app.run(() -> app.bean(Ui.class).showDialog(UnnamedStepDialog.class)))
                    .hasMessageContaining("of be.imgn.swing.wizard.UnnamedStepDialog has no accessible name");
            // The build passed: the step was hidden then; showing it failed.
            assertThat(seen).containsExactly("open", "shown");
        }
    }

    @Test
    void rejectsStepsGivenWrongly() {
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            Misuse misuse = app.bean(Misuse.class);

            app.run(() -> misuse.kind(Misuse.Kind.NONE));
            assertThatThrownBy(() -> app.run(() -> app.bean(Ui.class).showDialog(MisusedDialog.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one step");
            app.run(() -> misuse.kind(Misuse.Kind.TWICE));
            assertThatThrownBy(() -> app.run(() -> app.bean(Ui.class).showDialog(MisusedDialog.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("given once");
            app.run(() -> misuse.kind(Misuse.Kind.DUPLICATE));
            assertThatThrownBy(() -> app.run(() -> app.bean(Ui.class).showDialog(MisusedDialog.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("given twice");
            app.run(() -> misuse.kind(Misuse.Kind.UNEXPOSED));
            assertThatThrownBy(() -> app.run(() -> app.bean(Ui.class).showDialog(MisusedDialog.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No @Exposed component field");
        }
    }

    @Test
    void rejectsStepsGivenAfterTheBuild() {
        List<Throwable> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            show(app, dialog -> {
                try {
                    dialog.view().wizard().steps(dialog.view().note);
                } catch (IllegalStateException expected) {
                    seen.add(expected);
                }
            });
        }

        assertThat(seen).singleElement().satisfies(error -> assertThat(error).hasMessageContaining("during the build"));
    }

    @Test
    void rejectsStepsInAFrameView() {
        assertThatThrownBy(() -> AppTester.start(FrameWizardApp.class).close())
                .isInstanceOf(AssertionError.class)
                .rootCause()
                .hasMessageContaining("during the build of the dialog view");
    }

    @Test
    void focusesAComboBoxThatStartsAStep() {
        List<Boolean> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            app.dialogs().answerDialog(ComboStepDialog.class, dialog -> {
                dialog.view()
                        .buttons(dialog.commands(StandardCommands.class))
                        .get(1)
                        .execute();
                seen.add(dialog.focused().filter(dialog.view().colors()::equals).isPresent());
            });
            app.run(() -> app.bean(Ui.class).showDialog(ComboStepDialog.class));
        }

        assertThat(seen).containsExactly(true);
    }

    @Test
    void sizesTheStepsAsTheLargestOfThem() {
        List<Dimension> seen = new ArrayList<>();
        try (AppTester<WizardApp> app = AppTester.start(WizardApp.class)) {
            show(app, dialog -> {
                StepsDialog view = dialog.view();
                seen.add(view.identity.getParent().getPreferredSize());
                seen.add(new Dimension(
                        Stream.of(view.identity, view.address, view.note)
                                .mapToInt(step -> step.getPreferredSize().width)
                                .max()
                                .orElseThrow(),
                        Stream.of(view.identity, view.address, view.note)
                                .mapToInt(step -> step.getPreferredSize().height)
                                .max()
                                .orElseThrow()));
            });
        }

        assertThat(seen.get(0)).isEqualTo(seen.get(1));
    }
}
