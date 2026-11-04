package be.imgn.swing.examples.addressbook;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.swing.JTabbedPane;
import javax.swing.table.TableColumnModel;
import org.junit.jupiter.api.Test;

/** The headless windows restore the state of their components from a window state written with the builder. */
class AddressBookWindowStateTest {

    @Test
    void restoresTheMainWindowsComponentsFromAWindowStateWrittenWithTheBuilder() {
        try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                .file(directories -> directories.state().resolve("window-state.toml"), """
                        [windows.main.components.mainSplit]
                        divider-location = 123
                        """)
                .start()) {
            AddressBookApp book = app.application();

            assertThat(book.mainSplit.getDividerLocation()).isEqualTo(123);
        }
    }

    // The table is bound in content(), so its columns exist when window state is restored.
    @Test
    void restoresTheOrderAndWidthsOfTheContactsTablesColumns() {
        try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                .file(directories -> directories.state().resolve("window-state.toml"), """
                        [windows.main.components.table]
                        columns = [
                            { model-index = 2, width = 150 },
                            { model-index = 0, width = 90 },
                            { model-index = 1, width = 110 }
                        ]
                        """)
                .start()) {
            TableColumnModel columns = app.application().table.getColumnModel();

            List<List<Object>> restored = app.call(() -> IntStream.range(0, columns.getColumnCount())
                    .mapToObj(columns::getColumn)
                    .map(column -> List.<Object>of(column.getModelIndex(), column.getHeaderValue(), column.getWidth()))
                    .toList());

            assertThat(restored)
                    .containsExactly(
                            List.of(2, "Home City", 150), List.of(0, "First Name", 90), List.of(1, "Last Name", 110));
        }
    }

    @Test
    void restoresADialogsComponentsFromAWindowStateWrittenWithTheBuilder() {
        try (AppTester<AddressBookApp> app = AppTester.builder(AddressBookApp.class)
                .file(directories -> directories.state().resolve("window-state.toml"), """
                        [windows."be.imgn.swing.examples.addressbook.ContactTabsDialog".components.tabs]
                        selected-index = 2
                        """)
                .start()) {
            AtomicInteger selected = new AtomicInteger(-1);
            app.dialogs().answerDialog(ContactTabsDialog.class, dialog -> {
                selected.set(dialog.component(JTabbedPane.class).getSelectedIndex());
                dialog.commands(StandardCommands.class).cancel().execute();
            });

            app.commands(ContactActionsCommands.class).createContactWithTabs().execute();

            assertThat(selected).hasValue(2);
        }
    }
}
