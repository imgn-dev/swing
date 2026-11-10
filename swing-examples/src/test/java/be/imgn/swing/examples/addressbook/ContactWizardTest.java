package be.imgn.swing.examples.addressbook;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.Locales;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.wizard.Wizard;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The contact wizard, on swing-wizard: its steps, heading, commands, focus and Finish. */
class ContactWizardTest {

    private static void create(AppTester<AddressBookApp> app, Consumer<OpenDialog<ContactWizardDialog>> answer) {
        app.dialogs().answerDialog(ContactWizardDialog.class, answer);
        app.commands(ContactActionsCommands.class).createContactWithWizard().execute();
    }

    private static String heading(OpenDialog<?> dialog) {
        return dialog.components(JLabel.class).getFirst().getText();
    }

    private static JTextField field(OpenDialog<?> dialog, String name) {
        return dialog.component(JTextField.class, name);
    }

    @Test
    void headsEachStepWithItsTitle() {
        List<String> seen = new ArrayList<>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            create(app, dialog -> {
                seen.add(heading(dialog));
                field(dialog, "First Name").setText("Grace");
                field(dialog, "Last Name").setText("Hopper");
                dialog.bean(Wizard.class).next().execute();
                dialog.settle();
                seen.add(heading(dialog));
            });
        }

        assertThat(seen).containsExactly("Step 1 of 3: Contact Information", "Step 2 of 3: Home Address");
    }

    @Test
    void headsTheStepsInFrenchAndFollowsAChangeOfTheLocale() {
        List<String> seen = new ArrayList<>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.FRENCH)) {
            create(app, dialog -> {
                seen.add(heading(dialog));
                dialog.bean(Locales.class).set(Locale.ENGLISH);
                dialog.settle();
                seen.add(heading(dialog));
            });
        }

        assertThat(seen).containsExactly("Étape 1 sur 3 : Information générale", "Step 1 of 3: Contact Information");
    }

    @Test
    void disablesPreviousOnTheFirstStepAndNextOnTheLast() {
        List<Boolean> seen = new ArrayList<>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            create(app, dialog -> {
                Wizard wizard = dialog.bean(Wizard.class);
                seen.add(wizard.previous().execute());
                field(dialog, "First Name").setText("Grace");
                field(dialog, "Last Name").setText("Hopper");
                wizard.next().execute();
                wizard.next().execute();
                seen.add(wizard.next().execute());
            });
        }

        assertThat(seen).containsExactly(false, false);
    }

    @Test
    void staysOnTheFirstStepWithAnEmptyFirstNameShowingItsError() {
        List<Object> seen = new ArrayList<>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            create(app, dialog -> {
                Wizard wizard = dialog.bean(Wizard.class);
                field(dialog, "Last Name").setText("Hopper");
                wizard.next().execute();
                seen.add(wizard.step().get());
                seen.add(Optional.ofNullable(field(dialog, "First Name").getToolTipText()));
            });
        }

        assertThat(seen).containsExactly(0, Optional.of("A first name is required"));
    }

    @Test
    void focusesTheFirstFieldOfAStepItShows() {
        List<Boolean> seen = new ArrayList<>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            create(app, dialog -> {
                field(dialog, "First Name").setText("Grace");
                field(dialog, "Last Name").setText("Hopper");
                dialog.bean(Wizard.class).next().execute();
                AddressPanel home = dialog.view().home;
                seen.add(dialog.focused()
                        .filter(focused -> SwingUtilities.isDescendingFrom(focused, home))
                        .isPresent());
                seen.add(dialog.focused().filter(JTextField.class::isInstance).isPresent());
            });
        }

        assertThat(seen).containsExactly(true, true);
    }

    @Test
    void keepsWhatWasEnteredWhenGoingBack() {
        List<String> seen = new ArrayList<>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            create(app, dialog -> {
                Wizard wizard = dialog.bean(Wizard.class);
                field(dialog, "First Name").setText("Grace");
                field(dialog, "Last Name").setText("Hopper");
                wizard.next().execute();
                wizard.previous().execute();
                seen.add(field(dialog, "First Name").getText());
                seen.add(field(dialog, "Last Name").getText());
            });
        }

        assertThat(seen).containsExactly("Grace", "Hopper");
    }

    @Test
    void createsTheContactFromTheFirstStepWhenTheFormIsValid() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            create(app, dialog -> {
                field(dialog, "First Name").setText("Grace");
                field(dialog, "Last Name").setText("Hopper");
                dialog.commands(StandardCommands.class).ok().execute();
            });

            JTable table = AddressBookViews.table(app);
            assertThat(app.call(table::getRowCount)).isEqualTo(6);
            assertThat(app.call(() -> table.getValueAt(5, 1))).isEqualTo("Hopper");
        }
    }

    @Test
    void endsTheModificationOfAContactFromAnyStep() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            JTable table = AddressBookViews.table(app);
            app.run(() -> table.setRowSelectionInterval(0, 0));
            app.dialogs().answerDialog(ContactWizardDialog.class, dialog -> {
                Wizard wizard = dialog.bean(Wizard.class);
                field(dialog, "Last Name").setText("Lovelace-King");
                wizard.next().execute();
                wizard.next().execute();
                dialog.commands(StandardCommands.class).ok().execute();
            });

            app.commands(ContactActionsCommands.class).modifyContactWithWizard().execute();

            assertThat(app.call(() -> table.getValueAt(0, 1))).isEqualTo("Lovelace-King");
        }
    }

    @Test
    void namesPreviousAndNextWithTheTextsOfItsResources() {
        List<Integer> seen = new ArrayList<>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            create(app, dialog -> {
                Wizard wizard = dialog.bean(Wizard.class);
                seen.add((Integer) wizard.previous().asAction().getValue(Action.MNEMONIC_KEY));
                seen.add((Integer) wizard.next().asAction().getValue(Action.MNEMONIC_KEY));
            });
        }

        // The dialog is created through its factory, after the wizard's commands are bound: its own texts apply all the
        // same, "< P&revious" rather than swing-wizard's "< &Previous".
        assertThat(seen).containsExactly(KeyEvent.VK_R, KeyEvent.VK_N);
    }
}
