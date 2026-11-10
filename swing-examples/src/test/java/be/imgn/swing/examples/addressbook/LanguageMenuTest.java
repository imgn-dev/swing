package be.imgn.swing.examples.addressbook;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.testing.AppTester;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class LanguageMenuTest {

    @Test
    void listsFrenchThenEnglishInTheLanguageMenu() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.ENGLISH)) {
            assertThat(items(app, "Language")).containsExactly("French", "English");
        }
    }

    @Test
    void frenchChangesTheMenusTheHeadersTheLabelsAndTheTitle() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.ENGLISH)) {
            app.commands(GeneralActionsCommands.class).french().execute();

            assertThat(menus(app)).containsExactly("Fichier", "Édition", "Contacts", "Langage", "Tests de tâches");
            assertThat(items(app, "Langage")).containsExactly("Francais", "Anglais");
            assertThat(app.call(() -> headers(app))).containsExactly("Prénom", "Nom", "Ville");
            assertThat(app.call(() -> details(app)))
                    .containsExactly("Prénom", "Nom", "Date de naissance", "Aucun contact sélectionné");
            assertThat(app.title()).isEqualTo("Simple application de gestion d'adresses");
        }
    }

    @Test
    void englishChangesThemBack() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.ENGLISH)) {
            GeneralActionsCommands general = app.commands(GeneralActionsCommands.class);
            general.french().execute();

            general.english().execute();

            assertThat(menus(app)).containsExactly("File", "Edit", "Contacts", "Language", "Tasks Tests");
            assertThat(app.call(() -> headers(app))).containsExactly("First Name", "Last Name", "Home City");
            assertThat(app.call(() -> details(app)))
                    .containsExactly("First Name", "Last Name", "Birth Date", "No contact selected");
            assertThat(app.title()).isEqualTo("Simple Address Book Application");
        }
    }

    @Test
    void opensAContactDialogInFrenchAfterTheChange() {
        var streets = new ArrayList<String>();
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.ENGLISH)) {
            app.commands(GeneralActionsCommands.class).french().execute();
            app.dialogs().answerDialog(ContactDialog.class, dialog -> {
                streets.add(dialog.view().home.streetLabel.getText());
            });

            app.commands(ContactActionsCommands.class).createContact().execute();

            assertThat(streets).containsExactly("Rue");
        }
    }

    @Test
    void startsInTheLanguageChosenInAnEarlierRun() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                    .fileSystem(fileSystem)
                    .locale(Locale.ENGLISH)
                    .start()) {
                app.commands(GeneralActionsCommands.class).french().execute();
            }
            try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                    .fileSystem(fileSystem)
                    .locale(Locale.ENGLISH)
                    .start()) {
                assertThat(app.call(() -> headers(app))).containsExactly("Prénom", "Nom", "Ville");
                assertThat(app.title()).isEqualTo("Simple application de gestion d'adresses");
            }
        }
    }

    private static List<String> menus(AppTester<AddressBookApp> app) {
        return app.components(JMenu.class).stream().map(JMenu::getText).toList();
    }

    private static List<String> items(AppTester<AddressBookApp> app, String menu) {
        JMenu found = app.components(JMenu.class).stream()
                .filter(candidate -> candidate.getText().equals(menu))
                .findFirst()
                .orElseThrow();
        return IntStream.range(0, found.getItemCount())
                .mapToObj(found::getItem)
                .map(JMenuItem::getText)
                .toList();
    }

    // The detail panel's labels, then the picture's text.
    private static List<String> details(AppTester<AddressBookApp> app) {
        AddressBookApp view = app.application();
        return List.of(
                view.firstNameLabel.getText(),
                view.lastNameLabel.getText(),
                view.birthLabel.getText(),
                view.picture.getText());
    }

    private static List<String> headers(AppTester<AddressBookApp> app) {
        JTable table = app.application().table;
        return IntStream.range(0, table.getColumnCount())
                .mapToObj(table::getColumnName)
                .toList();
    }
}
