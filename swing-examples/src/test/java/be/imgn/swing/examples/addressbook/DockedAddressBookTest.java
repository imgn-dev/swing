package be.imgn.swing.examples.addressbook;

import static be.imgn.swing.examples.addressbook.AddressBookViews.table;
import static be.imgn.swing.examples.addressbook.AddressBookViews.view;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.docking.Dock;
import be.imgn.swing.docking.DockLayout;
import be.imgn.swing.docking.DockLayout.Orientation;
import be.imgn.swing.docking.DockLayout.Split;
import be.imgn.swing.docking.DockLayout.Tabs;
import be.imgn.swing.docking.DockView;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.CommandPalette;
import be.imgn.swing.ui.PaletteEntry;
import be.imgn.swing.ui.StandardCommands;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/** The address book docked as in Guts' docking demo: its views, picture tabs, Views menu and saved layout. */
class DockedAddressBookTest {

    private static final String STATE = "window-state.toml";

    private static Optional<DockLayout> layout(AppTester<AddressBookApp> app) {
        Dock dock = app.bean(Dock.class);
        return app.call(dock::layout);
    }

    private static List<DockView> views(AppTester<AddressBookApp> app) {
        Dock dock = app.bean(Dock.class);
        return app.call(dock::views);
    }

    private static void select(AppTester<AddressBookApp> app, int row) {
        JTable table = table(app);
        app.run(() -> table.setRowSelectionInterval(row, row));
    }

    private static String lastName(AppTester<AddressBookApp> app, int row) {
        JTable table = table(app);
        return app.call(() -> (String) table.getValueAt(row, 1));
    }

    private static void openPicture(AppTester<AddressBookApp> app) {
        ContactListView list = view(app, ContactListView.class);
        app.bean(list, ContactListViewCommands.class).openPicture().execute();
    }

    private static ContactPictureView pictureTab(AppTester<AddressBookApp> app) {
        return view(app, ContactPictureView.class);
    }

    private static void choose(AppTester<AddressBookApp> app, String menu, String item) {
        app.run(() -> app.components(JMenu.class).stream()
                .filter(candidate -> candidate.getText().equals(menu))
                .flatMap(found -> Stream.of(found.getMenuComponents()))
                .filter(JMenuItem.class::isInstance)
                .map(JMenuItem.class::cast)
                .filter(candidate -> candidate.getText().equals(item))
                .findFirst()
                .orElseThrow()
                .doClick());
    }

    private static Path stateFile(FileSystem fileSystem) throws IOException {
        try (Stream<Path> paths = Files.walk(fileSystem.getPath("/"))) {
            return paths.filter(path -> path.endsWith(STATE)).findFirst().orElseThrow();
        }
    }

    @Test
    void opensTheFirstLayout() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            DockView list = view(app, ContactListView.class);
            DockView detail = view(app, ContactDetailView.class);
            DockView picture = view(app, SelectedPictureView.class);

            assertThat(layout(app))
                    .contains(new Split(
                            Orientation.HORIZONTAL,
                            0.33,
                            new Split(
                                    Orientation.VERTICAL,
                                    0.5,
                                    new Tabs(List.of(list), list),
                                    new Tabs(List.of(detail), detail)),
                            new Tabs(List.of(picture), picture)));
            assertThat(app.call(() -> app.bean(Dock.class).title(list))).isEqualTo("Contacts List");
        }
    }

    @Test
    void showsASelectedContactInTheDetailAndThePicture() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            JLabel picture = view(app, SelectedPictureView.class).picture;
            assertThat(app.call(picture::getText)).isEqualTo("No contact selected");

            select(app, 0);

            assertThat(app.call(
                            () -> app.component(JTextField.class, "Last Name").getText()))
                    .isEqualTo(lastName(app, 0));
            assertThat(app.call(() ->
                            picture.getIcon() != null || !picture.getText().isEmpty()))
                    .isTrue();
        }
    }

    @Test
    void opensAPictureTabTitledWithTheLastNameInThePicturesGroupOnce() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            select(app, 0);
            openPicture(app);
            ContactPictureView tab = pictureTab(app);
            DockView picture = view(app, SelectedPictureView.class);
            Dock dock = app.bean(Dock.class);

            assertThat(app.call(() -> dock.title(tab))).isEqualTo(lastName(app, 0));
            assertThat(layout(app))
                    .hasValueSatisfying(root ->
                            assertThat(((Split) root).second()).isEqualTo(new Tabs(List.of(picture, tab), tab)));

            app.run(() -> dock.toFront(view(app, ContactListView.class)));
            openPicture(app);

            assertThat(views(app).stream().filter(ContactPictureView.class::isInstance))
                    .hasSize(1);
            assertThat(app.call(() -> dock.active().get())).contains(tab);
        }
    }

    @Test
    void opensAPictureTabOnADoubleClickOnARow() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            select(app, 1);
            JTable table = table(app);

            app.run(() -> table.dispatchEvent(
                    new MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 2, false, MouseEvent.BUTTON1)));

            assertThat(app.call(() -> app.bean(Dock.class).title(pictureTab(app))))
                    .isEqualTo(lastName(app, 1));
        }
    }

    @Test
    void selectsTheOpenPictureTabOfASelectedContactWithoutChangingTheActiveView() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            Dock dock = app.bean(Dock.class);
            DockView picture = view(app, SelectedPictureView.class);
            select(app, 0);
            openPicture(app);
            ContactPictureView tab = pictureTab(app);
            DockView list = view(app, ContactListView.class);
            app.run(() -> dock.select(picture));
            app.run(() -> dock.toFront(list));
            select(app, 1);

            select(app, 0);

            assertThat(layout(app))
                    .hasValueSatisfying(root ->
                            assertThat(((Split) root).second()).isEqualTo(new Tabs(List.of(picture, tab), tab)));
            assertThat(app.call(() -> dock.active().get())).contains(list);
        }
    }

    @Test
    void selectsTheContactOfAPictureTabBroughtToTheFront() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            Dock dock = app.bean(Dock.class);
            select(app, 0);
            openPicture(app);
            ContactPictureView tab = pictureTab(app);
            app.run(() -> dock.toFront(view(app, ContactListView.class)));
            select(app, 2);

            app.run(() -> dock.toFront(tab));

            assertThat(app.call(() -> table(app).getSelectedRow())).isEqualTo(0);
        }
    }

    @Test
    void opensAClosedViewAgainFromTheViewsMenuShowingTheSelection() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            Dock dock = app.bean(Dock.class);
            ContactDetailView detail = view(app, ContactDetailView.class);
            JTextField closedLastName = app.call(() -> app.component(JTextField.class, "Last Name"));
            app.run(() -> dock.close(detail));
            select(app, 1);

            choose(app, "Views", "Contact Detail");

            ContactDetailView reopened = view(app, ContactDetailView.class);
            assertThat(reopened).isNotSameAs(detail);
            assertThat(app.call(
                            () -> app.component(JTextField.class, "Last Name").getText()))
                    .isEqualTo(lastName(app, 1));
            // The closed view no longer follows the selection.
            assertThat(app.call(() -> closedLastName.getText())).isEmpty();
        }
    }

    @Test
    void restoresTheLayoutAfterARestartWithoutThePictureTabsAndWithTheTablesColumns() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                    .fileSystem(fileSystem)
                    .start()) {
                Dock dock = app.bean(Dock.class);
                app.run(() -> dock.close(view(app, ContactDetailView.class)));
                select(app, 0);
                openPicture(app);
                JTable table = table(app);
                app.run(() -> table.getColumnModel().moveColumn(0, 2));
                app.commands(StandardCommands.class).exit().execute();
            }
            try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                    .fileSystem(fileSystem)
                    .start()) {
                DockView list = view(app, ContactListView.class);
                DockView picture = view(app, SelectedPictureView.class);
                JTable table = table(app);

                assertThat(layout(app))
                        .contains(new Split(
                                Orientation.HORIZONTAL,
                                0.33,
                                new Tabs(List.of(list), list),
                                new Tabs(List.of(picture), picture)));
                assertThat(app.call(() -> table.getColumnModel().getColumn(2).getModelIndex()))
                        .isZero();
            }
        }
    }

    @Test
    void restoresTheTablesColumnsWhenTheListOpensAgain() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            Dock dock = app.bean(Dock.class);
            JTable table = table(app);
            app.run(() -> table.getColumnModel().moveColumn(0, 2));
            app.run(() -> dock.close(view(app, ContactListView.class)));

            choose(app, "Views", "Contacts List");

            JTable reopened = table(app);
            assertThat(reopened).isNotSameAs(table);
            assertThat(app.call(() -> reopened.getColumnModel().getColumn(2).getModelIndex()))
                    .isZero();
        }
    }

    @Test
    void restoresASavedLayoutWithoutAClassThatDoesntExistWithAWarning() throws IOException {
        String saved = """
                [windows.main.dock]
                split = "horizontal"
                proportion = 0.4

                [windows.main.dock.first]
                tabs = ["be.imgn.swing.examples.addressbook.ContactListView", "p.NoSuchView"]
                selected = 0

                [windows.main.dock.second]
                tabs = ["be.imgn.swing.examples.addressbook.SelectedPictureView"]
                selected = 0
                """;
        List<String> warnings = new ArrayList<>();
        Logger logger = Logger.getLogger("be.imgn.swing.docking.internal.SavedLayouts");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
                // Nothing buffered.
            }

            @Override
            public void close() {
                // Nothing held.
            }
        };
        logger.addHandler(handler);
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                    .fileSystem(fileSystem)
                    .start()) {
                app.commands(StandardCommands.class).exit().execute();
            }
            Files.writeString(stateFile(fileSystem), saved);
            try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                    .fileSystem(fileSystem)
                    .start()) {
                DockView list = view(app, ContactListView.class);
                DockView picture = view(app, SelectedPictureView.class);

                assertThat(layout(app))
                        .contains(new Split(
                                Orientation.HORIZONTAL,
                                0.4,
                                new Tabs(List.of(list), list),
                                new Tabs(List.of(picture), picture)));
            }
        } finally {
            logger.removeHandler(handler);
        }
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("p.NoSuchView"));
    }

    @Test
    void namesTheViewsInFrenchButAPictureTabByItsContactsLastName() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.ENGLISH)) {
            Dock dock = app.bean(Dock.class);
            select(app, 0);
            openPicture(app);
            ContactPictureView tab = pictureTab(app);

            app.locale(Locale.FRENCH);

            assertThat(app.call(() -> dock.title(view(app, ContactListView.class))))
                    .isEqualTo("Tous les Contacts");
            assertThat(app.call(() -> dock.title(view(app, ContactDetailView.class))))
                    .isEqualTo("Information Contact");
            assertThat(app.call(() -> dock.title(view(app, SelectedPictureView.class))))
                    .isEqualTo("Photo du Contact");
            assertThat(app.call(() -> dock.title(tab))).isEqualTo(lastName(app, 0));
        }
    }

    @Test
    void listsOpenPictureInThePaletteOfTheFrame() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            select(app, 0);

            List<String> texts = app.call(() -> app.bean(CommandPalette.class).entries().stream()
                    .map(PaletteEntry::text)
                    .toList());

            assertThat(texts).contains("Open Picture", "Contacts List", "Contact Detail", "Contact Picture");
        }
    }

    @Test
    void inspectsTheTableAsTheListsField() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            JTable table = table(app);

            assertThat(app.inspect(table).view()).isEqualTo(ContactListView.class);
            assertThat(app.inspect(table).field()).isEqualTo("table");
        }
    }

    @Test
    void selectsAPictureTabsContactAsTheServiceHoldsItNow() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            Dock dock = app.bean(Dock.class);
            AddressBookService service = app.bean(AddressBookService.class);
            select(app, 0);
            openPicture(app);
            ContactPictureView tab = pictureTab(app);
            Contact contact = app.call(() -> service.contacts().get(0));
            app.run(() -> service.modifyContact(contact.withIdentity("Johnny", contact.lastName(), null)));
            app.run(() -> dock.toFront(view(app, ContactListView.class)));
            select(app, 2);

            app.run(() -> dock.toFront(tab));

            assertThat(app.call(
                            () -> app.component(JTextField.class, "First Name").getText()))
                    .isEqualTo("Johnny");
            assertThat(app.call(() -> app.bean(CurrentContact.class)
                            .contact()
                            .get()
                            .orElseThrow()
                            .firstName()))
                    .isEqualTo("Johnny");
        }
    }

    @Test
    void selectsTheKeptContactInAListOpenedAgain() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            Dock dock = app.bean(Dock.class);
            select(app, 1);
            app.run(() -> dock.close(view(app, ContactListView.class)));

            choose(app, "Views", "Contacts List");

            assertThat(app.call(() -> table(app).getSelectedRow())).isEqualTo(1);
        }
    }

    @Test
    void followsAModificationOfTheSelectedContactWhileTheListIsClosed() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            Dock dock = app.bean(Dock.class);
            AddressBookService service = app.bean(AddressBookService.class);
            select(app, 0);
            app.run(() -> dock.close(view(app, ContactListView.class)));
            Contact contact = app.call(() -> service.contacts().get(0));

            app.run(() -> service.modifyContact(contact.withIdentity("Johnny", contact.lastName(), null)));

            assertThat(app.call(
                            () -> app.component(JTextField.class, "First Name").getText()))
                    .isEqualTo("Johnny");
        }
    }

    @Test
    void startsWithNoContactSelected() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            assertThat(app.call(() -> app.bean(CurrentContact.class).contact().get()))
                    .isEmpty();
        }
    }

    @Test
    void enablesOpenPictureWhileAContactIsSelectedAndNamesItInFrench() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.FRENCH)) {
            ContactListView list = view(app, ContactListView.class);
            ContactListViewCommands commands = app.bean(list, ContactListViewCommands.class);
            assertThat(commands.openPicture().execute()).isFalse();

            select(app, 0);

            assertThat(app.call(() -> commands.openPicture().asAction().getValue(Action.NAME)))
                    .isEqualTo("Ouvrir la photo");
            assertThat(commands.openPicture().execute()).isTrue();
        }
    }

    @Test
    void namesTheViewsMenusItemsWithGutsTextsAndIcons() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH)) {
            try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, locale)) {
                String menu = locale.equals(Locale.ENGLISH) ? "Views" : "Vues";
                List<JMenuItem> items = app.call(() -> app.components(JMenu.class).stream()
                        .filter(candidate -> candidate.getText().equals(menu))
                        .flatMap(found -> Stream.of(found.getMenuComponents()))
                        .filter(JMenuItem.class::isInstance)
                        .map(JMenuItem.class::cast)
                        .toList());

                assertThat(app.call(() -> items.stream().map(JMenuItem::getText).toList()))
                        .isEqualTo(
                                locale.equals(Locale.ENGLISH)
                                        ? List.of("Contacts List", "Contact Detail", "Contact Picture")
                                        : List.of("Tous les Contacts", "Information du Contact", "Photo du Contact"));
                assertThat(app.call(() -> items.stream().allMatch(item -> item.getIcon() != null)))
                        .isTrue();
            }
        }
    }

    @Test
    void bringsAnOpenViewToTheFrontFromTheViewsMenu() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            Dock dock = app.bean(Dock.class);
            DockView list = view(app, ContactListView.class);
            app.run(() -> dock.toFront(view(app, ContactDetailView.class)));

            choose(app, "Views", "Contacts List");

            assertThat(views(app)).hasSize(3);
            assertThat(app.call(() -> dock.active().get())).contains(list);
        }
    }

    @Test
    void showsTheViewsIconsOnTheirTabs() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class)) {
            select(app, 0);
            openPicture(app);

            assertThat(app.call(() -> app.components(JTabbedPane.class).stream()
                            .flatMap(tabs ->
                                    IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getIconAt))
                            .allMatch(Objects::nonNull)))
                    .isTrue();
        }
    }

    @Test
    void showsAPictureTabsPictureOrTellsItHasNoneInTheLocale() {
        try (AppTester<AddressBookApp> app = AppTester.start(AddressBookApp.class, Locale.ENGLISH)) {
            select(app, 0);
            openPicture(app);
            JLabel withPicture = pictureTab(app).picture;
            assertThat(app.call(() -> withPicture.getIcon() != null)).isTrue();
            app.run(() -> app.bean(Dock.class).close(pictureTab(app)));
            select(app, 4);
            openPicture(app);
            JLabel without = pictureTab(app).picture;
            assertThat(app.call(without::getText)).isEqualTo("No picture for this contact");

            app.locale(Locale.FRENCH);

            assertThat(app.call(without::getText)).isEqualTo("Pas de photo pour ce contact");
        }
    }
}
