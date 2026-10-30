package be.imgn.swing.examples.addressbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.ui.StandardCommands;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import org.junit.jupiter.api.Test;

class AddressBookAppTest {

    @Test
    void showsTheContactsAndTheirMenus() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            assertThat(app.title()).isEqualTo("Simple Address Book Application");
            assertThat(app.components(JMenu.class))
                    .extracting(JMenu::getText)
                    .containsExactly("File", "Edit", "Contacts", "Tasks Tests");
            assertThat(app.components(JMenuItem.class))
                    .filteredOn(item -> !(item instanceof JMenu))
                    .extracting(JMenuItem::getText)
                    .startsWith("Throw Exception", "Exit", "Cut", "Copy", "Paste", "Create Contact...");
            assertThat(names(app)).containsExactly("John Smith", "Phoebe Smith", "Luke Smith", "Linda Doe", "Mary Doe");
            assertThat(app.component(JLabel.class, "Contact picture").getText()).isEqualTo("No contact selected");
            ContactActionsCommands contacts = app.commands(ContactActionsCommands.class);
            assertThat(contacts.modifyContact().asAction().isEnabled()).isFalse();
            assertThat(contacts.deleteContact().asAction().isEnabled()).isFalse();
        }
    }

    @Test
    void showsTheSelectedContact() throws Exception {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            AddressBookApp book = app.application();

            JLabel picture = app.component(JLabel.class, "Contact picture");
            select(book, 0);

            assertThat(app.component(JTextField.class, "First Name").getText()).isEqualTo("John");
            assertThat(app.component(JTextField.class, "Last Name").getText()).isEqualTo("Smith");
            assertThat(app.component(JTextField.class, "Birth Date").getText()).isEqualTo("1966-09-24");
            assertThat(picture.getIcon()).isNotNull();
            assertThat(picture.getText()).isEmpty();
            assertThat(app.commands(ContactActionsCommands.class)
                            .modifyContact()
                            .asAction()
                            .isEnabled())
                    .isTrue();

            select(book, 4);

            assertThat(app.component(JTextField.class, "First Name").getText()).isEqualTo("Mary");
            assertThat(picture.getIcon()).isNull();
            assertThat(picture.getText()).isEqualTo("No picture for this contact");
        }
    }

    @Test
    void createsAContactWithTheFormDialog() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            app.dialogs().answerDialog(ContactDialog.class, dialog -> {
                ContactDialog view = dialog.view();
                assertThat(view.firstNameLabel.getText()).isEqualTo("First Name");
                assertThat(((TitledBorder) view.home.getBorder()).getTitle()).isEqualTo("Home Address");
                JTextField firstName = dialog.component(JTextField.class, "First Name");
                assertThat(SwingUtilities.getRootPane(firstName)
                                .getDefaultButton()
                                .getAction())
                        .isSameAs(dialog.commands(StandardCommands.class).ok().asAction());
                firstName.setText("Ada");
                dialog.component(JTextField.class, "Last Name").setText("Lovelace");
                dialog.component(JTextField.class, "Birth Date").setText("1815-12-10");
                ok(dialog);
            });

            app.commands(ContactActionsCommands.class).createContact().execute();

            assertThat(names(app)).endsWith("Ada Lovelace");
            assertThat(app.dialogs().shown()).containsExactly(ContactDialog.class);
        }
    }

    @Test
    void modifiesAContactWithTheTabsDialog() throws Exception {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            AddressBookApp book = app.application();
            select(book, 3);
            app.dialogs().answerDialog(ContactTabsDialog.class, dialog -> {
                JTabbedPane tabs = dialog.component(JTabbedPane.class);
                assertThat(IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt))
                        .containsExactly("General", "Home Address", "Office Address");
                JTextField firstName = dialog.component(JTextField.class, "First Name");
                assertThat(firstName.getText()).isEqualTo("Linda");
                firstName.setText("Belinda");
                ok(dialog);
            });

            app.commands(ContactActionsCommands.class).modifyContactWithTabs().execute();

            assertThat(names(app)).contains("Belinda Doe").doesNotContain("Linda Doe");
            assertThat(app.component(JTextField.class, "First Name").getText()).isEqualTo("Belinda");
        }
    }

    @Test
    void cancellingADialogChangesNothing() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            app.dialogs().answerDialog(ContactTabsDialog.class, dialog -> {
                dialog.component(JTextField.class, "First Name").setText("Nobody");
                dialog.commands(StandardCommands.class).cancel().execute();
            });

            app.commands(ContactActionsCommands.class).createContactWithTabs().execute();

            assertThat(names(app)).hasSize(5);
        }
    }

    @Test
    void createsAContactStepByStepWithTheWizard() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            app.dialogs().answerDialog(ContactWizardDialog.class, dialog -> {
                ContactWizardDialogCommands steps = dialog.commands(ContactWizardDialogCommands.class);
                assertThat(dialog.components(JLabel.class))
                        .extracting(JLabel::getText)
                        .contains("Step 1 of 3: Contact Information");
                assertThat(steps.previous().execute()).isFalse();
                dialog.component(JTextField.class, "First Name").setText("Grace");
                dialog.component(JTextField.class, "Last Name").setText("Hopper");
                steps.next().execute();
                dialog.component(AddressPanel.class, "Home Address").setAddress(Address.ofCity("Arlington"));
                steps.next().execute();
                assertThat(steps.next().execute()).isFalse();
                assertThat(steps.previous().execute()).isTrue();
                ok(dialog);
            });

            app.commands(ContactActionsCommands.class).createContactWithWizard().execute();

            assertThat(app.application().table.getValueAt(5, 2)).isEqualTo("Arlington");
            assertThat(names(app)).endsWith("Grace Hopper");
        }
    }

    @Test
    void deletesTheSelectedContactOnceConfirmed() throws Exception {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            AddressBookApp book = app.application();
            ContactActionsCommands contacts = app.commands(ContactActionsCommands.class);
            select(book, 1);
            app.dialogs().answerConfirm(false);

            contacts.deleteContact().execute();

            assertThat(names(app)).hasSize(5);
            assertThat(app.dialogs().informed()).isEmpty();

            app.dialogs().answerConfirm(true);
            contacts.deleteContact().execute();

            assertThat(names(app)).doesNotContain("Phoebe Smith").hasSize(4);
            assertThat(app.dialogs().confirmed())
                    .containsExactly(
                            "Are you sure you want to delete contact \"Phoebe Smith\"?",
                            "Are you sure you want to delete contact \"Phoebe Smith\"?");
            assertThat(app.dialogs().informed()).containsExactly("Contact has been removed!");
            assertThat(contacts.deleteContact().asAction().isEnabled()).isFalse();
            assertThat(app.component(JTextField.class, "First Name").getText()).isEmpty();
        }
    }

    @Test
    void throwsItsException() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            assertThatThrownBy(() -> app.commands(GeneralActionsCommands.class)
                            .throwException()
                            .execute())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Some message here");
        }
    }

    @Test
    void runsTheTasksOfEachKindOfBlocking() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            TaskTestActionsCommands tasks = app.commands(TaskTestActionsCommands.class);
            TaskTestActions actions = app.commands(TaskTestActions.class);
            actions.chunk(Duration.ofMillis(1));

            List.<Supplier<Boolean>>of(
                            tasks.oneTaskNoBlocker()::execute,
                            tasks.oneTaskActionBlocker()::execute,
                            tasks.oneTaskComponentBlocker()::execute,
                            tasks.oneTaskWindowBlocker()::execute,
                            tasks.oneTaskDialogBlocker()::execute,
                            tasks.oneTaskProgressDialogBlocker()::execute,
                            tasks.oneTaskSerialExecutor()::execute)
                    .forEach(command -> assertThat(command.get()).isTrue());

            assertThat(actions.log())
                    .filteredOn(line -> line.endsWith("finished"))
                    .containsExactly(
                            "Task `oneTaskNoBlocker` finished",
                            "Task `oneTaskActionBlocker` finished",
                            "Task `oneTaskComponentBlocker` finished",
                            "Task `oneTaskWindowBlocker` finished",
                            "Task `oneTaskDialogBlocker` finished",
                            "Task `oneTaskProgressDialogBlocker` finished",
                            "Task `oneTaskSerialExecutor` finished");
        }
    }

    @Test
    void runsGroupsOfTasksAndTasksThatStartOthers() throws InterruptedException {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            TaskTestActionsCommands tasks = app.commands(TaskTestActionsCommands.class);
            TaskTestActions actions = app.commands(TaskTestActions.class);
            actions.chunk(Duration.ofMillis(1));

            tasks.fiveTasksDialogBlocker().execute();
            tasks.twoSerialTaskDialogBlocker().execute();
            tasks.twoSerialGroupsDialogBlocker().execute();

            awaitFinished(actions, 9);
            assertThat(actions.log())
                    .filteredOn(line -> line.endsWith("finished"))
                    .contains(
                            "Task `fiveTasksDialogBlocker #4` finished",
                            "Task `twoSerialTaskDialogBlocker #2` finished",
                            "Task `twoSerialGroupsDialogBlocker #2` finished");
            List<String> log = actions.log();
            assertThat(log.indexOf("Task `twoSerialTaskDialogBlocker #1` finished"))
                    .isLessThan(log.indexOf("Task `twoSerialTaskDialogBlocker #2` just started"));
        }
    }

    @Test
    void coversADialogOpenedWhileATaskBlocksTheApplication() throws Exception {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            TaskTestActionsCommands tasks = app.commands(TaskTestActionsCommands.class);
            ContactActionsCommands contacts = app.commands(ContactActionsCommands.class);
            app.commands(TaskTestActions.class).chunk(Duration.ofMillis(100));
            AtomicReference<Component> glassPane = new AtomicReference<>();
            app.dialogs().answerDialog(ContactDialog.class, dialog -> {
                glassPane.set(SwingUtilities.getRootPane(dialog.component(JTextField.class, "First Name"))
                        .getGlassPane());
                dialog.commands(StandardCommands.class).cancel().execute();
            });

            EventQueue.invokeAndWait(() -> tasks.oneTaskDialogBlocker()
                    .asAction()
                    .actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "test")));
            EventQueue.invokeAndWait(() -> contacts.createContact().execute());

            assertThat(glassPane.get())
                    .matches(Component::isVisible)
                    .extracting(pane -> pane.getClass().getSimpleName())
                    .isEqualTo("BlockingGlassPane");
            assertThat(app.dialogs().shown()).containsExactly(ContactDialog.class);
        }
    }

    @Test
    void speaksFrench() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.FRENCH)) {
            assertThat(app.title()).isEqualTo("Simple application de gestion d'adresses");
            assertThat(app.components(JMenu.class))
                    .extracting(JMenu::getText)
                    .containsExactly("Fichier", "Édition", "Contacts", "Tests de tâches");
            assertThat(app.application().table.getColumnName(0)).isEqualTo("Prénom");
            assertThat(app.component(JLabel.class, "Photo du contact").getText())
                    .isEqualTo("Aucun contact sélectionné");
        }
    }

    private static void awaitFinished(TaskTestActions actions, int count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (actions.log().stream().filter(line -> line.endsWith("finished")).count() < count
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static List<String> names(AppTester<AddressBookApp> app) {
        JTable table = app.application().table;
        return IntStream.range(0, table.getRowCount())
                .mapToObj(row -> table.getValueAt(row, 0) + " " + table.getValueAt(row, 1))
                .toList();
    }

    private static void select(AddressBookApp book, int row) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(() -> book.table.setRowSelectionInterval(row, row));
        EventQueue.invokeAndWait(() -> {});
    }

    private static void ok(OpenDialog<?> dialog) {
        dialog.commands(StandardCommands.class).ok().execute();
    }
}
