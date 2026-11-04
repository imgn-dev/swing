package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Observable;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.Scope;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.AboutDialog;
import be.imgn.swing.ui.Binding;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * How {@code Form.bind} shows an observable list in a {@link JComboBox} and binds its selection, on the counter's view.
 */
class ComboBindingTest {

    private static final String CHOICE = "A choice";

    @Test
    void showsTheItemsThroughANewModelAndMakesTheComboBoxNotEditable() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            app.run(() -> combo.setEditable(true));
            ComboBoxModel<String> before = app.call(combo::getModel);

            bind(app, combo, items, Property.of(Optional.empty()));

            assertThat(app.call(combo::getModel)).isNotSameAs(before);
            assertThat(elements(app, combo)).containsExactly("a", "b", "c");
            assertThat(app.call(combo::isEditable)).isFalse();
        }
    }

    @Test
    void firesTheModelEventOfEachChangeInOrder() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            List<String> events = new CopyOnWriteArrayList<>();
            bind(app, combo, items, Property.of(Optional.empty()));
            app.run(() -> combo.getModel().addListDataListener(recorder(events)));

            // Made off the EDT: each reaches the model, in order, however the updates batch them.
            items.add("d");
            items.add(0, "z");
            items.addAll(List.of("x", "y"));
            items.remove(1);
            items.set(0, "q");
            items.setAll(List.of("1", "2", "3", "4", "5", "6"));
            items.setAll(List.of("1", "2"));
            items.clear();
            app.run(() -> {});

            assertThat(events)
                    .containsExactly(
                            "added 3-3",
                            "added 0-0",
                            "added 5-6",
                            "removed 1-1",
                            "changed 0-0",
                            "changed 0-5",
                            "changed 0-1",
                            "removed 0-1");
            assertThat(elements(app, combo)).isEmpty();
        }
    }

    @Test
    void rendersTheTextOfEachElementAndOfTheSelectedItem() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            bind(app, combo, items, Property.of(Optional.of("b")));

            List<Object> rendered = app.call(() -> {
                ListCellRenderer<? super String> renderer = combo.getRenderer();
                JList<String> list = new JList<>();
                List<Object> found = new ArrayList<>();
                found.add(renderer);
                for (int index = 0; index < combo.getModel().getSize(); index++) {
                    found.add(render(renderer, list, combo.getModel().getElementAt(index), index));
                }
                // At no index: the selected item in the display area, then an element measured, then a value that is
                // no element, such as a prototype display value.
                found.add(render(renderer, list, combo.getSelectedItem(), -1));
                found.add(render(renderer, list, "c", -1));
                found.add(render(renderer, list, "wide", -1));
                return found;
            });

            assertThat(rendered.getFirst()).isInstanceOf(DefaultListCellRenderer.class);
            assertThat(rendered.subList(1, rendered.size())).containsExactly("A", "B", "C", "B", "C", "wide");
        }
    }

    @Test
    void selectsThePropertysValueWhenBoundAndIsClean() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);

            Binding<Optional<String>> binding = bind(app, combo, items, selection);

            assertThat(app.call(combo::getSelectedItem)).isEqualTo("b");
            assertThat(selection.get()).contains("b");
            assertThat(app.call(() -> form(app).dirty().get())).isFalse();
            assertThat(app.call(() -> binding.shown().get())).isFalse();
        }
    }

    @Test
    void clearsAndEmptiesAValueThatIsNotInTheListWhenBound() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.of("z"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);

            bind(app, combo, items, selection);

            assertThat(app.call(() -> Optional.ofNullable(combo.getSelectedItem())))
                    .isEmpty();
            assertThat(selection.get()).isEmpty();
            // The baseline is the value once bound, so that a form just bound is clean.
            assertThat(app.call(() -> form(app).dirty().get())).isFalse();
        }
    }

    @Test
    void aUserSelectionSetsThePropertyAndShowsTheError() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("a"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            Binding<Optional<String>> binding = bind(app, combo, items, selection);
            app.run(() -> binding.check(Rule.of(CHOICE, Optional::isPresent)));

            app.run(() -> combo.setSelectedIndex(2));

            assertThat(selection.get()).contains("c");
            assertThat(app.call(() -> binding.shown().get())).isTrue();
            assertThat(app.call(() -> form(app).dirty().get())).isTrue();
            assertThat(toolTip(app, combo)).isEmpty();

            app.run(() -> combo.setSelectedItem(null));

            assertThat(selection.get()).isEmpty();
            assertThat(app.call(() -> binding.validation().get().error())).contains(CHOICE);
            assertThat(toolTip(app, combo)).contains(CHOICE);

            app.run(() -> combo.setSelectedItem("a"));

            assertThat(selection.get()).contains("a");
            assertThat(app.call(() -> form(app).dirty().get())).isFalse();
            assertThat(toolTip(app, combo)).isEmpty();
        }
    }

    @Test
    void aUserSelectionUpdatesTheStateBeforeTheComboBoxsEventsReturn() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.empty());
        List<Object> seen = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            Binding<Optional<String>> binding = bind(app, combo, items, selection);
            Form form = form(app);
            app.run(() -> {
                binding.check(Rule.of(CHOICE, Optional::isPresent));
                combo.addItemListener(event -> seen.add(
                        "item " + form.valid().get() + " " + form.dirty().get()));
                combo.addActionListener(event -> seen.add("action " + selection.get()));
            });

            app.run(() -> {
                combo.setSelectedIndex(1);
                seen.add(binding.validation().get().valid());
            });

            assertThat(seen).containsExactly("item true true", "action Optional[b]", true);
        }
    }

    @Test
    void aRuleIsTestedOnceAtEachChangeOfTheSelection() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("a"));
        AtomicBoolean armed = new AtomicBoolean();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            Binding<Optional<String>> binding = bind(app, combo, items, selection);
            // Once armed, it throws a new exception at each test, so that the tester records each call of the
            // FailureHandler.
            app.run(() -> binding.check(Rule.of(CHOICE, (Optional<String> value) -> {
                if (armed.get()) {
                    throw new IllegalStateException("A bug in the rule");
                }
                return true;
            })));
            armed.set(true);

            failsOnce(app, () -> combo.setSelectedIndex(1));
            assertThat(selection.get()).contains("b");

            failsOnce(app, () -> selection.set(Optional.of("c")));

            selection.set(Optional.of("a"));
            failsOnce(app, () -> {});
            assertThat(app.call(combo::getSelectedItem)).isEqualTo("a");
        }
    }

    @Test
    void aChangeOfThePropertyOnAnyThreadSelectsItsValueAndIsNotAUserChange() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            Binding<Optional<String>> binding = bind(app, combo, items, selection);
            List<Optional<String>> values = record(app, selection);

            selection.set(Optional.of("c"));
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedItem)).isEqualTo("c");
            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(2);
            assertThat(app.call(() -> binding.shown().get())).isFalse();
            assertThat(app.call(() -> form(app).dirty().get())).isTrue();
            assertThat(values).containsExactly(Optional.of("c"));

            selection.set(Optional.empty());
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(-1);
            assertThat(app.call(() -> form(app).dirty().get())).isFalse();
        }
    }

    @Test
    void clearsTheSelectionAndEmptiesThePropertyForAValueNotInTheList() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.of("a"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            bind(app, combo, items, selection);

            selection.set(Optional.of("z"));
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(-1);
            assertThat(selection.get()).isEmpty();
        }
    }

    @Test
    void findsAnElementAddedThenSelected() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            bind(app, combo, items, selection);

            items.add("c");
            selection.set(Optional.of("c"));
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(2);
            assertThat(selection.get()).contains("c");
        }
    }

    @Test
    void keepsTheSelectionOnTheElementWhenChangesBeforeItShiftIt() {
        ObservableList<String> items = ObservableList.of("a", "b", "c", "d");
        Property<Optional<String>> selection = Property.of(Optional.of("c"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            bind(app, combo, items, selection);
            List<Optional<String>> values = record(app, selection);

            items.add(0, "z");
            items.addAll(List.of("x"));
            items.remove(1);
            items.remove(1);
            items.set(0, "y");
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedItem)).isEqualTo("c");
            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(1);
            assertThat(values).isEmpty();
        }
    }

    @Test
    void keepsTheSelectionOnAReplacedElementAndSetsThePropertyToIt() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            Binding<Optional<String>> binding = bind(app, combo, items, selection);

            items.set(1, "B");
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedItem)).isEqualTo("B");
            assertThat(selection.get()).contains("B");
            assertThat(app.call(() -> form(app).dirty().get())).isTrue();
            assertThat(app.call(() -> binding.shown().get())).isFalse();
        }
    }

    @Test
    void clearsTheSelectionAndEmptiesThePropertyWhenTheSelectedElementIsRemoved() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            bind(app, combo, items, selection);

            items.remove("b");
            app.run(() -> {});

            assertThat(app.call(() -> Optional.ofNullable(combo.getSelectedItem())))
                    .isEmpty();
            assertThat(selection.get()).isEmpty();
        }
    }

    @Test
    void movesTheSelectionToAnEqualElementAfterAReplacementOfAnotherSize() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            bind(app, combo, items, selection);
            List<Optional<String>> values = record(app, selection);

            items.setAll(List.of("x", "y", "b", "z"));
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(2);
            assertThat(values).isEmpty();

            items.setAll(List.of("x", "y"));
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(-1);
            assertThat(values).containsExactly(Optional.empty());
        }
    }

    @Test
    void matchesTheSelectedElementByEquality() {
        Item first = new Item(1, "one");
        Item second = new Item(2, "two");
        ObservableList<Item> items = ObservableList.of(first, second);
        Property<Optional<Item>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<Item> combo = app.call(() -> new JComboBox<Item>());
            Form form = form(app);
            app.call(() -> form.bind(combo, items, Item::name, selection));

            selection.set(Optional.of(new Item(2, "two")));
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(1);

            // Without a key, an element with the same id but another name is another element.
            selection.set(Optional.of(new Item(2, "deux")));
            app.run(() -> {});

            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(-1);
            assertThat(selection.get()).isEmpty();
        }
    }

    @Test
    void validateShowsTheErrorOnTheComboBoxAndFocusesIt() {
        ObservableList<String> items = ObservableList.of("a", "b");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            Window window = app.commands(Window.class);
            app.run(() -> {
                JPanel panel = new JPanel();
                panel.add(combo);
                window.rootPane().setContentPane(panel);
            });
            Binding<Optional<String>> binding = bind(app, combo, items, Property.of(Optional.empty()));
            app.run(() -> binding.check(Rule.of(CHOICE, Optional::isPresent)));
            assertThat(app.call(() -> form(app).valid().get())).isFalse();
            assertThat(toolTip(app, combo)).isEmpty();

            assertThat(app.call(form(app)::validate)).isFalse();

            assertThat(toolTip(app, combo)).contains(CHOICE);
            assertThat(app.focused()).containsSame(combo);
        }
    }

    @Test
    void rejectsASecondBinding() {
        ObservableList<String> items = ObservableList.of("a");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JComboBox<String> combo = newCombo(app);
            bind(app, combo, items, Property.of(Optional.empty()));

            assertThatThrownBy(() -> bind(app, combo, ObservableList.of("b"), Property.of(Optional.empty())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already bound");
            assertThat(elements(app, combo)).containsExactly("a");
        }
    }

    @Test
    void doesNothingOnceTheScopeIsClosed() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.empty());
        List<JComboBox<String>> combos = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                JComboBox<String> combo = new JComboBox<>();
                dialog.commands(Form.class).bind(combo, items, ComboBindingTest::upper, selection);
                combos.add(combo);
            });
            app.commands(StandardCommands.class).about().execute();

            items.add("c");
            selection.set(Optional.of("b"));
            app.run(() -> {});

            JComboBox<String> combo = combos.getFirst();
            assertThat(elements(app, combo)).containsExactly("a", "b");
            assertThat(app.call(combo::getSelectedIndex)).isEqualTo(-1);
        }
    }

    private static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    private static Form form(AppTester<CounterApp> app) {
        return app.commands(Form.class);
    }

    private static void failsOnce(AppTester<CounterApp> app, Runnable action) {
        assertThatThrownBy(() -> app.run(action))
                .isInstanceOf(AssertionError.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasNoSuppressedExceptions();
    }

    private static JComboBox<String> newCombo(AppTester<CounterApp> app) {
        return app.call(() -> new JComboBox<String>());
    }

    private static Binding<Optional<String>> bind(
            AppTester<CounterApp> app,
            JComboBox<String> combo,
            ObservableList<String> items,
            Property<Optional<String>> selection) {
        Form form = form(app);
        return app.call(() -> form.bind(combo, items, ComboBindingTest::upper, selection));
    }

    // The binding's renderer is a DefaultListCellRenderer, which renders any value.
    private static String render(
            ListCellRenderer<? super String> renderer, JList<String> list, @Nullable Object value, int index) {
        DefaultListCellRenderer any = (DefaultListCellRenderer) renderer;
        return ((JLabel) any.getListCellRendererComponent(list, value, index, false, false)).getText();
    }

    private static <E> List<E> elements(AppTester<CounterApp> app, JComboBox<E> combo) {
        return app.call(() -> {
            List<E> elements = new ArrayList<>();
            ComboBoxModel<E> model = combo.getModel();
            for (int index = 0; index < model.getSize(); index++) {
                elements.add(model.getElementAt(index));
            }
            return elements;
        });
    }

    private static Optional<String> toolTip(AppTester<CounterApp> app, JComboBox<?> combo) {
        return app.call(() -> Optional.ofNullable(combo.getToolTipText()));
    }

    // The values the property takes from now on.
    private static <T> List<T> record(AppTester<CounterApp> app, Observable<T> observable) {
        List<T> values = new CopyOnWriteArrayList<>();
        observable.subscribe(app.commands(Scope.class), values::add);
        return values;
    }

    private static ListDataListener recorder(List<String> events) {
        return new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent event) {
                events.add("added " + event.getIndex0() + "-" + event.getIndex1());
            }

            @Override
            public void intervalRemoved(ListDataEvent event) {
                events.add("removed " + event.getIndex0() + "-" + event.getIndex1());
            }

            @Override
            public void contentsChanged(ListDataEvent event) {
                events.add("changed " + event.getIndex0() + "-" + event.getIndex1());
            }
        };
    }

    record Item(int id, String name) {}
}
