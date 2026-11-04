package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Check;
import be.imgn.swing.Check.Status;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.Scope;
import be.imgn.swing.Validation;
import be.imgn.swing.Validator;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.AboutDialog;
import be.imgn.swing.ui.Binding;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.CommandRegistry;
import be.imgn.swing.ui.Converter;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Window;
import java.awt.CardLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;

/**
 * The form of a view, on the counter's main view: its bindings, conversion, validation, feedback and dirty tracking.
 * The tester keeps FlatLaf's validation feedback, which {@code swing-flatlaf}, on the examples' path, provides.
 */
class FormTest {

    private static final String REQUIRED = "Required";
    private static final String SHORT = "At most 5 characters";
    private static final String WHOLE = "A whole number";
    private static final String SMALL = "At most 10";
    private static final String OUTLINE = "JComponent.outline";
    private static final Converter<Integer> NUMBER = Converter.of(Integer::valueOf, String::valueOf, WHOLE);

    private static Form form(AppTester<CounterApp> app) {
        return app.commands(Form.class);
    }

    private static JTextField field(AppTester<CounterApp> app, Property<String> property) {
        Form form = form(app);
        return app.call(() -> {
            JTextField field = new JTextField();
            form.bind(field, property);
            return field;
        });
    }

    // Makes components the content of the main window, where the form looks for them when it focuses one.
    private static void show(AppTester<CounterApp> app, JComponent... components) {
        Window window = app.commands(Window.class);
        app.run(() -> {
            JPanel panel = new JPanel();
            for (JComponent component : components) {
                panel.add(component);
            }
            window.rootPane().setContentPane(panel);
        });
    }

    private static String text(AppTester<CounterApp> app, JTextField field) {
        return app.call(field::getText);
    }

    private static Optional<String> toolTip(AppTester<CounterApp> app, JComponent component) {
        return app.call(() -> Optional.ofNullable(component.getToolTipText()));
    }

    private static Optional<String> description(AppTester<CounterApp> app, JComponent component) {
        return app.call(
                () -> Optional.ofNullable(component.getAccessibleContext().getAccessibleDescription()));
    }

    private static Optional<Object> outline(AppTester<CounterApp> app, JComponent component) {
        return app.call(() -> Optional.ofNullable(component.getClientProperty(OUTLINE)));
    }

    private static List<Status> statuses(Validation validation) {
        return validation.checks().stream().map(Check::status).toList();
    }

    // A rule that, once armed, throws a new exception at each test, so that the tester records each call of the
    // FailureHandler.
    private static <T> Rule<T> breaking(AtomicBoolean armed) {
        return Rule.of("Not broken", value -> {
            if (armed.get()) {
                throw new IllegalStateException("A bug in the rule");
            }
            return true;
        });
    }

    private static void failsOnce(AppTester<CounterApp> app, Runnable action) {
        assertThatThrownBy(() -> app.run(action))
                .isInstanceOf(AssertionError.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasNoSuppressedExceptions();
    }

    @Test
    void setsTheTextFromThePropertyWhenBound() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTextField field = field(app, Property.of("Ada"));

            assertThat(text(app, field)).isEqualTo("Ada");
        }
    }

    @Test
    void aChangeOfTheDocumentSetsTheProperty() {
        Property<String> name = Property.of("Ada");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTextField field = field(app, name);

            app.run(() -> field.setText("Grace"));
            assertThat(name.get()).isEqualTo("Grace");

            app.run(() -> field.replaceSelection("!"));
            assertThat(name.get()).isEqualTo("Grace!");
        }
    }

    @Test
    void aChangeOfThePropertyOnAnyThreadSetsTheText() {
        Property<String> name = Property.of("Ada");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTextField field = field(app, name);

            name.set("Lin");

            assertThat(text(app, field)).isEqualTo("Lin");
        }
    }

    @Test
    void itsOwnChangesOfTheTextDontSetTheProperty() {
        Property<String> name = Property.of("Ada");
        List<String> values = new CopyOnWriteArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTextField field = field(app, name);
            name.subscribe(app.commands(Scope.class), values::add);

            name.set("Lin");
            app.call(field::getText);

            assertThat(values).containsExactly("Lin");
        }
    }

    @Test
    void listensToTheDocumentTheFieldHasWhenBound() {
        Property<String> name = Property.of("Ada");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTextField field = field(app, name);
            app.run(() -> {
                JTextField sharing = new JTextField(field.getDocument(), null, 0);
                field.setDocument(new JTextField().getDocument());
                field.setText("Grace");
                sharing.setText("Lin");
            });

            assertThat(name.get()).isEqualTo("Lin");
        }
    }

    @Test
    void leavesATextThatParsesToTheValueAsTheUserTypedIt() {
        Property<Integer> count = Property.of(1);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(() -> {
                JTextField created = new JTextField();
                form.bind(created, count, NUMBER);
                return created;
            });
            app.run(() -> field.setText("007"));
            app.run(() -> field.setCaretPosition(1));

            // Set on the EDT, so that both changes come before the update they cause.
            app.run(() -> {
                count.set(8);
                count.set(7);
            });

            assertThat(text(app, field)).isEqualTo("007");
            assertThat(app.call(field::getCaretPosition)).isEqualTo(1);
        }
    }

    @Test
    void theUpdateOfAnEarlierChangeDoesntInterruptTyping() {
        Property<Integer> count = Property.of(0);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);

            app.run(() -> {
                form.bind(field, count, NUMBER);
                field.setText("1");
                field.replaceSelection("x");
            });

            assertThat(count.get()).isEqualTo(1);
            assertThat(text(app, field)).isEqualTo("1x");
        }
    }

    @Test
    void aRejectedTextLeavesThePropertyUnchangedAndTheRulesUnknown() {
        Property<Integer> count = Property.of(5);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<Integer> binding = app.call(
                    () -> form.bind(field, count, NUMBER).check(Rule.of(SMALL, (Integer value) -> value <= 10)));

            app.run(() -> field.setText("five"));

            assertThat(count.get()).isEqualTo(5);
            Validation validation = app.call(() -> binding.validation().get());
            assertThat(validation.checks())
                    .containsExactly(new Check(WHOLE, Status.UNMET), new Check(SMALL, Status.UNKNOWN));
            assertThat(validation.error()).contains(WHOLE);
            assertThat(app.call(() -> form.valid().get())).isFalse();
            assertThat(app.call(() -> form.dirty().get())).isFalse();

            app.run(() -> field.setText("12"));

            assertThat(count.get()).isEqualTo(12);
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.MET, Status.UNMET);

            app.run(() -> field.setText("7"));

            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.MET, Status.MET);
            assertThat(app.call(() -> form.valid().get())).isTrue();
        }
    }

    @Test
    void aChangeOfThePropertyReplacesARejectedText() {
        Property<Integer> count = Property.of(5);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<Integer> binding = app.call(() -> form.bind(field, count, NUMBER));
            app.run(() -> field.setText("five"));

            count.set(6);

            assertThat(text(app, field)).isEqualTo("6");
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.MET);
        }
    }

    @Test
    void anOptionalConverterTakesABlankTextForEmpty() {
        Property<Optional<LocalDate>> birthDate = Property.of(Optional.of(LocalDate.of(1815, 12, 10)));
        Converter<Optional<LocalDate>> date =
                Converter.optional(Converter.of(LocalDate::parse, LocalDate::toString, "A date"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<Optional<LocalDate>> binding = app.call(() -> form.bind(field, birthDate, date));
            assertThat(text(app, field)).isEqualTo("1815-12-10");

            app.run(() -> field.setText("  "));
            assertThat(birthDate.get()).isEmpty();

            app.run(() -> field.setText("1906-12-09"));
            assertThat(birthDate.get()).contains(LocalDate.of(1906, 12, 9));

            app.run(() -> field.replaceSelection(" or so"));
            assertThat(birthDate.get()).contains(LocalDate.of(1906, 12, 9));
            assertThat(app.call(() -> binding.validation().get().error())).contains("A date");

            birthDate.set(Optional.empty());
            assertThat(text(app, field)).isEmpty();
        }
    }

    @Test
    void testsEveryRuleInTheOrderTheyWereAdded() {
        Property<String> password = Property.of("");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<String> binding = app.call(() -> form.bind(field, password)
                    .check(Rule.required(REQUIRED))
                    .check(Validator.<String>of(
                            Rule.of("At least 3 characters", s -> s.length() >= 3),
                            Rule.of("A digit", s -> s.chars().anyMatch(Character::isDigit)))));

            app.run(() -> field.setText("ab"));

            Validation validation = app.call(() -> binding.validation().get());
            assertThat(validation.checks())
                    .containsExactly(
                            new Check(REQUIRED, Status.MET),
                            new Check("At least 3 characters", Status.UNMET),
                            new Check("A digit", Status.UNMET));
            assertThat(validation.error()).contains("At least 3 characters");

            app.run(() -> field.setText("ab1"));

            assertThat(app.call(() -> binding.validation().get().valid())).isTrue();
        }
    }

    @Test
    void isValidatedWhenBoundAndAtEachChangeOfItsProperty() {
        Property<String> name = Property.of("");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<String> binding = app.call(() -> form.bind(field, name).check(Rule.required(REQUIRED)));
            assertThat(app.call(() -> binding.validation().get().valid())).isFalse();
            assertThat(app.call(() -> form.valid().get())).isFalse();

            name.set("Ada");

            assertThat(app.call(() -> binding.validation().get().valid())).isTrue();
            assertThat(app.call(() -> form.valid().get())).isTrue();
        }
    }

    @Test
    void revalidatesOnAnotherObservableWithoutShowingTheError() {
        Property<String> password = Property.of("secret");
        Property<String> confirmation = Property.of("secret");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<String> binding = app.call(() -> form.bind(field, confirmation)
                    .check(Rule.of("Matches the password", s -> s.equals(password.get())))
                    .revalidateOn(password));
            assertThat(app.call(() -> binding.validation().get().valid())).isTrue();

            password.set("other");

            assertThat(app.call(() -> binding.validation().get().error())).contains("Matches the password");
            assertThat(app.call(() -> binding.shown().get())).isFalse();
            assertThat(toolTip(app, field)).isEmpty();
        }
    }

    @Test
    void showsTheErrorOnceTheUserChangesTheComponent() {
        Property<String> name = Property.of("Ada");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<String> binding = app.call(() -> form.bind(field, name).check(Rule.required(REQUIRED)));

            name.set("");

            assertThat(app.call(() -> binding.shown().get())).isFalse();
            assertThat(toolTip(app, field)).isEmpty();

            app.run(() -> field.setText(" "));

            assertThat(app.call(() -> binding.shown().get())).isTrue();
            assertThat(toolTip(app, field)).contains(REQUIRED);
            assertThat(description(app, field)).contains(REQUIRED);
            assertThat(outline(app, field)).contains("error");
        }
    }

    @Test
    void isValidatedFromValidateUntilTheUserChangesTheComponent() {
        Property<String> name = Property.of("");
        Property<String> other = Property.of("");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<String> binding = app.call(
                    () -> form.bind(field, name).check(Rule.required(REQUIRED)).revalidateOn(other));
            List<Boolean> seen = new ArrayList<>();
            seen.add(app.call(() -> binding.validated().get()));

            app.call(form::validate);
            seen.add(app.call(() -> binding.validated().get()));
            name.set("Ada");
            other.set("changed");
            seen.add(app.call(() -> binding.validated().get()));
            app.run(() -> field.setText(""));
            seen.add(app.call(() -> binding.validated().get()));
            app.call(form::validate);
            seen.add(app.call(() -> binding.validated().get()));

            assertThat(seen).containsExactly(false, true, true, false, true);
            assertThat(app.call(() -> binding.shown().get())).isTrue();
        }
    }

    @Test
    void showsAChangedErrorAgainAndHidesItOnceThereIsNone() {
        Property<String> name = Property.of("Ada");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            app.call(() ->
                    form.bind(field, name).check(Rule.required(REQUIRED)).check(Rule.of(SHORT, s -> s.length() <= 5)));

            app.run(() -> field.setText(""));
            assertThat(toolTip(app, field)).contains(REQUIRED);

            app.run(() -> field.setText("Adeline"));
            assertThat(toolTip(app, field)).contains(SHORT);
            assertThat(description(app, field)).contains(SHORT);

            app.run(() -> field.setText("Ada"));
            assertThat(toolTip(app, field)).isEmpty();
            assertThat(description(app, field)).isEmpty();
            assertThat(outline(app, field)).isEmpty();

            name.set("");
            assertThat(toolTip(app, field)).contains(REQUIRED);
        }
    }

    @Test
    void showsTheErrorOfATextAreaOnItsScrollPane() {
        Property<String> notes = Property.of("Some notes");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextArea area = app.call(JTextArea::new);
            JScrollPane pane = app.call(() -> new JScrollPane(area));
            app.call(() -> form.bind(area, notes).check(Rule.required(REQUIRED)));

            app.run(() -> area.setText(""));

            assertThat(toolTip(app, pane)).contains(REQUIRED);
            assertThat(outline(app, pane)).contains("error");
            assertThat(toolTip(app, area)).isEmpty();
            assertThat(outline(app, area)).isEmpty();

            app.run(() -> area.setText("Other notes"));

            assertThat(toolTip(app, pane)).isEmpty();
            assertThat(outline(app, pane)).isEmpty();
        }
    }

    @Test
    void bindsTheSelectedStateOfAButton() {
        Property<Boolean> wrap = Property.of(true);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JCheckBox box = app.call(JCheckBox::new);
            Binding<Boolean> binding = app.call(() -> form.bind(box, wrap));
            assertThat(app.call(box::isSelected)).isTrue();

            wrap.set(false);

            assertThat(app.call(box::isSelected)).isFalse();
            assertThat(app.call(() -> binding.shown().get())).isFalse();

            app.run(box::doClick);

            assertThat(wrap.get()).isTrue();
            assertThat(app.call(() -> binding.shown().get())).isTrue();
        }
    }

    @Test
    void checksTheValueOfAButton() {
        Property<Boolean> accepted = Property.of(false);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JCheckBox box = app.call(JCheckBox::new);
            Binding<Boolean> binding =
                    app.call(() -> form.bind(box, accepted).check(Rule.of("Accepted", (Boolean value) -> value)));

            app.run(box::doClick);
            app.run(box::doClick);

            assertThat(app.call(() -> binding.validation().get().error())).contains("Accepted");
            assertThat(toolTip(app, box)).contains("Accepted");
        }
    }

    @Test
    void validateShowsEveryErrorFocusesTheFirstAndReturnsWhetherTheFormIsValid() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField name = app.call(JTextField::new);
            JTextField city = app.call(JTextField::new);
            JTextField street = app.call(JTextField::new);
            show(app, street, name, city);
            Binding<String> first =
                    app.call(() -> form.bind(name, Property.of("")).check(Rule.required(REQUIRED)));
            app.call(() -> form.bind(street, Property.of("Main Street")));
            app.call(() -> form.bind(city, Property.of("")).check(Rule.required(REQUIRED)));

            boolean valid = app.call(form::validate);

            assertThat(valid).isFalse();
            assertThat(app.call(() -> first.shown().get())).isTrue();
            assertThat(toolTip(app, name)).contains(REQUIRED);
            assertThat(toolTip(app, city)).contains(REQUIRED);
            assertThat(toolTip(app, street)).isEmpty();
            assertThat(app.focused()).containsSame(name);
        }
    }

    @Test
    void validateOfAValidFormFocusesNothing() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField name = app.call(JTextField::new);
            show(app, name);
            app.call(() -> form.bind(name, Property.of("Ada")).check(Rule.required(REQUIRED)));

            assertThat(app.call(form::validate)).isTrue();
            assertThat(app.focused()).isEmpty();
        }
    }

    @Test
    void aFormThatBindsNothingIsValidAndClean() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);

            assertThat(app.call(() -> form.valid().get())).isTrue();
            assertThat(app.call(() -> form.dirty().get())).isFalse();
            assertThat(app.call(form::validate)).isTrue();
        }
    }

    @Test
    void validateSelectsTheTabsOfTheComponentFromTheOutermostPaneIn() {
        List<String> selections = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            JTabbedPane outer = app.call(JTabbedPane::new);
            JTabbedPane inner = app.call(JTabbedPane::new);
            app.run(() -> {
                inner.addTab("Home", new JPanel());
                inner.addTab("Office", field);
                outer.addTab("Name", new JPanel());
                outer.addTab("Addresses", inner);
                outer.addChangeListener(event -> selections.add("outer"));
                inner.addChangeListener(event -> selections.add("inner"));
            });
            show(app, outer);
            app.call(() -> form.bind(field, Property.of("")).check(Rule.required(REQUIRED)));
            assertThat(app.call(field::isVisible)).isFalse();

            app.call(form::validate);

            assertThat(app.call(outer::getSelectedIndex)).isEqualTo(1);
            assertThat(app.call(inner::getSelectedIndex)).isEqualTo(1);
            assertThat(selections).containsExactly("outer", "inner");
            assertThat(app.focused()).containsSame(field);
        }
    }

    @Test
    void validateDoesntFocusAComponentThatIsntShowing() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            JPanel cards = app.call(() -> new JPanel(new CardLayout()));
            app.run(() -> {
                cards.add(new JPanel(), "first");
                cards.add(field, "second");
            });
            show(app, cards);
            app.call(() -> form.bind(field, Property.of("")).check(Rule.required(REQUIRED)));

            assertThat(app.call(form::validate)).isFalse();
            assertThat(toolTip(app, field)).contains(REQUIRED);
            assertThat(app.focused()).isEmpty();
        }
    }

    @Test
    void validateDoesntFocusAComponentOutsideItsWindow() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            app.call(() -> form.bind(field, Property.of("")).check(Rule.required(REQUIRED)));

            assertThat(app.call(form::validate)).isFalse();
            assertThat(app.focused()).isEmpty();
        }
    }

    @Test
    void isDirtyWhileAValueDiffersFromItsBaseline() {
        Property<String> name = Property.of("Ada");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = field(app, name);
            assertThat(app.call(() -> form.dirty().get())).isFalse();

            app.run(() -> field.setText("Grace"));
            assertThat(app.call(() -> form.dirty().get())).isTrue();

            app.run(() -> field.setText("Ada"));
            assertThat(app.call(() -> form.dirty().get())).isFalse();

            name.set("Lin");
            assertThat(app.call(() -> form.dirty().get())).isTrue();
        }
    }

    @Test
    void aRejectedTextDoesntMakeTheFormDirty() {
        Property<Integer> count = Property.of(3);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            app.call(() -> form.bind(field, count, NUMBER));

            app.run(() -> field.setText("three"));

            assertThat(app.call(() -> form.dirty().get())).isFalse();
        }
    }

    @Test
    void tracksAnObservableWithoutComponent() {
        Property<List<String>> tags = Property.of(List.of("work"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            app.run(() -> form.track(tags));

            tags.set(List.of("work", "home"));
            assertThat(app.call(() -> form.dirty().get())).isTrue();

            tags.set(List.of("work"));
            assertThat(app.call(() -> form.dirty().get())).isFalse();
        }
    }

    @Test
    void markCleanMakesEveryCurrentValueTheNewBaseline() {
        Property<String> name = Property.of("Ada");
        Property<List<String>> tags = Property.of(List.of("work"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = field(app, name);
            app.run(() -> form.track(tags));
            app.run(() -> field.setText("Grace"));
            tags.set(List.of("home"));
            assertThat(app.call(() -> form.dirty().get())).isTrue();

            app.run(form::markClean);
            assertThat(app.call(() -> form.dirty().get())).isFalse();

            app.run(() -> field.setText("Ada"));
            assertThat(app.call(() -> form.dirty().get())).isTrue();

            app.run(() -> field.setText("Grace"));
            tags.set(List.of("work"));
            assertThat(app.call(() -> form.dirty().get())).isTrue();
        }
    }

    @Test
    void aUserChangeUpdatesTheStateBeforeItsEventReturns() {
        Property<String> name = Property.of("Ada");
        List<Object> seen = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<String> binding = app.call(() -> form.bind(field, name).check(Rule.required(REQUIRED)));

            app.run(() -> {
                field.setText("");
                seen.add(binding.validation().get().valid());
                seen.add(form.valid().get());
                seen.add(form.dirty().get());
            });

            assertThat(seen).containsExactly(false, false, true);
        }
    }

    @Test
    void aCommandEnabledWhileTheFormIsDirtySeesAUserChangeAtOnce() {
        Property<String> name = Property.of("Ada");
        AtomicInteger saves = new AtomicInteger();
        List<Boolean> executed = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            CommandRegistry registry = app.commands(CommandRegistry.class);
            JTextField field = field(app, name);
            BoundCommand save = app.call(() -> registry.command(FormTest.class, "save")
                    .handler(context -> saves.incrementAndGet())
                    .enabled(form::dirty)
                    .bind());

            app.run(() -> {
                executed.add(save.execute());
                field.setText("Grace");
                executed.add(save.execute());
                form.markClean();
                executed.add(save.execute());
            });

            assertThat(executed).containsExactly(false, true, false);
            assertThat(saves).hasValue(1);
        }
    }

    @Test
    void aUserChangeRevalidatesTheBindingsThatDependOnItBeforeItsEventReturns() {
        Property<String> password = Property.of("secret");
        Property<String> confirmation = Property.of("secret");
        List<Boolean> executed = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            CommandRegistry registry = app.commands(CommandRegistry.class);
            JTextField passwordField = field(app, password);
            JTextField confirmationField = app.call(JTextField::new);
            Binding<String> confirming = app.call(() -> form.bind(confirmationField, confirmation)
                    .check(Rule.of("Matches the password", s -> s.equals(password.get())))
                    .revalidateOn(password));
            BoundCommand change = app.call(() -> registry.command(FormTest.class, "change")
                    .handler(context -> {})
                    .enabled(form::valid)
                    .bind());

            app.run(() -> {
                passwordField.setText("other");
                executed.add(confirming.validation().get().valid());
                executed.add(change.execute());
                confirmationField.setText("other");
                executed.add(change.execute());
            });

            assertThat(executed).containsExactly(false, false, true);
        }
    }

    @Test
    void aUserChangeRevalidatesAnotherBindingOfTheSamePropertyAtOnce() {
        Property<String> name = Property.of("Ada");
        List<Boolean> seen = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField first = field(app, name);
            JTextField second = app.call(JTextField::new);
            Binding<String> other = app.call(() -> form.bind(second, name).check(Rule.required(REQUIRED)));

            app.run(() -> {
                first.setText("");
                seen.add(other.validation().get().valid());
                seen.add(form.validate());
            });

            assertThat(seen).containsExactly(false, false);
        }
    }

    @Test
    void aChangeEventOfTheDocumentIsAUserChange() {
        Property<String> name = Property.of("Ada");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextPane pane = app.call(JTextPane::new);
            Binding<String> binding = app.call(() -> form.bind(pane, name));
            SimpleAttributeSet bold = new SimpleAttributeSet();
            StyleConstants.setBold(bold, true);

            app.run(() -> {
                // The property changes on the EDT, and before its update reaches the pane, the document changes: the
                // change event sets the property to the pane's text.
                name.set("Lin");
                pane.getStyledDocument().setCharacterAttributes(0, 3, bold, false);
            });

            assertThat(name.get()).isEqualTo("Ada");
            String text = app.call(pane::getText);
            assertThat(text).isEqualTo("Ada");
            assertThat(app.call(() -> binding.shown().get())).isTrue();
        }
    }

    @Test
    void aUserChangeOfAButtonUpdatesTheStateBeforeItsEventReturns() {
        Property<Boolean> accepted = Property.of(false);
        List<Object> seen = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JCheckBox box = app.call(JCheckBox::new);
            Binding<Boolean> binding =
                    app.call(() -> form.bind(box, accepted).check(Rule.of("Accepted", (Boolean value) -> value)));

            app.run(() -> {
                box.doClick();
                seen.add(binding.validation().get().valid());
                seen.add(form.valid().get());
                seen.add(form.dirty().get());
            });

            assertThat(seen).containsExactly(true, true, true);
        }
    }

    @Test
    void leavesATextThatEqualsTheValueAsTheUserTypedIt() {
        Property<String> name = Property.of("Ada");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTextField field = field(app, name);
            app.run(() -> field.setCaretPosition(1));

            // Set on the EDT, so that both changes come before the update they cause.
            app.run(() -> {
                name.set("Grace");
                name.set("Ada");
            });

            assertThat(text(app, field)).isEqualTo("Ada");
            assertThat(app.call(field::getCaretPosition)).isEqualTo(1);
        }
    }

    @Test
    void aRuleThatThrowsGoesToTheFailureHandlerAndIsUnknownWhileTheOthersAreTested() {
        Property<String> name = Property.of("Ada");
        IllegalStateException bug = new IllegalStateException("A bug in the rule");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<String> binding = app.call(() -> form.bind(field, name)
                    .check(Rule.required(REQUIRED))
                    .check(Rule.of("Not boom", s -> {
                        if (s.equals("boom")) {
                            throw bug;
                        }
                        return true;
                    }))
                    .check(Rule.of("At least 5 characters", s -> s.length() >= 5)));

            assertThatThrownBy(() -> app.run(() -> field.setText("boom")))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("The application failed")
                    .hasCause(bug);

            assertThat(name.get()).isEqualTo("boom");
            assertThat(statuses(app.call(() -> binding.validation().get())))
                    .containsExactly(Status.MET, Status.UNKNOWN, Status.UNMET);
            assertThat(app.call(() -> form.valid().get())).isFalse();
        }
    }

    @Test
    void anErrorThrownByARulePropagates() {
        Property<String> name = Property.of("Ada");
        InternalError failure = new InternalError("A broken invariant");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            app.run(() -> form.bind(field, name).check(Rule.of("Not boom", s -> {
                if (s.equals("boom")) {
                    throw failure;
                }
                return true;
            })));

            assertThatThrownBy(() -> app.run(() -> field.setText("boom"))).isSameAs(failure);
            assertThat(app.isRunning()).isTrue();
        }
    }

    @Test
    void anErrorThrownByAConverterPropagates() {
        Property<Integer> count = Property.of(1);
        InternalError failure = new InternalError("A broken invariant");
        Converter<Integer> breaking = Converter.of(
                text -> {
                    if (text.equals("boom")) {
                        throw failure;
                    }
                    return Integer.valueOf(text);
                },
                value -> {
                    if (value == 13) {
                        throw failure;
                    }
                    return String.valueOf(value);
                },
                WHOLE);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            app.run(() -> form.bind(field, count, breaking));

            assertThatThrownBy(() -> app.run(() -> field.setText("boom"))).isSameAs(failure);
            JTextField other = app.call(JTextField::new);
            assertThatThrownBy(() -> app.run(() -> form.bind(other, Property.of(13), breaking)))
                    .isSameAs(failure);
            assertThat(app.isRunning()).isTrue();
        }
    }

    @Test
    void aConverterThatThrowsGoesToTheFailureHandlerAndLeavesThePropertyUnchanged() {
        Property<Integer> count = Property.of(1);
        IllegalStateException bug = new IllegalStateException("A bug in the converter");
        Converter<Integer> breaking = Converter.of(
                text -> {
                    if (text.equals("boom")) {
                        throw bug;
                    }
                    return Integer.valueOf(text);
                },
                String::valueOf,
                WHOLE);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<Integer> binding = app.call(
                    () -> form.bind(field, count, breaking).check(Rule.of(SMALL, (Integer value) -> value <= 10)));

            assertThatThrownBy(() -> app.run(() -> field.setText("boom")))
                    .isInstanceOf(AssertionError.class)
                    .hasCause(bug);

            assertThat(count.get()).isEqualTo(1);
            assertThat(statuses(app.call(() -> binding.validation().get())))
                    .containsExactly(Status.UNKNOWN, Status.UNKNOWN);
            assertThat(app.call(() -> form.dirty().get())).isFalse();

            app.run(() -> field.setText("2"));

            assertThat(count.get()).isEqualTo(2);
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.MET, Status.MET);
        }
    }

    @Test
    void aConverterWhoseFormatThrowsGoesToTheFailureHandlerAndLeavesTheText() {
        Property<Integer> count = Property.of(1);
        IllegalStateException bug = new IllegalStateException("A bug in the converter");
        Converter<Integer> breaking = Converter.of(
                Integer::valueOf,
                value -> {
                    if (value == 13) {
                        throw bug;
                    }
                    return String.valueOf(value);
                },
                WHOLE);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<Integer> binding = app.call(() -> form.bind(field, count, breaking));

            count.set(13);

            assertThatThrownBy(() -> app.run(() -> {}))
                    .isInstanceOf(AssertionError.class)
                    .hasCause(bug);
            assertThat(text(app, field)).isEqualTo("1");
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.UNKNOWN);

            JTextField other = app.call(JTextField::new);
            assertThatThrownBy(() -> app.run(() -> form.bind(other, Property.of(13), breaking)))
                    .isInstanceOf(AssertionError.class)
                    .hasCause(bug);
            assertThat(text(app, other)).isEmpty();
        }
    }

    @Test
    void aChangeOfThePropertyBackToTheTextAfterAFormatThatThrewMeetsTheConvertersCheckAgain() {
        Property<Integer> count = Property.of(14);
        IllegalStateException bug = new IllegalStateException("A bug in the converter");
        Converter<Integer> breaking = Converter.of(
                Integer::valueOf,
                value -> {
                    if (value == 13) {
                        throw bug;
                    }
                    return String.valueOf(value);
                },
                WHOLE);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<Integer> binding = app.call(() -> form.bind(field, count, breaking));
            count.set(13);
            assertThatThrownBy(() -> app.run(() -> {})).hasCause(bug);
            assertThat(text(app, field)).isEqualTo("14");

            count.set(14);

            assertThat(text(app, field)).isEqualTo("14");
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.MET);
            assertThat(app.call(() -> form.valid().get())).isTrue();

            count.set(13);
            assertThatThrownBy(() -> app.run(() -> {})).hasCause(bug);

            // The user's text replaces the one the format left, and stays as typed.
            app.run(() -> {
                field.setCaretPosition(0);
                field.replaceSelection("0");
            });

            assertThat(count.get()).isEqualTo(14);
            assertThat(text(app, field)).isEqualTo("014");
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.MET);
        }
    }

    @Test
    void aRuleIsTestedOnceAtEachChangeOfATextBinding() {
        Property<String> name = Property.of("Ada");
        AtomicBoolean armed = new AtomicBoolean();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            app.call(() -> form.bind(field, name).check(breaking(armed)));
            armed.set(true);

            failsOnce(app, () -> {
                field.setCaretPosition(3);
                field.replaceSelection("!");
            });
            assertThat(name.get()).isEqualTo("Ada!");

            failsOnce(app, () -> name.set("Lin"));

            name.set("Grace");
            failsOnce(app, () -> {});
            assertThat(text(app, field)).isEqualTo("Grace");
        }
    }

    @Test
    void aRuleIsTestedOnceWhenAChangeOfThePropertyReplacesARejectedText() {
        Property<Integer> count = Property.of(5);
        AtomicBoolean armed = new AtomicBoolean();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            app.call(() -> form.bind(field, count, NUMBER).check(breaking(armed)));
            armed.set(true);

            app.run(() -> field.setText("five"));
            failsOnce(app, () -> count.set(6));
            assertThat(text(app, field)).isEqualTo("6");

            app.run(() -> field.setText("seven"));
            count.set(7);
            failsOnce(app, () -> {});
            assertThat(text(app, field)).isEqualTo("7");
        }
    }

    @Test
    void aRuleIsTestedOnceAtEachChangeOfAButtonBinding() {
        Property<Boolean> accepted = Property.of(false);
        AtomicBoolean armed = new AtomicBoolean();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JCheckBox box = app.call(JCheckBox::new);
            app.call(() -> form.bind(box, accepted).check(breaking(armed)));
            armed.set(true);

            failsOnce(app, box::doClick);
            assertThat(accepted.get()).isTrue();

            failsOnce(app, () -> accepted.set(false));

            accepted.set(true);
            failsOnce(app, () -> {});
            assertThat(app.call(box::isSelected)).isTrue();
        }
    }

    @Test
    void aChangeOfATrackedListOnTheEdtUpdatesTheFormOnceTheListsListenersAreDone() {
        ObservableList<String> tags = ObservableList.of("work");
        ObservableList<Boolean> states = ObservableList.of();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            Scope scope = app.commands(Scope.class);
            JTextField field = app.call(JTextField::new);
            app.run(() -> {
                form.track(tags);
                form.bind(field, Property.of(""))
                        .check(Rule.of("At most 2 tags", s -> tags.size() <= 2))
                        .revalidateOn(tags);
                // A dependent of the form's state may modify an observable list: it doesn't run in the list's
                // listeners.
                form.valid().subscribe(scope, states::add);
            });

            app.run(() -> tags.addAll(List.of("home", "travel")));

            assertThat(app.call(() -> form.valid().get())).isFalse();
            assertThat(app.call(() -> form.dirty().get())).isTrue();
            assertThat(states.get()).containsExactly(false);
        }
    }

    @Test
    void aRuleThatThrowsAtAChangeOfAListOnTheEdtGoesToTheFailureHandlerOutsideTheListsListeners() {
        ObservableList<String> tags = ObservableList.of("work");
        ObservableList<Integer> tested = ObservableList.of();
        IllegalStateException bug = new IllegalStateException("A bug in the rule");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<String> binding = app.call(() -> form.bind(field, Property.of(""))
                    .check(Rule.of("Few tags", s -> {
                        // Code that the form runs, such as this rule, may modify an observable list.
                        tested.add(tags.size());
                        if (tags.size() > 1) {
                            throw bug;
                        }
                        return true;
                    }))
                    .revalidateOn(tags));
            app.run(() -> form.track(tags));

            assertThatThrownBy(() -> app.run(() -> tags.add("home")))
                    .isInstanceOf(AssertionError.class)
                    .hasCause(bug)
                    .hasNoSuppressedExceptions();

            assertThat(tested.get()).containsExactly(1, 2);
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.UNKNOWN);
            assertThat(app.call(() -> form.valid().get())).isFalse();
            assertThat(app.call(() -> form.dirty().get())).isTrue();
        }
    }

    @Test
    void bindingAComponentASecondTimeThrows() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = field(app, Property.of("Ada"));
            JCheckBox box = app.call(JCheckBox::new);
            app.call(() -> form.bind(box, Property.of(true)));

            assertThatThrownBy(() -> app.call(() -> form.bind(field, Property.of("Grace"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already bound");
            assertThatThrownBy(() -> app.call(() -> form.bind(field, Property.of(1), NUMBER)))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> app.call(() -> form.bind(box, Property.of(false))))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void bindingAComponentInTheFormsOfTwoViewsThrows() {
        List<Throwable> thrown = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JTextField field = field(app, Property.of("Ada"));
            JComboBox<String> combo = app.call(JComboBox::new);
            app.call(
                    () -> form(app).bind(combo, ObservableList.of("Ada"), text -> text, Property.of(Optional.empty())));
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                Form dialogForm = dialog.commands(Form.class);
                try {
                    dialogForm.bind(field, Property.of("Grace"));
                } catch (IllegalStateException expected) {
                    thrown.add(expected);
                }
                try {
                    dialogForm.bind(combo, ObservableList.of("Grace"), text -> text, Property.of(Optional.empty()));
                } catch (IllegalStateException expected) {
                    thrown.add(expected);
                }
            });

            app.commands(StandardCommands.class).about().execute();
        }

        assertThat(thrown).hasSize(2).allSatisfy(failure -> assertThat(failure).hasMessageContaining("already bound"));
    }

    @Test
    void theBindingsOfADialogEndWhenItCloses() {
        Property<String> name = Property.of("Ada");
        List<JTextField> fields = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                JTextField field = new JTextField();
                dialog.commands(Form.class).bind(field, name);
                fields.add(field);
            });
            app.commands(StandardCommands.class).about().execute();

            name.set("Lin");

            assertThat(app.call(() -> fields.getFirst().getText())).isEqualTo("Ada");
        }
    }

    @Test
    void aChangeOfATrackedListOnTheEdtUpdatesTheFormBeforeTheModificationReturns() {
        ObservableList<String> tags = ObservableList.of("work");
        ObservableList<Boolean> states = ObservableList.of();
        List<Boolean> executed = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            Scope scope = app.commands(Scope.class);
            CommandRegistry registry = app.commands(CommandRegistry.class);
            BoundCommand save = app.call(() -> {
                form.track(tags);
                // A dependent of the form's state may modify an observable list: it doesn't run under the list's lock.
                form.dirty().subscribe(scope, states::add);
                return registry.command(FormTest.class, "save")
                        .handler(context -> {})
                        .enabled(form::dirty)
                        .bind();
            });

            app.run(() -> {
                tags.add("home");
                executed.add(form.dirty().get());
                executed.add(save.execute());
            });

            assertThat(executed).containsExactly(true, true);
            assertThat(states.get()).containsExactly(true);
        }
    }

    @Test
    void aChangeOfThePropertyThatReplacesARejectedTextValidatesOnceOnAndOffTheEdt() {
        Property<Integer> count = Property.of(5);
        List<List<Status>> validations = new CopyOnWriteArrayList<>();
        List<Boolean> dirty = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            Scope scope = app.commands(Scope.class);
            JTextField field = app.call(JTextField::new);
            app.run(() -> form.bind(field, count, NUMBER)
                    .check(Rule.of(SMALL, (Integer value) -> value <= 10))
                    .validation()
                    .subscribe(scope, validation -> validations.add(statuses(validation))));
            app.run(() -> field.setText("five"));

            app.run(() -> {
                count.set(6);
                dirty.add(form.dirty().get());
            });

            assertThat(text(app, field)).isEqualTo("6");
            assertThat(dirty).containsExactly(true);

            app.run(() -> field.setText("seven"));
            count.set(12);
            app.run(() -> {});

            assertThat(text(app, field)).isEqualTo("12");
            assertThat(validations)
                    .containsExactly(
                            List.of(Status.UNMET, Status.UNKNOWN),
                            List.of(Status.MET, Status.MET),
                            List.of(Status.UNMET, Status.UNKNOWN),
                            List.of(Status.MET, Status.UNMET));
        }
    }

    @Test
    void leavesATextThatParsesToTheValueAsTheUserTypedItAfterAFormatThatThrew() {
        Property<Integer> count = Property.of(1);
        IllegalStateException bug = new IllegalStateException("A bug in the converter");
        Converter<Integer> breaking = Converter.of(
                Integer::valueOf,
                value -> {
                    if (value == 13) {
                        throw bug;
                    }
                    return String.valueOf(value);
                },
                WHOLE);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            Binding<Integer> binding = app.call(() -> form.bind(field, count, breaking));
            app.run(() -> field.setText("014"));
            count.set(13);
            assertThatThrownBy(() -> app.run(() -> {})).hasCause(bug);
            app.run(() -> field.setCaretPosition(1));

            count.set(14);

            assertThat(text(app, field)).isEqualTo("014");
            assertThat(app.call(field::getCaretPosition)).isEqualTo(1);
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.MET);
            assertThat(app.call(() -> form.valid().get())).isTrue();

            count.set(15);

            assertThat(text(app, field)).isEqualTo("15");
        }
    }

    @Test
    void aChangeBackToTheInitialValueAfterAFormatThatThrewWhenBoundMeetsTheConvertersCheckAgain() {
        Property<Integer> count = Property.of(13);
        AtomicBoolean armed = new AtomicBoolean(true);
        IllegalStateException bug = new IllegalStateException("A bug in the converter");
        Converter<Integer> breaking = Converter.of(
                Integer::valueOf,
                value -> {
                    if (armed.get()) {
                        throw bug;
                    }
                    return String.valueOf(value);
                },
                WHOLE);
        List<Binding<Integer>> bindings = new ArrayList<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Form form = form(app);
            JTextField field = app.call(JTextField::new);
            assertThatThrownBy(() -> app.run(() -> bindings.add(form.bind(field, count, breaking))))
                    .hasCause(bug);
            Binding<Integer> binding = bindings.getFirst();
            assertThat(text(app, field)).isEmpty();
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.UNKNOWN);
            armed.set(false);

            // Set on the EDT, so that both changes come before the update they cause, which finds the initial value.
            app.run(() -> {
                count.set(14);
                count.set(13);
            });

            assertThat(text(app, field)).isEqualTo("13");
            assertThat(statuses(app.call(() -> binding.validation().get()))).containsExactly(Status.MET);
            assertThat(app.call(() -> form.valid().get())).isTrue();
        }
    }
}
