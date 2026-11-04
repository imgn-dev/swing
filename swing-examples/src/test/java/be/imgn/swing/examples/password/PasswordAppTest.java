package be.imgn.swing.examples.password;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.ui.StandardCommands;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The change of password: the checklist of the new password's requirements in {@link ChangePasswordDialog}, the checks
 * of its three fields, and the change of the {@link Account}'s password by {@link PasswordApp}. The tester keeps
 * FlatLaf's validation feedback, so a field's error is its tool tip text.
 */
class PasswordAppTest {

    private static final String CURRENT = "Current password";
    private static final String NEW = "New password";
    private static final String CONFIRMATION = "Confirmation";
    private static final String LENGTH = "At least 8 characters";
    private static final String DIGIT = "At least one digit";
    private static final String GREEN = "#2e7d32";
    private static final String RED = "#c62828";
    private static final String DEFAULT = "the label's default foreground";

    private static void changePassword(AppTester<PasswordApp> app) {
        app.commands(PasswordAppCommands.class).changePassword().execute();
    }

    private static JPasswordField field(OpenDialog<?> dialog, String name) {
        return dialog.component(JPasswordField.class, name);
    }

    private static Optional<String> error(OpenDialog<?> dialog, String name) {
        return Optional.ofNullable(field(dialog, name).getToolTipText());
    }

    // The labels of the checklist: those of the dialog that label no field.
    // The checklist follows its observables through Ui.bind, whose updates are posted even for a change made on the
    // EDT,
    // so they run once the answer settles.
    private static List<Line> checklist(OpenDialog<?> dialog) {
        dialog.settle();
        Color defaultForeground = new JLabel().getForeground();
        return dialog.components(JLabel.class).stream()
                .filter(label -> label.getLabelFor() == null)
                .map(label -> new Line(label.getText(), color(label.getForeground(), defaultForeground)))
                .toList();
    }

    private static String color(Color foreground, Color defaultForeground) {
        return foreground.equals(defaultForeground) ? DEFAULT : String.format("#%06x", foreground.getRGB() & 0xffffff);
    }

    private static List<Line> typeNewPassword(OpenDialog<?> dialog, String text) {
        field(dialog, NEW).setText(text);
        return checklist(dialog);
    }

    @Test
    void beforeAnythingIsTypedBothRequirementsShowABulletInTheDefaultForeground() {
        List<List<Line>> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> seen.add(checklist(dialog)));

            changePassword(app);

            assertThat(seen)
                    .containsExactly(List.of(new Line("• " + LENGTH, DEFAULT), new Line("• " + DIGIT, DEFAULT)));
        }
    }

    @Test
    void theRequirementsTurnFromUnmetToMetAsTheUserTypes() {
        List<List<Line>> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                seen.add(typeNewPassword(dialog, "abc"));
                seen.add(typeNewPassword(dialog, "abc1"));
                seen.add(typeNewPassword(dialog, "abcdefg1"));
                // Once the whole text is deleted, the requirements are not checked again until the user types.
                seen.add(typeNewPassword(dialog, ""));
            });

            changePassword(app);

            assertThat(seen)
                    .containsExactly(
                            List.of(new Line("✗ " + LENGTH, RED), new Line("✗ " + DIGIT, RED)),
                            List.of(new Line("✗ " + LENGTH, RED), new Line("✓ " + DIGIT, GREEN)),
                            List.of(new Line("✓ " + LENGTH, GREEN), new Line("✓ " + DIGIT, GREEN)),
                            List.of(new Line("• " + LENGTH, DEFAULT), new Line("• " + DIGIT, DEFAULT)));
        }
    }

    @Test
    void changingTheNewPasswordAfterwardsShowsTheConfirmationsErrorWithoutTheConfirmationBeingChanged() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                field(dialog, NEW).setText("abcdefg1");
                field(dialog, CONFIRMATION).setText("abcdefg1");
                seen.add(error(dialog, CONFIRMATION));

                field(dialog, NEW).setText("abcdefg2");
                seen.add(error(dialog, CONFIRMATION));
                seen.add(new String(field(dialog, CONFIRMATION).getPassword()));
            });

            changePassword(app);

            assertThat(seen).containsExactly(Optional.empty(), Optional.of("Matches the new password"), "abcdefg1");
        }
    }

    @Test
    void aWrongCurrentPasswordShowsItsError() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                field(dialog, CURRENT).setText("secret2");
                seen.add(error(dialog, CURRENT));
                field(dialog, CURRENT).setText("secret1");
                seen.add(error(dialog, CURRENT));
            });

            changePassword(app);

            assertThat(seen).containsExactly(Optional.of("Matches your current password"), Optional.empty());
        }
    }

    @Test
    void okWithAnErrorKeepsTheDialogOpenAndFocusesTheFirstFieldWithAnError() {
        List<Object> seen = new ArrayList<>();
        List<@Nullable Component> focused = new ArrayList<>();
        List<JPasswordField> fields = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                fields.add(field(dialog, CURRENT));
                fields.add(field(dialog, NEW));
                StandardCommands commands = dialog.commands(StandardCommands.class);
                field(dialog, CURRENT).setText("wrong");
                field(dialog, NEW).setText("abcdefg1");
                field(dialog, CONFIRMATION).setText("abcdefg1");

                seen.add(commands.ok().execute());
                seen.add(dialog.isOpen());
                focused.add(dialog.focused().orElse(null));

                field(dialog, CURRENT).setText("secret1");
                field(dialog, NEW).setText("abcdefgh");
                seen.add(commands.ok().execute());
                seen.add(dialog.isOpen());
                seen.add(error(dialog, NEW));
                focused.add(dialog.focused().orElse(null));
            });

            changePassword(app);

            assertThat(seen).containsExactly(true, true, true, true, Optional.of(DIGIT));
            assertThat(focused).containsExactly(fields.get(0), fields.get(1));
            assertThat(app.bean(Account.class).password().get()).isEqualTo("secret1");
            assertThat(status(app)).isEmpty();
        }
    }

    @Test
    void okOnAnUntouchedDialogShowsTheErrorOfEveryField() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                dialog.commands(StandardCommands.class).ok().execute();
                seen.add(dialog.isOpen());
                seen.add(error(dialog, CURRENT));
                seen.add(error(dialog, NEW));
                seen.add(error(dialog, CONFIRMATION));
            });

            changePassword(app);

            // The confirmation equals the new password, both empty.
            assertThat(seen)
                    .containsExactly(
                            true,
                            Optional.of("Your current password is required"),
                            Optional.of(LENGTH),
                            Optional.empty());
        }
    }

    @Test
    void okOnAnUntouchedDialogShowsBothRequirementsAsUnmetUntilTheUserTypes() {
        List<List<Line>> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                dialog.commands(StandardCommands.class).ok().execute();
                seen.add(checklist(dialog));
                seen.add(typeNewPassword(dialog, "a"));
                seen.add(typeNewPassword(dialog, ""));
            });

            changePassword(app);

            assertThat(seen)
                    .containsExactly(
                            List.of(new Line("✗ " + LENGTH, RED), new Line("✗ " + DIGIT, RED)),
                            List.of(new Line("✗ " + LENGTH, RED), new Line("✗ " + DIGIT, RED)),
                            List.of(new Line("• " + LENGTH, DEFAULT), new Line("• " + DIGIT, DEFAULT)));
        }
    }

    @Test
    void okWithValidFieldsClosesTheDialogSetsThePasswordAndShowsTheChange() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                field(dialog, CURRENT).setText("secret1");
                field(dialog, NEW).setText("abcdefg1");
                field(dialog, CONFIRMATION).setText("abcdefg1");
                seen.add(dialog.commands(StandardCommands.class).ok().execute());
                seen.add(dialog.isOpen());
            });

            changePassword(app);

            assertThat(seen).containsExactly(true, false);
            assertThat(app.bean(Account.class).password().get()).isEqualTo("abcdefg1");
            assertThat(status(app)).isEqualTo("Your password was changed.");
        }
    }

    @Test
    void theNewPasswordIsTheCurrentOneOfTheNextChange() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                field(dialog, CURRENT).setText("secret1");
                field(dialog, NEW).setText("abcdefg1");
                field(dialog, CONFIRMATION).setText("abcdefg1");
                dialog.commands(StandardCommands.class).ok().execute();
            });
            changePassword(app);

            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                field(dialog, CURRENT).setText("secret1");
                seen.add(error(dialog, CURRENT));
                field(dialog, CURRENT).setText("abcdefg1");
                seen.add(error(dialog, CURRENT));
            });
            changePassword(app);

            assertThat(seen).containsExactly(Optional.of("Matches your current password"), Optional.empty());
        }
    }

    @Test
    void cancelLeavesThePasswordUnchanged() {
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                field(dialog, CURRENT).setText("secret1");
                field(dialog, NEW).setText("abcdefg1");
                field(dialog, CONFIRMATION).setText("abcdefg1");
                dialog.commands(StandardCommands.class).cancel().execute();
            });

            changePassword(app);

            assertThat(app.bean(Account.class).password().get()).isEqualTo("secret1");
            assertThat(status(app)).isEmpty();
        }
    }

    @Test
    void inFrenchTheRequirementsAreInFrench() {
        List<List<String>> seen = new ArrayList<>();
        try (AppTester<PasswordApp> app = AppTester.start(PasswordApp.class, Locale.FRENCH)) {
            app.dialogs().answerDialog(ChangePasswordDialog.class, dialog -> {
                seen.add(texts(checklist(dialog)));
                dialog.component(JPasswordField.class, "Nouveau mot de passe").setText("abc1");
                seen.add(texts(checklist(dialog)));
            });

            changePassword(app);

            assertThat(seen)
                    .containsExactly(
                            List.of("• Au moins 8 caractères", "• Au moins un chiffre"),
                            List.of("✗ Au moins 8 caractères", "✓ Au moins un chiffre"));
        }
    }

    private static List<String> texts(List<Line> lines) {
        return lines.stream().map(Line::text).toList();
    }

    private static String status(AppTester<PasswordApp> app) {
        JLabel status = app.component(JLabel.class);
        return app.call(status::getText);
    }

    /** A line of the checklist as the user sees it. */
    private record Line(String text, String foreground) {}
}
