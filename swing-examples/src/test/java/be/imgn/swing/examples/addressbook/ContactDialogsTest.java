package be.imgn.swing.examples.addressbook;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.StandardCommands;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/**
 * The forms of the three contact dialogs: their validation, the Apply of the tabs dialog, and the labels of their
 * address panels, which come from nested tables of each dialog's resources.
 */
class ContactDialogsTest {

    @Test
    void anEmptyFirstNameShowsItsErrorAndKeepsTheDialogOpenOnOk() {
        var seen = new ArrayList<Object>();
        try (var app = AppTester.start(AddressBookApp.class)) {
            app.dialogs().answerDialog(ContactDialog.class, dialog -> {
                var firstName = dialog.component(JTextField.class, "First Name");
                dialog.component(JTextField.class, "Last Name").setText("Lovelace");
                seen.add(Optional.ofNullable(firstName.getToolTipText()));

                seen.add(ok(dialog).execute());
                seen.add(dialog.isOpen());
                seen.add(Optional.ofNullable(firstName.getToolTipText()));
                seen.add(dialog.focused().filter(firstName::equals).isPresent());
            });

            app.commands(ContactActionsCommands.class).createContact().execute();

            // Not shown before the user changes the field or OK validates the form.
            assertThat(seen)
                    .containsExactly(Optional.empty(), true, true, Optional.of("A first name is required"), true);
            // The answer left the dialog open: it was cancelled.
            assertThat(names(app)).hasSize(5);
        }
    }

    @Test
    void anInvalidBirthDateShowsItsErrorAndKeepsTheDialogOpenOnOk() {
        var seen = new ArrayList<Object>();
        try (var app = AppTester.start(AddressBookApp.class)) {
            app.dialogs().answerDialog(ContactDialog.class, dialog -> {
                dialog.component(JTextField.class, "First Name").setText("Ada");
                dialog.component(JTextField.class, "Last Name").setText("Lovelace");
                var birth = dialog.component(JTextField.class, "Birth Date");
                birth.setText("1815-13-10");
                seen.add(Optional.ofNullable(birth.getToolTipText()));

                seen.add(ok(dialog).execute());
                seen.add(dialog.isOpen());

                birth.setText("");
                seen.add(Optional.ofNullable(birth.getToolTipText()));
                seen.add(ok(dialog).execute());
                seen.add(dialog.isOpen());
            });

            app.commands(ContactActionsCommands.class).createContact().execute();

            // An empty birth date is no date, which the contact may have.
            assertThat(seen)
                    .containsExactly(Optional.of("A date as YYYY-MM-DD"), true, true, Optional.empty(), true, false);
            assertThat(names(app)).endsWith("Ada Lovelace");
            assertThat(app.bean(AddressBookService.class).contacts().get(5).birthDate())
                    .isEmpty();
        }
    }

    @Test
    void aLastNameIsRequiredInEveryDialog() {
        var seen = new ArrayList<Object>();
        try (var app = AppTester.start(AddressBookApp.class)) {
            var contacts = app.commands(ContactActionsCommands.class);
            answerEachDialog(app, dialog -> {
                dialog.component(JTextField.class, "First Name").setText("Grace");
                seen.add(ok(dialog).execute());
                seen.add(dialog.isOpen());
                seen.add(dialog.component(JTextField.class, "Last Name").getToolTipText());
            });

            contacts.createContact().execute();
            contacts.createContactWithTabs().execute();
            contacts.createContactWithWizard().execute();

            assertThat(seen)
                    .containsExactly(
                            true,
                            true,
                            "A last name is required",
                            true,
                            true,
                            "A last name is required",
                            true,
                            true,
                            "A last name is required");
            assertThat(names(app)).hasSize(5);
        }
    }

    @Test
    void theTabsDialogHasApplyOnlyWhenItModifiesAContact() {
        var buttons = new ArrayList<List<String>>();
        try (var app = AppTester.start(AddressBookApp.class)) {
            var contacts = app.commands(ContactActionsCommands.class);
            answerEachDialog(app, dialog -> buttons.add(buttonTexts(dialog)));

            contacts.createContactWithTabs().execute();
            select(app, 0);
            contacts.modifyContactWithTabs().execute();
            contacts.modifyContact().execute();
            contacts.modifyContactWithWizard().execute();

            assertThat(buttons)
                    .containsExactly(
                            List.of("Save", "Cancel"),
                            List.of("Save", "Cancel", "Apply"),
                            List.of("Save", "Cancel"),
                            List.of("< Previous", "Next >", "Save", "Cancel"));
        }
    }

    @Test
    void applyModifiesTheContactWhileTheDialogStaysOpen() {
        var seen = new ArrayList<Object>();
        try (var app = AppTester.start(AddressBookApp.class)) {
            var service = app.bean(AddressBookService.class);
            select(app, 3);
            app.dialogs().answerDialog(ContactTabsDialog.class, dialog -> {
                dialog.component(JTextField.class, "First Name").setText("Belinda");
                seen.add(apply(dialog).execute());
                seen.add(dialog.isOpen());
                seen.add(service.contacts().get(3).firstName());
                cancel(dialog).execute();
            });

            app.commands(ContactActionsCommands.class).modifyContactWithTabs().execute();

            assertThat(seen).containsExactly(true, true, "Belinda");
            // Cancel keeps what Apply committed.
            assertThat(names(app)).contains("Belinda Doe").doesNotContain("Linda Doe");
            assertThat(app.application().table.getSelectedRow()).isEqualTo(3);
        }
    }

    @Test
    void applyIsEnabledOnlyWhileAValueDiffersFromTheLastAppliedOne() {
        var seen = new ArrayList<Object>();
        try (var app = AppTester.start(AddressBookApp.class)) {
            var service = app.bean(AddressBookService.class);
            select(app, 3);
            app.dialogs().answerDialog(ContactTabsDialog.class, dialog -> {
                var firstName = dialog.component(JTextField.class, "First Name");
                var city = dialog.components(JTextField.class).stream()
                        .filter(field -> field.getText().equals("San Francisco"))
                        .findFirst()
                        .orElseThrow();
                // At each step, whether Apply's button is enabled, then whether the command executed.
                seen.add(applyEnabled(dialog));
                seen.add(apply(dialog).execute());

                firstName.setText("Belinda");
                firstName.setText("Linda");
                // Edited back to the value it was opened with.
                seen.add(applyEnabled(dialog));
                seen.add(apply(dialog).execute());

                city.setText("Oakland");
                seen.add(applyEnabled(dialog));
                seen.add(apply(dialog).execute());
                seen.add(service.contacts().get(3).home().city());
                // Clean once applied.
                seen.add(applyEnabled(dialog));
                seen.add(apply(dialog).execute());

                city.setText("San Francisco");
                city.setText("Oakland");
                // Edited back to the value last applied, not the one it was opened with.
                seen.add(applyEnabled(dialog));
                seen.add(apply(dialog).execute());

                city.setText("San Francisco");
                seen.add(applyEnabled(dialog));
                seen.add(apply(dialog).execute());
                seen.add(dialog.isOpen());
                cancel(dialog).execute();
            });

            app.commands(ContactActionsCommands.class).modifyContactWithTabs().execute();

            assertThat(seen)
                    .containsExactly(
                            false, false, false, false, true, true, "Oakland", false, false, false, false, true, true,
                            true);
            assertThat(service.contacts().get(3).home().city()).isEqualTo("San Francisco");
        }
    }

    @Test
    void theAddressPanelsShowTheLabelsOfTheirDialogsResources() {
        var labels = new ArrayList<List<String>>();
        try (var app = AppTester.start(AddressBookApp.class)) {
            var contacts = app.commands(ContactActionsCommands.class);
            app.dialogs().answerDialog(ContactDialog.class, dialog -> {
                labels.add(texts(dialog.view().home));
                labels.add(texts(dialog.view().office));
            });
            app.dialogs().answerDialog(ContactTabsDialog.class, dialog -> {
                labels.add(texts(dialog.view().home));
                labels.add(texts(dialog.view().office));
            });
            app.dialogs().answerDialog(ContactWizardDialog.class, dialog -> {
                labels.add(texts(dialog.view().home));
                labels.add(texts(dialog.view().office));
            });

            contacts.createContact().execute();
            contacts.createContactWithTabs().execute();
            contacts.createContactWithWizard().execute();

            var texts = List.of("Street", "Zip code", "City", "Phone");
            assertThat(labels).hasSize(6).containsOnly(texts);
        }
    }

    @Test
    void theAddressPanelsAndTheOkButtonShowTheTextsOfTheirDialogsFrenchResources() {
        var labels = new ArrayList<List<String>>();
        var oks = new ArrayList<List<Object>>();
        try (var app = AppTester.start(AddressBookApp.class, Locale.FRENCH)) {
            var contacts = app.commands(ContactActionsCommands.class);
            answerEachDialogWithPanels(app, (dialog, home, office) -> {
                labels.add(texts(home));
                labels.add(texts(office));
                var ok = okButton(dialog);
                oks.add(List.of(ok.getText(), ok.getMnemonic()));
            });

            contacts.createContact().execute();
            contacts.createContactWithTabs().execute();
            contacts.createContactWithWizard().execute();

            var texts = List.of("Rue", "Code postal", "Ville", "Téléphone");
            assertThat(labels).hasSize(6).containsOnly(texts);
            var save = List.<Object>of("Enregistrer", KeyEvent.VK_E);
            assertThat(oks).containsExactly(save, save, save);
        }
    }

    @Test
    void theHomePanelsLabelsHaveMnemonicsAndTheOfficePanelsNone() {
        var none = List.of(0, 0, 0, 0);
        var english = List.of(KeyEvent.VK_T, KeyEvent.VK_Z, KeyEvent.VK_C, KeyEvent.VK_P);
        var french = List.of(KeyEvent.VK_R, KeyEvent.VK_O, KeyEvent.VK_V, KeyEvent.VK_T);

        assertThat(panelMnemonics(Locale.ENGLISH)).containsExactly(english, none, english, none, english, none);
        assertThat(panelMnemonics(Locale.FRENCH)).containsExactly(french, none, french, none, french, none);
    }

    @Test
    void noMnemonicIsUsedTwiceInADialog() {
        for (var locale : List.of(Locale.ENGLISH, Locale.FRENCH)) {
            var mnemonics = new ArrayList<List<Integer>>();
            try (var app = AppTester.start(AddressBookApp.class, locale)) {
                var contacts = app.commands(ContactActionsCommands.class);
                answerEachDialog(
                        app,
                        dialog -> mnemonics.add(Stream.concat(
                                        dialog.components(JLabel.class).stream().map(JLabel::getDisplayedMnemonic),
                                        dialog.components(JButton.class).stream()
                                                .map(JButton::getMnemonic))
                                .filter(mnemonic -> mnemonic != 0)
                                .toList()));

                select(app, 0);
                contacts.modifyContact().execute();
                contacts.modifyContactWithTabs().execute();
                contacts.modifyContactWithWizard().execute();
            }

            assertThat(mnemonics)
                    .as("in %s", locale)
                    .hasSize(3)
                    .allSatisfy(used -> assertThat(used)
                            .doesNotHaveDuplicates()
                            // The three labels of the contact, the four of the home panel, and Save.
                            .hasSizeGreaterThanOrEqualTo(8));
        }
    }

    // The mnemonics of the home then the office panel of each dialog, in order.
    private static List<List<Integer>> panelMnemonics(Locale locale) {
        var mnemonics = new ArrayList<List<Integer>>();
        try (var app = AppTester.start(AddressBookApp.class, locale)) {
            var contacts = app.commands(ContactActionsCommands.class);
            answerEachDialogWithPanels(app, (dialog, home, office) -> {
                mnemonics.add(mnemonics(home));
                mnemonics.add(mnemonics(office));
            });

            contacts.createContact().execute();
            contacts.createContactWithTabs().execute();
            contacts.createContactWithWizard().execute();
        }
        return mnemonics;
    }

    private static void answerEachDialog(AppTester<AddressBookApp> app, Consumer<OpenDialog<?>> answer) {
        app.dialogs().answerDialog(ContactDialog.class, answer::accept);
        app.dialogs().answerDialog(ContactTabsDialog.class, answer::accept);
        app.dialogs().answerDialog(ContactWizardDialog.class, answer::accept);
    }

    private static void answerEachDialogWithPanels(AppTester<AddressBookApp> app, PanelsAnswer answer) {
        app.dialogs()
                .answerDialog(
                        ContactDialog.class, dialog -> answer.accept(dialog, dialog.view().home, dialog.view().office));
        app.dialogs()
                .answerDialog(
                        ContactTabsDialog.class,
                        dialog -> answer.accept(dialog, dialog.view().home, dialog.view().office));
        app.dialogs()
                .answerDialog(
                        ContactWizardDialog.class,
                        dialog -> answer.accept(dialog, dialog.view().home, dialog.view().office));
    }

    private static List<String> texts(AddressPanel panel) {
        return labels(panel, JLabel::getText);
    }

    private static List<Integer> mnemonics(AddressPanel panel) {
        return labels(panel, JLabel::getDisplayedMnemonic);
    }

    private static <T> List<T> labels(AddressPanel panel, Function<JLabel, T> property) {
        return Stream.of(panel.streetLabel, panel.zipLabel, panel.cityLabel, panel.phoneLabel)
                .map(property)
                .toList();
    }

    private static List<String> buttonTexts(OpenDialog<?> dialog) {
        return dialog.components(JButton.class).stream().map(JButton::getText).toList();
    }

    private static JButton okButton(OpenDialog<?> dialog) {
        var action = ok(dialog).asAction();
        return dialog.components(JButton.class).stream()
                .filter(button -> action.equals(button.getAction()))
                .findFirst()
                .orElseThrow();
    }

    private static BoundCommand ok(OpenDialog<?> dialog) {
        return dialog.commands(StandardCommands.class).ok();
    }

    private static BoundCommand apply(OpenDialog<?> dialog) {
        return dialog.commands(StandardCommands.class).apply();
    }

    // Whether the action of Apply's button is enabled, once the answer settles: the action follows the command's
    // enabled
    // state through posted updates.
    private static boolean applyEnabled(OpenDialog<?> dialog) {
        dialog.settle();
        return apply(dialog).asAction().isEnabled();
    }

    private static BoundCommand cancel(OpenDialog<?> dialog) {
        return dialog.commands(StandardCommands.class).cancel();
    }

    private static void select(AppTester<AddressBookApp> app, int row) {
        var table = app.application().table;
        app.run(() -> table.setRowSelectionInterval(row, row));
    }

    private static List<String> names(AppTester<AddressBookApp> app) {
        var table = app.application().table;
        return app.call(() -> {
            var names = new ArrayList<String>();
            for (var row = 0; row < table.getRowCount(); row++) {
                names.add(table.getValueAt(row, 0) + " " + table.getValueAt(row, 1));
            }
            return names;
        });
    }

    @FunctionalInterface
    private interface PanelsAnswer {
        void accept(OpenDialog<?> dialog, AddressPanel home, AddressPanel office);
    }
}
