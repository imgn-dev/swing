package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.Column;
import be.imgn.swing.ui.CommandRegistry;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.Ui;
import java.awt.Component;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** How {@code Ui.bind} shows an observable list in a {@link JTable}, edits it and binds its selection. */
class TableBindingTest {

    private static final Contact ADA = new Contact(1, "Ada", 36, true);
    private static final Contact BOB = new Contact(2, "Bob", null, null);
    private static final Contact CY = new Contact(3, "Cy", 7, false);

    @Test
    void showsARowPerElementAndAColumnPerColumnWhoseModelIndexIsItsPosition() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);

            // Within the call that binds, as in content(), so that window state finds the columns.
            List<Integer> modelIndices = app.call(() -> {
                ui(app).bind(table, rows, columns());
                List<Integer> indices = new ArrayList<>();
                TableColumnModel columns = table.getColumnModel();
                for (int index = 0; index < columns.getColumnCount(); index++) {
                    indices.add(columns.getColumn(index).getModelIndex());
                }
                return indices;
            });

            assertThat(modelIndices).containsExactly(0, 1, 2);
            List<Object> model = app.call(() -> {
                TableModel tableModel = table.getModel();
                List<Object> found = new ArrayList<>();
                found.add(tableModel.getRowCount());
                for (int column = 0; column < tableModel.getColumnCount(); column++) {
                    found.add(tableModel.getColumnName(column));
                    found.add(tableModel.getColumnClass(column));
                    found.add(String.valueOf(tableModel.getValueAt(0, column)));
                }
                return found;
            });
            assertThat(model)
                    .containsExactly(
                            2,
                            "Name",
                            String.class,
                            "Ada",
                            "Age",
                            Integer.class,
                            "36",
                            "Active",
                            Boolean.class,
                            "true");
        }
    }

    @Test
    void hasNoRowSorter() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> table.setAutoCreateRowSorter(true));

            app.run(() -> ui(app).bind(table, rows, columns()));

            assertThat(app.call(table::getAutoCreateRowSorter)).isFalse();
            assertThat(app.call(() -> Optional.ofNullable(table.getRowSorter())))
                    .isEmpty();
        }
    }

    @Test
    void firesTheTableEventOfEachChangeInOrder() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            List<String> events = new CopyOnWriteArrayList<>();
            app.run(() -> {
                ui(app).bind(table, rows, columns());
                table.getModel().addTableModelListener(event -> events.add(describe(event)));
            });

            rows.add(CY);
            rows.remove(0);
            rows.set(0, BOB.withName("Robert"));
            rows.setAll(List.of(ADA, CY));
            rows.setAll(List.of(ADA));
            rows.clear();
            app.run(() -> {});

            assertThat(events)
                    .containsExactly(
                            "inserted 2-2", "deleted 0-0", "updated 0-0", "updated 0-1", "data changed", "deleted 0-0");
            assertThat(app.call(table::getRowCount)).isZero();
        }
    }

    @Test
    void showsAnEmptyCellForANullValue() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns()));

            Component nullAge = rendered(app, table, 1, 1);
            Component nullActive = rendered(app, table, 1, 2);
            Component active = rendered(app, table, 0, 2);
            Object booleanRenderer = app.call(() -> table.getDefaultRenderer(Boolean.class));

            assertThat(nullAge)
                    .isInstanceOfSatisfying(
                            JLabel.class, label -> assertThat(label.getText()).isEmpty());
            // The default renderer of Boolean, which the look and feel chooses, would show an unchecked box.
            assertThat(nullActive).isNotSameAs(booleanRenderer).isInstanceOfSatisfying(JLabel.class, label -> {
                assertThat(label.getText()).isEmpty();
                assertThat(label.getIcon()).isNull();
            });
            assertThat(active).isSameAs(booleanRenderer);
        }
    }

    @Test
    void commitsAnEditByReplacingTheRowInTheList() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns()));

            app.run(() -> {
                table.editCellAt(1, 1);
                ((JTextField) table.getEditorComponent()).setText("42");
                table.getCellEditor().stopCellEditing();
            });

            assertThat(rows.get()).containsExactly(ADA, BOB.withAge(42));
            assertThat(app.call(() -> table.getValueAt(1, 1))).isEqualTo(42);
        }
    }

    @Test
    void dropsAnEditOfARowThatTheListNoLongerHoldsAtItsIndex() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns()));

            // The insertion is queued: the table still shows Ada at 0 when the edit is committed.
            app.run(() -> {
                rows.add(0, CY);
                table.setValueAt("Augusta", 0, 0);
            });

            assertThat(rows.get()).containsExactly(CY, ADA, BOB);
        }
    }

    @Test
    void checksAndReplacesTheEditedRowWithoutAnotherThreadChangingTheListInBetween() throws InterruptedException {
        List<Thread> removers = new CopyOnWriteArrayList<>();
        AtomicReference<@Nullable Runnable> onEquals = new AtomicReference<>();
        Racy edited = new Racy("Bob", onEquals);
        ObservableList<Racy> rows = ObservableList.of(new Racy("Ada"), edited, new Racy("Cy"));
        // The binding's check that the list holds the edited row compares it, which lets another thread remove the
        // first row: the removal waits for the list's lock if the check holds it, and otherwise is done before the
        // check returns, so that the replacement would overwrite Cy, now at the edited index.
        onEquals.set(() -> {
            Thread remover = Thread.ofPlatform().start(() -> rows.remove(0));
            removers.add(remover);
            while (remover.isAlive() && remover.getState() != Thread.State.WAITING) {
                Thread.onSpinWait();
            }
        });
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(
                            table,
                            rows,
                            List.of(Column.of("Name", String.class, Racy::name).editable(Racy::withName))));

            app.run(() -> table.setValueAt("Robert", 1, 0));
            for (Thread remover : removers) {
                remover.join();
            }

            assertThat(removers).hasSize(1);
            assertThat(rows.get()).containsExactly(new Racy("Robert"), new Racy("Cy"));
        }
    }

    @Test
    void passesTheNullThatAnEditsFunctionReturnsToTheList() {
        ObservableList<Contact> rows = ObservableList.of(ADA);
        // Through a list, which NullAway doesn't check, to return null where a row is expected.
        List<Contact> nulls = new ArrayList<>();
        nulls.add(null);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(
                            table,
                            rows,
                            List.of(Column.of("Name", String.class, Contact::name)
                                    .editable((contact, name) -> nulls.getFirst()))));

            assertThatThrownBy(() -> app.run(() -> {
                        table.editCellAt(0, 0);
                        ((JTextField) table.getEditorComponent()).setText("Augusta");
                        table.getCellEditor().stopCellEditing();
                    }))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("element");
            assertThat(rows.get()).containsExactly(ADA);
        }
    }

    @Test
    void createsAColumnPerColumnEvenWhenTheTableDoesNotCreateThemFromItsModel() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> table.setAutoCreateColumnsFromModel(false));

            List<Object> found = app.call(() -> {
                ui(app).bind(table, rows, columns());
                List<Object> columns = new ArrayList<>();
                TableColumnModel model = table.getColumnModel();
                for (int index = 0; index < model.getColumnCount(); index++) {
                    columns.add(model.getColumn(index).getModelIndex());
                    columns.add(model.getColumn(index).getHeaderValue());
                }
                return columns;
            });

            assertThat(found).containsExactly(0, "Name", 1, "Age", 2, "Active");
            assertThat(rendered(app, table, 1, 2)).isInstanceOfSatisfying(JLabel.class, label -> {
                assertThat(label.getText()).isEmpty();
                assertThat(label.getIcon()).isNull();
            });
        }
    }

    @Test
    void dropsAnEditThatCommitsNoValue() {
        ObservableList<Contact> rows = ObservableList.of(ADA);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns()));

            app.run(() -> {
                table.editCellAt(0, 1);
                ((JTextField) table.getEditorComponent()).setText("");
                table.getCellEditor().stopCellEditing();
            });

            assertThat(rows.get()).containsExactly(ADA);
        }
    }

    @Test
    void editsOnlyTheEditableColumns() {
        ObservableList<Contact> rows = ObservableList.of(ADA);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns()));

            assertThat(app.call(() -> List.of(
                            table.isCellEditable(0, 0), table.isCellEditable(0, 1), table.isCellEditable(0, 2))))
                    .containsExactly(true, true, false);
        }
    }

    @Test
    void editableAcceptsOnlyTheTypesOfTheDefaultEditors() {
        assertThatCode(() -> {
                    Column.of("s", String.class, (Contact c) -> "").editable((c, v) -> c);
                    Column.of("b", Boolean.class, (Contact c) -> true).editable((c, v) -> c);
                    Column.of("i", Integer.class, (Contact c) -> 1).editable((c, v) -> c);
                    Column.of("l", Long.class, (Contact c) -> 1L).editable((c, v) -> c);
                    Column.of("d", Double.class, (Contact c) -> 1.0).editable((c, v) -> c);
                })
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Column.of("date", LocalDate.class, (Contact c) -> LocalDate.EPOCH)
                        .editable((c, v) -> c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
        assertThatThrownBy(() -> Column.of("n", Number.class, (Contact c) -> 1).editable((c, v) -> c))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Column.of("o", Object.class, (Contact c) -> c).editable((c, v) -> c))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullHeaderTypeOrValue() {
        // Through lists, which NullAway doesn't check, to reach the column's own checks.
        List<String> headers = new ArrayList<>();
        headers.add(null);
        List<Class<String>> types = new ArrayList<>();
        types.add(null);
        List<Function<Contact, String>> values = new ArrayList<>();
        values.add(null);

        assertThatThrownBy(() -> Column.of(headers.getFirst(), String.class, Contact::name))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Column.of("Name", types.getFirst(), Contact::name))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Column.of("Name", String.class, values.getFirst()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsASecondBinding() {
        ObservableList<Contact> rows = ObservableList.of(ADA);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns()));

            assertThatThrownBy(() -> app.run(() ->
                            ui(app).bind(table, rows, columns(), Property.of(Optional.<Contact>empty()), Contact::id)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already bound");
        }
    }

    @Test
    void setsThePropertyWhenTheUserSelectsARow() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB, CY);
        Property<Optional<Contact>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns(), selection));

            app.run(() -> table.setRowSelectionInterval(2, 2));

            assertThat(app.call(() -> table.getSelectionModel().getSelectionMode()))
                    .isEqualTo(ListSelectionModel.SINGLE_SELECTION);
            assertThat(selection.get()).contains(CY);
        }
    }

    @Test
    void keepsAModifiedRowSelected() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB, CY);
        Property<Optional<Contact>> selection = Property.of(Optional.of(BOB));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns(), selection));

            rows.set(1, BOB.withName("Robert"));
            app.run(() -> {});

            assertThat(app.call(table::getSelectedRow)).isEqualTo(1);
            assertThat(selection.get()).contains(BOB.withName("Robert"));
        }
    }

    @Test
    void theTablesOwnShiftOfTheSelectionOnARemovalIsNotTheUsers() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB, CY);
        Property<Optional<Contact>> selection = Property.of(Optional.of(CY));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns(), selection));
            List<Optional<Contact>> values = record(app, selection);

            rows.remove(0);
            app.run(() -> {});

            assertThat(app.call(table::getSelectedRow)).isEqualTo(1);
            assertThat(values).isEmpty();
        }
    }

    @Test
    void keepsTheRowWithTheSelectedKeyAcrossAReloadThatTheTableClearsItsSelectionFor() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB, CY);
        Property<Optional<Contact>> selection = Property.of(Optional.of(BOB));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            app.run(() -> ui(app).bind(table, rows, columns(), selection, Contact::id));
            List<Optional<Contact>> values = record(app, selection);

            Contact reloaded = BOB.withAge(50);
            rows.setAll(List.of(CY, reloaded, ADA, new Contact(4, "Dee", null, null)));
            app.run(() -> {});

            assertThat(app.call(table::getSelectedRow)).isEqualTo(1);
            assertThat(selection.get()).contains(reloaded);
            assertThat(values).containsExactly(Optional.of(reloaded));
        }
    }

    @Test
    void scrollsToTheSelectedRow() {
        List<Contact> many = new ArrayList<>();
        for (int id = 0; id < 50; id++) {
            many.add(new Contact(id, "Contact " + id, id, null));
        }
        ObservableList<Contact> rows = ObservableList.copyOf(many);
        Property<Optional<Contact>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTable table = newTable(app);
            JScrollPane scroll = app.call(() -> {
                ui(app).bind(table, rows, columns(), selection, Contact::id);
                JScrollPane pane = new JScrollPane(table);
                pane.setSize(200, 80);
                pane.validate();
                return pane;
            });

            selection.set(Optional.of(many.get(45)));
            app.run(() -> {});

            assertThat(app.call(table::getSelectedRow)).isEqualTo(45);
            assertThat(app.call(() -> scroll.getViewport().getViewPosition().y)).isPositive();
        }
    }

    @Test
    void anEditOfATrackedListUpdatesTheFormBeforeTheEditReturns() {
        ObservableList<Contact> rows = ObservableList.of(ADA, BOB);
        ObservableList<Boolean> states = ObservableList.of();
        List<Boolean> executed = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = app.commands(Form.class);
            Scope scope = app.commands(Scope.class);
            CommandRegistry registry = app.commands(CommandRegistry.class);
            JTable table = newTable(app);
            BoundCommand save = app.call(() -> {
                ui(app).bind(table, rows, columns());
                form.track(rows);
                // A dependent of the form's state may modify an observable list: it doesn't run under the list's lock.
                form.dirty().subscribe(scope, states::add);
                return registry.command(TableBindingTest.class, "save")
                        .handler(context -> {})
                        .enabled(form::dirty)
                        .bind();
            });

            app.run(() -> {
                table.editCellAt(1, 1);
                ((JTextField) table.getEditorComponent()).setText("42");
                table.getCellEditor().stopCellEditing();
                executed.add(save.execute());
            });

            assertThat(rows.get()).containsExactly(ADA, BOB.withAge(42));
            assertThat(executed).containsExactly(true);
            assertThat(states.get()).containsExactly(true);
        }
    }

    private static Ui ui(AppTester<CounterApp> app) {
        return app.commands(Ui.class);
    }

    private static JTable newTable(AppTester<CounterApp> app) {
        return app.call(JTable::new);
    }

    private static List<Column<Contact, ?>> columns() {
        return List.of(
                Column.of("Name", String.class, Contact::name).editable(Contact::withName),
                Column.of("Age", Integer.class, Contact::age).editable(Contact::withAge),
                Column.of("Active", Boolean.class, Contact::active));
    }

    private static Component rendered(AppTester<CounterApp> app, JTable table, int row, int column) {
        return app.call(() -> table.prepareRenderer(table.getCellRenderer(row, column), row, column));
    }

    private static <T> List<T> record(AppTester<CounterApp> app, Property<T> property) {
        List<T> values = new CopyOnWriteArrayList<>();
        property.subscribe(app.commands(Scope.class), values::add);
        return values;
    }

    private static String describe(TableModelEvent event) {
        return switch (event.getType()) {
            case TableModelEvent.INSERT -> "inserted " + event.getFirstRow() + "-" + event.getLastRow();
            case TableModelEvent.DELETE -> "deleted " + event.getFirstRow() + "-" + event.getLastRow();
            default ->
                event.getLastRow() == Integer.MAX_VALUE
                        ? "data changed"
                        : "updated " + event.getFirstRow() + "-" + event.getLastRow();
        };
    }

    record Contact(
            int id,
            String name,
            @Nullable Integer age,
            @Nullable Boolean active) {

        Contact withName(String newName) {
            return new Contact(id, newName, age, active);
        }

        Contact withAge(Integer newAge) {
            return new Contact(id, name, newAge, active);
        }
    }

    // A row equal to another of the same name, whose comparison first runs a hook, once.
    static final class Racy {

        private final String name;
        private final AtomicReference<@Nullable Runnable> onEquals;

        Racy(String name, AtomicReference<@Nullable Runnable> onEquals) {
            this.name = name;
            this.onEquals = onEquals;
        }

        Racy(String name) {
            this(name, new AtomicReference<>());
        }

        String name() {
            return name;
        }

        Racy withName(String newName) {
            return new Racy(newName);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            Runnable hook = onEquals.getAndSet(null);
            if (hook != null) {
                hook.run();
            }
            return other instanceof Racy racy && racy.name.equals(name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
