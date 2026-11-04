package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Observable;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.AboutDialog;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.junit.jupiter.api.Test;

/** How {@code Ui.bind} shows an observable list in a {@link JList} and binds its selection, on the counter's view. */
class ListBindingTest {

    @Test
    void showsTheElementsThroughANewModel() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            ListModel<String> before = app.call(list::getModel);

            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper));

            assertThat(app.call(list::getModel)).isNotSameAs(before);
            assertThat(elements(app, list)).containsExactly("a", "b", "c");
        }
    }

    @Test
    void firesTheModelEventOfEachChangeInOrder() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            List<String> events = new CopyOnWriteArrayList<>();
            app.run(() -> {
                ui(app).bind(list, items, ListBindingTest::upper);
                list.getModel().addListDataListener(recorder(events));
            });

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
            assertThat(elements(app, list)).isEmpty();
        }
    }

    @Test
    void readsTheCopyWhichOnlyThePostedUpdateChanges() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper));

            // Even on the EDT, the change is posted, so it never lands in the middle of the code that made it.
            int sizeRightAfter = app.call(() -> {
                items.add("d");
                return list.getModel().getSize();
            });

            assertThat(sizeRightAfter).isEqualTo(3);
            assertThat(elements(app, list)).containsExactly("a", "b", "c", "d");
        }
    }

    @Test
    void viewBindingsSeeTheRowsAlreadyFollowingTheChange() {
        ObservableList<String> items = ObservableList.of("a", "b");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            List<String> seen = new CopyOnWriteArrayList<>();
            app.run(() -> {
                // Bound before the list, so that only the order of the groups puts the list's update first.
                ui(app).bind(
                                size -> seen.add(size + " in a model of "
                                        + list.getModel().getSize()),
                                items.map(List::size));
                ui(app).bind(list, items, ListBindingTest::upper);
            });

            items.add("c");
            app.run(() -> {});

            assertThat(seen).last().isEqualTo("3 in a model of 3");
        }
    }

    @Test
    void viewBindingsSeeTheRowsFollowingTheChangeEvenWhenTheEdtRunsBeforeTheListIsDoneNotifying() {
        ObservableList<String> items = ObservableList.of("a", "b");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            List<String> seen = new CopyOnWriteArrayList<>();
            Scope scope = app.bean(Scope.class);
            app.run(() -> {
                ui(app).bind(
                                size -> seen.add(size + " in a model of "
                                        + list.getModel().getSize()),
                                items.map(List::size));
                // Between the view's listener and the list's, the EDT runs whatever the change has posted so far.
                items.subscribe(scope, ignored -> {
                    try {
                        EventQueue.invokeAndWait(() -> {});
                    } catch (InterruptedException | InvocationTargetException e) {
                        throw new IllegalStateException(e);
                    }
                });
                ui(app).bind(list, items, ListBindingTest::upper);
            });

            items.add("c");
            app.run(() -> {});

            assertThat(seen).last().isEqualTo("3 in a model of 3");
        }
    }

    @Test
    void appliesTheChangesStillQueuedWhenAnObserverThrowsInTheNextDrain() {
        ObservableList<String> items = ObservableList.of("a", "b");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            IllegalStateException failure = new IllegalStateException("listener failed");
            app.run(() -> {
                ui(app).bind(list, items, ListBindingTest::upper);
                list.getModel().addListDataListener(failingOnce(failure));
            });

            // Made on the EDT, so that both changes are queued when the update runs.
            assertThatThrownBy(() -> app.run(() -> {
                        items.add("c");
                        items.add("d");
                    }))
                    .hasCause(failure);

            assertThat(elements(app, list)).containsExactly("a", "b", "c", "d");
        }
    }

    @Test
    void tracksTheSelectionThroughAChangeWhoseModelEventThrows() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            IllegalStateException failure = new IllegalStateException("listener failed");
            app.run(() -> {
                ui(app).bind(list, items, ListBindingTest::upper, selection);
                list.getModel().addListDataListener(failingOnce(failure));
            });

            assertThatThrownBy(() -> app.run(() -> items.add(0, "x"))).hasCause(failure);

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(2);
            assertThat(selection.get()).contains("b");
        }
    }

    @Test
    void followsChangesMadeOnSeveralThreads() throws Exception {
        ObservableList<String> items = ObservableList.of();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper));

            // The EDT checker fails the next call if a listener touched the component on these threads.
            Thread first =
                    Thread.ofPlatform().start(() -> IntStream.range(0, 200).forEach(n -> items.add("a" + n)));
            Thread second =
                    Thread.ofPlatform().start(() -> IntStream.range(0, 200).forEach(n -> items.add(0, "b" + n)));
            first.join();
            second.join();

            assertThat(elements(app, list)).isEqualTo(items.get());
        }
    }

    @Test
    void rendersTheTextOfEachElementWithADefaultListCellRenderer() {
        ObservableList<String> items = ObservableList.of("a", "b");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper));

            List<Object> rendered = app.call(() -> {
                List<Object> found = new ArrayList<>();
                found.add(list.getCellRenderer());
                for (int index = 0; index < list.getModel().getSize(); index++) {
                    JLabel label = (JLabel) list.getCellRenderer()
                            .getListCellRendererComponent(
                                    list, list.getModel().getElementAt(index), index, false, false);
                    found.add(label.getText());
                }
                return found;
            });

            assertThat(rendered.getFirst()).isInstanceOf(DefaultListCellRenderer.class);
            assertThat(rendered.subList(1, rendered.size())).containsExactly("A", "B");
        }
    }

    @Test
    void rejectsASecondBinding() {
        ObservableList<String> items = ObservableList.of("a");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper));

            assertThatThrownBy(() -> app.run(() -> ui(app).bind(list, ObservableList.of("b"), ListBindingTest::upper)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already bound");
            assertThatThrownBy(() -> app.run(() ->
                            ui(app).bind(list, items, ListBindingTest::upper, Property.of(Optional.<String>empty()))))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(elements(app, list)).containsExactly("a");
        }
    }

    @Test
    void doesNothingOnceTheScopeIsClosed() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.empty());
        List<JList<String>> lists = new ArrayList<>();
        List<String> events = new CopyOnWriteArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                JList<String> list = new JList<>();
                dialog.commands(Ui.class).bind(list, items, ListBindingTest::upper, selection);
                list.getModel().addListDataListener(recorder(events));
                lists.add(list);
                // Both updates are posted; the dialog closes, and its view's scope with it, before they run.
                items.add("c");
                selection.set(Optional.of("c"));
            });

            app.commands(StandardCommands.class).about().execute();

            JList<String> list = lists.getFirst();
            assertThat(elements(app, list)).containsExactly("a", "b");
            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
            assertThat(events).isEmpty();
        }
    }

    @Test
    void setsTheSingleSelectionModeAndSelectsThePropertysValueWhenBound() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);

            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            assertThat(app.call(list::getSelectionMode)).isEqualTo(ListSelectionModel.SINGLE_SELECTION);
            assertThat(app.call(list::getSelectedIndex)).isEqualTo(1);
            assertThat(selection.get()).contains("b");
        }
    }

    @Test
    void clearsAndEmptiesAValueThatIsNotInTheListWhenBound() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.of("z"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);

            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
            assertThat(selection.get()).isEmpty();
        }
    }

    @Test
    void setsThePropertyWhenTheUserSelectsOnceTheSelectionNoLongerAdjusts() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            Optional<String> whileAdjusting = app.call(() -> {
                list.setValueIsAdjusting(true);
                list.setSelectedIndex(2);
                Optional<String> value = selection.get();
                list.setValueIsAdjusting(false);
                return value;
            });

            assertThat(whileAdjusting).isEmpty();
            assertThat(selection.get()).contains("c");

            app.run(list::clearSelection);

            assertThat(selection.get()).isEmpty();
        }
    }

    @Test
    void selectsTheValueWhenThePropertyChangesAndScrollsToIt() {
        ObservableList<String> items = ObservableList.copyOf(
                IntStream.range(0, 50).mapToObj(n -> "item " + n).toList());
        Property<Optional<String>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            JScrollPane scroll = app.call(() -> {
                ui(app).bind(list, items, ListBindingTest::upper, selection);
                list.setFixedCellHeight(10);
                JScrollPane pane = new JScrollPane(list);
                pane.setSize(new Dimension(100, 60));
                pane.validate();
                return pane;
            });

            // Set off the EDT: the update selects on the EDT.
            selection.set(Optional.of("item 40"));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(40);
            assertThat(app.call(() -> scroll.getViewport().getViewPosition().y)).isPositive();

            selection.set(Optional.empty());
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
        }
    }

    @Test
    void clearsTheSelectionAndEmptiesThePropertyForAValueNotInTheList() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.of("a"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            selection.set(Optional.of("z"));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
            assertThat(selection.get()).isEmpty();
        }
    }

    @Test
    void findsAnElementAddedThenSelected() {
        ObservableList<String> items = ObservableList.of("a", "b");
        Property<Optional<String>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            items.add("c");
            selection.set(Optional.of("c"));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(2);
            assertThat(selection.get()).contains("c");
        }
    }

    @Test
    void selectsTheNewValueWhenThePropertyAndTheListChangeBeforeOneSettle() {
        ObservableList<String> items = ObservableList.of("a", "b", "x");
        Property<Optional<String>> selection = Property.of(Optional.of("a"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            // On the EDT, so that both precede the updates: the list's change doesn't keep the old selection.
            app.run(() -> {
                selection.set(Optional.of("x"));
                items.add(0, "z");
            });

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(3);
            assertThat(selection.get()).contains("x");
        }
    }

    @Test
    void movesTheSelectionToAnEqualElementAfterAReplacementOfTheSameSizeThatCoversIt() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));
            List<Optional<String>> values = record(app, selection);

            items.setAll(List.of("b", "x", "y"));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isZero();
            assertThat(values).isEmpty();

            items.setAll(List.of("p", "q", "r"));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
            assertThat(selection.get()).isEmpty();
            assertThat(values).containsExactly(Optional.empty());
        }
    }

    @Test
    void keepsTheSelectionOnTheElementWhenChangesBeforeItShiftIt() {
        ObservableList<String> items = ObservableList.of("a", "b", "c", "d");
        Property<Optional<String>> selection = Property.of(Optional.of("c"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));
            List<Optional<String>> values = record(app, selection);

            items.add(0, "z");
            items.addAll(List.of("x"));
            items.remove(1);
            items.remove(1);
            items.set(0, "y");
            app.run(() -> {});

            assertThat(app.call(list::getSelectedValue)).isEqualTo("c");
            assertThat(app.call(list::getSelectedIndex)).isEqualTo(1);
            assertThat(values).isEmpty();
        }
    }

    @Test
    void keepsTheSelectionOnAReplacedElementAndSetsThePropertyToIt() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            items.set(1, "B");
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(1);
            assertThat(selection.get()).contains("B");
        }
    }

    @Test
    void movesTheSelectionToAnEqualElementWhenTheSelectedOneIsRemoved() {
        ObservableList<String> items = ObservableList.of("a", "b", "c", "b");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));
            List<Optional<String>> values = record(app, selection);

            items.remove(1);
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(2);
            assertThat(selection.get()).contains("b");
            assertThat(values).isEmpty();
        }
    }

    @Test
    void clearsTheSelectionAndEmptiesThePropertyWhenTheSelectedElementIsRemoved() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            items.remove("b");
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
            assertThat(selection.get()).isEmpty();
        }
    }

    @Test
    void movesTheSelectionToAnEqualElementAfterAReplacementOfAnotherSize() {
        ObservableList<String> items = ObservableList.of("a", "b", "c");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));
            List<Optional<String>> values = record(app, selection);

            items.setAll(List.of("x", "y", "b", "z"));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(2);
            assertThat(values).isEmpty();

            items.setAll(List.of("x", "y"));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
            assertThat(values).containsExactly(Optional.empty());
        }
    }

    @Test
    void keepsTheElementTheUserSelectedAmongEqualOnes() {
        ObservableList<String> items = ObservableList.of("a", "b", "a");
        Property<Optional<String>> selection = Property.of(Optional.of("b"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<String> list = newList(app);
            app.run(() -> ui(app).bind(list, items, ListBindingTest::upper, selection));

            app.run(() -> list.setSelectedIndex(2));

            assertThat(selection.get()).contains("a");
            assertThat(app.call(list::getSelectedIndex)).isEqualTo(2);
        }
    }

    @Test
    void keepsTheElementWithTheSelectedKeyWhereverItMoved() {
        Item first = new Item(1, "one");
        Item second = new Item(2, "two");
        Item third = new Item(3, "three");
        ObservableList<Item> items = ObservableList.of(first, second, third);
        Property<Optional<Item>> selection = Property.of(Optional.of(second));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<Item> list = app.call(() -> new JList<Item>());
            app.run(() -> ui(app).bind(list, items, Item::name, selection, Item::id));

            Item renamed = new Item(2, "deux");
            items.setAll(List.of(third, first, renamed));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(2);
            assertThat(selection.get()).contains(renamed);
        }
    }

    @Test
    void aReplacedElementWithAnotherKeyLosesTheSelection() {
        Item first = new Item(1, "one");
        Item second = new Item(2, "two");
        ObservableList<Item> items = ObservableList.of(first, second);
        Property<Optional<Item>> selection = Property.of(Optional.of(second));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<Item> list = app.call(() -> new JList<Item>());
            app.run(() -> ui(app).bind(list, items, Item::name, selection, Item::id));

            items.set(1, new Item(9, "two"));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
            assertThat(selection.get()).isEmpty();
        }
    }

    @Test
    void selectsTheElementWithTheValuesKey() {
        Item first = new Item(1, "one");
        Item second = new Item(2, "two");
        ObservableList<Item> items = ObservableList.of(first, second);
        Property<Optional<Item>> selection = Property.of(Optional.empty());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JList<Item> list = app.call(() -> new JList<Item>());
            app.run(() -> ui(app).bind(list, items, Item::name, selection, Item::id));

            selection.set(Optional.of(new Item(2, "stale")));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(1);

            selection.set(Optional.of(new Item(7, "seven")));
            app.run(() -> {});

            assertThat(app.call(list::getSelectedIndex)).isEqualTo(-1);
            assertThat(selection.get()).isEmpty();
        }
    }

    private static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    private static Ui ui(AppTester<CounterApp> app) {
        return app.commands(Ui.class);
    }

    private static JList<String> newList(AppTester<CounterApp> app) {
        return app.call(() -> new JList<String>());
    }

    private static <E> List<E> elements(AppTester<CounterApp> app, JList<E> list) {
        return app.call(() -> {
            List<E> elements = new ArrayList<>();
            ListModel<E> model = list.getModel();
            for (int index = 0; index < model.getSize(); index++) {
                elements.add(model.getElementAt(index));
            }
            return elements;
        });
    }

    // The values the property takes from now on.
    private static <T> List<T> record(AppTester<CounterApp> app, Observable<T> observable) {
        List<T> values = new CopyOnWriteArrayList<>();
        observable.subscribe(app.commands(Scope.class), values::add);
        return values;
    }

    private static ListDataListener failingOnce(RuntimeException failure) {
        AtomicBoolean failed = new AtomicBoolean();
        return new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent event) {
                if (failed.compareAndSet(false, true)) {
                    throw failure;
                }
            }

            @Override
            public void intervalRemoved(ListDataEvent event) {}

            @Override
            public void contentsChanged(ListDataEvent event) {}
        };
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
