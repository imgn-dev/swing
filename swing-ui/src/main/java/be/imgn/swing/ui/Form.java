package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.swing.Check;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.Observable;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.internal.DefaultObservableList;
import be.imgn.swing.internal.Guarded;
import be.imgn.swing.ui.internal.EdtDelivery;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.jspecify.annotations.Nullable;

/**
 * Binds the input components of a view to properties, validates their values, and tracks whether they changed. Each
 * view has one, which it injects.
 *
 * <pre>{@code
 * form.bind(nameField, name).check(Rule.required(messages.nameRequired()));
 * form.bind(ageField, age, Converter.of(Integer::valueOf, String::valueOf, messages.wholeNumber()));
 * }</pre>
 *
 * <p>The bindings are two-way. A user change of a component sets its property, and updates the binding's
 * {@link Binding#validation()}, {@link #valid()} and {@link #dirty()}, synchronously on the EDT, before the change's
 * event returns, so that a command whose {@code enabled} reads them, executed right after, sees the new state. A change
 * of a property, which may be set from any thread, updates its component on the EDT, in an update posted as for
 * {@link Ui#bind(java.util.function.Consumer, Observable)}; the validations that depend on it, and the form's state,
 * are updated before the change returns when it changes on the EDT, such as from a user change of another component or
 * the edit of a table's cell that modifies a tracked list, once the list has released its lock, and in a posted update
 * otherwise. Each change validates the bindings that depend on it once. Changes that a binding makes to its own
 * component, from the property, are not user changes: they don't show errors, and don't set the property.
 *
 * <p>A rule or a converter that throws, other than a converter rejecting a text, has a bug: the exception goes to the
 * {@code FailureHandler}, and its check is {@link Check.Status#UNKNOWN}, as are the rules' after a converter's. An
 * {@link Error} is not caught.
 *
 * <p>Every method is called on the EDT, and every subscription it makes belongs to the view's scope.
 */
@ViewScoped
public final class Form {

    private static final Converter<String> TEXT = Converter.of(Function.identity(), Function.identity(), "");

    private final Window window;
    private final Scope scope;
    private final ValidationFeedback feedback;
    private final FailureHandler failures;
    private final UndoStack undo;
    private final List<Binding<?>> bindings = new ArrayList<>();
    private final List<Tracked<?>> tracked = new ArrayList<>();
    private final Property<Boolean> valid = Property.of(true);
    private final Property<Boolean> dirty = Property.of(false);

    @Inject
    Form(Window window, Scope scope, ValidationFeedback feedback, FailureHandler failures, UndoStack undo) {
        this.window = window;
        this.scope = scope;
        this.feedback = feedback;
        this.failures = failures;
        this.undo = undo;
    }

    /**
     * Binds a text component to a property of its text. The field's text is set from the property now; each change of
     * the document the field has now sets the property to the text; a change of the property sets the text, unless it
     * already equals the value, so that typing is never interrupted and the caret stays in place.
     *
     * @param field the text component
     * @param property the property
     * @return the binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if the component is already bound
     */
    public Binding<String> bind(JTextComponent field, Property<String> property) {
        return bindText(field, property, TEXT, null);
    }

    /**
     * Binds a text component to a property through a converter. The field's text is set from the property, formatted,
     * now; each change of the document the field has now sets the property to the text parsed, unless the converter
     * rejects it, which leaves the property unchanged; a change of the property sets the text, unless it already parses
     * to an equal value, so that typing is never interrupted and the caret stays in place.
     *
     * <p>The converter's requirement is the first check of the binding: when the converter rejects the field's text,
     * the check is unmet and the rules' are unknown, since the property doesn't hold the text.
     *
     * @param field the text component
     * @param property the property
     * @param converter the converter
     * @param <T> the type of the value
     * @return the binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if the component is already bound
     */
    public <T> Binding<T> bind(JTextComponent field, Property<T> property, Converter<T> converter) {
        Objects.requireNonNull(converter, "converter");
        return bindText(field, property, converter, converter.requirement());
    }

    /**
     * Binds a button, such as a check box, a toggle button or a radio button, to a property of its selected state. The
     * button's selected state is set from the property now; an item event sets the property; a change of the property
     * sets the selected state.
     *
     * @param button the button
     * @param property the property
     * @return the binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if the button is already bound
     */
    public Binding<Boolean> bind(AbstractButton button, Property<Boolean> property) {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(property, "property");
        claim(button);
        Binding<Boolean> binding = new Binding<>(this, button, property, null);
        ButtonLink link = new ButtonLink(button, property, binding);
        EdtDelivery.subscribe(scope, property, ignored -> link.update(), EdtDelivery.Order.FRAMEWORK);
        subscribe(property, binding::propertyChanged);
        link.select(property.get());
        button.addItemListener(link);
        scope.onClose(() -> button.removeItemListener(link));
        add(binding);
        return binding;
    }

    /**
     * Binds a combo box to an observable list and to a property of its selected item. The combo box shows
     * {@code items}, through a new model that follows the list change by change on the EDT, with a renderer showing
     * {@code text} of each element, and becomes not editable. Its selection and {@code selection} are kept in step, as
     * {@link Ui#bind(javax.swing.JList, ObservableList, Function, Property)} keeps a list's: the user's selection sets
     * the property, and a change of the property selects its value, or clears the selection for an empty value or one
     * that isn't in the list, then setting the property to empty; changes of the list keep the selection on the
     * selected element, by tracking its index. {@code Optional.empty()} means no selected item.
     *
     * <p>The property's baseline is its value once the binding has selected it, which is empty when the value isn't in
     * the list.
     *
     * @param combo the combo box
     * @param items the elements, made by {@link ObservableList#of(Object[])} or {@link ObservableList#copyOf}
     * @param text the text of an element
     * @param selection the selected element, empty when nothing is selected
     * @param <E> the type of the elements
     * @return the binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if the combo box is already bound
     */
    public <E> Binding<Optional<E>> bind(
            JComboBox<E> combo,
            ObservableList<E> items,
            Function<? super E, String> text,
            Property<Optional<E>> selection) {
        Objects.requireNonNull(combo, "combo");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(selection, "selection");
        ListBindings.checkNotBound(combo);
        ListFeed<E> feed = ListFeed.of(items, scope);
        claim(combo);
        FeedComboModel<E> model = new FeedComboModel<>(feed);
        feed.observe(model);
        combo.setEditable(false);
        combo.setModel(model);
        TextRenderer.Elements<E> rows = TextRenderer.of(feed);
        // At no index, the combo box renders its selected item, and each element to measure its display area.
        combo.setRenderer(
                new TextRenderer<>(text, (index, value) -> index < 0 ? model.element(value) : rows.at(index, value)));
        ComboSelectionView view = new ComboSelectionView(model);
        SelectionSync<E> sync = SelectionSync.bind(feed, view, selection, null, scope);
        // Created once the sync has selected the value, which it empties when the list doesn't hold it.
        Binding<Optional<E>> binding = new Binding<>(this, combo, selection, null);
        view.follow(sync, binding);
        subscribe(selection, binding::propertyChanged);
        add(binding);
        return binding;
    }

    /**
     * Tracks an observable that has no component in the form, such as an observable list edited in a table: the form is
     * dirty while its value differs from its value now, or when {@link #markClean()} was last called.
     *
     * @param value the observable
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public void track(Observable<?> value) {
        Objects.requireNonNull(value, "value");
        tracked.add(new Tracked<>(value));
        subscribe(value, this::refresh);
    }

    /**
     * Returns whether every binding's validation is valid, whether or not its errors are shown.
     *
     * @return whether the form is valid; {@code true} for a form with no binding
     */
    public Observable<Boolean> valid() {
        return valid;
    }

    /**
     * Returns whether the value of a binding's property, or of a tracked observable, differs from its baseline: its
     * value when it was bound or tracked, or when {@link #markClean()} was last called.
     *
     * @return whether the form is dirty; {@code false} for a form with nothing bound or tracked
     */
    public Observable<Boolean> dirty() {
        return dirty;
    }

    /**
     * Shows the error of every binding that has one, and moves the focus to the first component with an error, in
     * binding order: it first selects the tabs of the {@link JTabbedPane}s that hold it and aren't selected, from the
     * outermost pane in, then focuses it through its window's {@link Window#focus(Component)}, unless it still isn't
     * showing, such as one on another card of a wizard.
     *
     * @return whether the form is valid, {@link #valid()}'s value
     */
    public boolean validate() {
        Binding<?> first = null;
        for (Binding<?> binding : bindings) {
            binding.show();
            if (first == null && binding.validation().get().error().isPresent()) {
                first = binding;
            }
        }
        if (first != null) {
            focus(first.component());
        }
        return valid.get();
    }

    /** Makes the current value of every binding's property and of every tracked observable its new baseline. */
    public void markClean() {
        bindings.forEach(Binding::markClean);
        tracked.forEach(Tracked::markClean);
        refresh();
    }

    private <T> Binding<T> bindText(
            JTextComponent field, Property<T> property, Converter<T> converter, @Nullable String requirement) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(property, "property");
        claim(field);
        Binding<T> binding = new Binding<>(this, field, property, requirement);
        Document document = field.getDocument();
        TextLink<T> link = new TextLink<>(field, property, converter, binding, failures);
        // Subscribed before the text is set, so that a change made in between still reaches the field. While the
        // field shows a text the converter doesn't accept, the property's change leaves the validation to the field's
        // update, which replaces the text: the rules, unknown until then, are tested once, by the update.
        subscribe(property, binding::propertyChanged);
        EdtDelivery.subscribe(scope, property, ignored -> link.update(), EdtDelivery.Order.FRAMEWORK);
        link.setText(property.get());
        document.addDocumentListener(link);
        scope.onClose(() -> document.removeDocumentListener(link));
        // The user's edits go to the view's undo stack; undoing one is a change of the document, which sets the
        // property as the user's do. The binding's own updates of the text are not the user's to undo, and the edits
        // before them no longer apply to the text: they clear the stack.
        UndoableEditListener edits = event -> {
            if (link.own) {
                undo.clear();
            } else {
                undo.add(event.getEdit());
            }
        };
        document.addUndoableEditListener(edits);
        scope.onClose(() -> document.removeUndoableEditListener(edits));
        add(binding);
        return binding;
    }

    // Marks the component itself, as Ui's bindings do, so that the form of another view can't bind it either.
    private static void claim(JComponent component) {
        ListBindings.checkNotBound(component);
        ListBindings.markBound(component);
    }

    private void add(Binding<?> binding) {
        bindings.add(binding);
        binding.revalidate();
    }

    // Runs a revalidation or a refresh at each change of an observable: on the EDT, before the change returns, so
    // that a user change, such as the edit of a table's cell that modifies a tracked list, updates the state of every
    // binding that depends on it before its event returns; in a posted update for one made on another thread. A
    // change delivered by an observable list runs it once the list has released its lock: its listeners must be
    // quick and can't modify a list, which what depends on the form's state may. Only the updates of components,
    // which this is not, are always posted.
    void subscribe(Observable<?> source, Runnable action) {
        Runnable posted = EdtDelivery.updater(scope, action, EdtDelivery.Order.FRAMEWORK);
        source.subscribe(scope, ignored -> {
            if (!EventQueue.isDispatchThread()) {
                posted.run();
            } else {
                DefaultObservableList.afterDelivery(() -> {
                    if (!scope.isClosed()) {
                        action.run();
                    }
                });
            }
        });
    }

    ValidationFeedback feedback() {
        return feedback;
    }

    /** Hands a bug in a rule or a converter to the {@code FailureHandler}. */
    void failed(Throwable failure) {
        failures.failed(failure);
    }

    // Recomputes the form's state from its bindings' validations and values: called on the EDT at each change.
    void refresh() {
        valid.set(
                bindings.stream().allMatch(binding -> binding.validation().get().valid()));
        dirty.set(
                bindings.stream().anyMatch(Binding::isDirty) || tracked.stream().anyMatch(Tracked::isDirty));
    }

    private void focus(JComponent component) {
        List<Tab> tabs = new ArrayList<>();
        Component child = component;
        for (Component parent = child.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof JTabbedPane pane) {
                int index = pane.indexOfComponent(child);
                if (index >= 0) {
                    tabs.addFirst(new Tab(pane, index));
                }
            }
            child = parent;
        }
        for (Tab tab : tabs) {
            if (tab.pane().getSelectedIndex() != tab.index()) {
                tab.pane().setSelectedIndex(tab.index());
            }
        }
        if (isShowing(component)) {
            window.focus(component);
        }
    }

    // Showing in its window: visible, as are its ancestors up to the window's root pane. A tab that isn't selected and
    // a card that isn't shown are not visible. The tester's windows are never realized, so Component.isShowing, which
    // also needs the window on screen, is false for every component there.
    private boolean isShowing(Component component) {
        JRootPane root = window.rootPane();
        for (Component current = component; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
            if (current.equals(root)) {
                return true;
            }
        }
        return false;
    }

    private record Tab(JTabbedPane pane, int index) {}

    /** An observable the form tracks, with its baseline. */
    private static final class Tracked<V> {
        private final Observable<V> observable;
        private V baseline;

        Tracked(Observable<V> observable) {
            this.observable = observable;
            this.baseline = observable.get();
        }

        boolean isDirty() {
            return !Objects.equals(observable.get(), baseline);
        }

        void markClean() {
            baseline = observable.get();
        }
    }

    /** Keeps a text component's document and a property in step. */
    private static final class TextLink<T> implements DocumentListener {
        private final JTextComponent field;
        private final Property<T> property;
        private final Converter<T> converter;
        private final Binding<T> binding;
        private final FailureHandler failures;
        // The value the field's text and the property last agreed on. A later update of the property to that value
        // came from the field itself, and leaves the text as the user is typing it, even when it no longer parses.
        private T synced;
        // Whether the converter's format threw when it last set the text, leaving one that may not show the synced
        // value: the next update sets the text, whatever the value, unless the text parses to it, until the user
        // changes the text.
        private boolean formatFailed;
        private boolean own;

        TextLink(
                JTextComponent field,
                Property<T> property,
                Converter<T> converter,
                Binding<T> binding,
                FailureHandler failures) {
            this.field = field;
            this.property = property;
            this.converter = converter;
            this.binding = binding;
            this.failures = failures;
            this.synced = property.get();
        }

        @Override
        public void insertUpdate(DocumentEvent event) {
            changed(event.getDocument());
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            changed(event.getDocument());
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            changed(event.getDocument());
        }

        // A user change of the document the field had when bound: the binding's own setText, a removal then an
        // insertion, sets nothing, so that the property never passes through an empty text.
        private void changed(Document document) {
            if (own) {
                return;
            }
            String text;
            try {
                text = document.getText(0, document.getLength());
            } catch (BadLocationException impossible) {
                throw new IllegalStateException(impossible);
            }
            formatFailed = false;
            binding.userChanged(() -> {
                T value = parse(text);
                if (value != null) {
                    synced = value;
                    property.set(value);
                }
                return true;
            });
        }

        // Records the converter's check for the next validation, and returns the value, or null when there is none.
        // Any other exception than a rejection is a bug in the converter, which goes to the FailureHandler.
        private @Nullable T parse(String text) {
            AtomicReference<@Nullable T> parsed = new AtomicReference<>();
            Throwable thrown = Guarded.runCatchingExceptions(() ->
                    parsed.set(Objects.requireNonNull(converter.parse(text), "The converter's parse returned null")));
            if (thrown == null) {
                binding.converted(Check.Status.MET);
                return parsed.get();
            }
            if (thrown instanceof IllegalArgumentException || thrown instanceof DateTimeException) {
                binding.converted(Check.Status.UNMET);
            } else {
                failures.failed(thrown);
                binding.converted(Check.Status.UNKNOWN);
            }
            return null;
        }

        // The update of the property, posted on the EDT. The text parsed to the synced value, unless the converter has
        // rejected it since: it already equals, or parses to, the property's value exactly when that is the synced
        // value, and a rejected text the user is typing stays while the property keeps the value it was typed from.
        // After a format that threw, the text is parsed instead, and stays if it parses to the value. The property's
        // change validates the binding while the converter accepts the text; the update validates it only when it
        // changes the converter's check, such as when it replaces a rejected text.
        void update() {
            T value = property.get();
            if (formatFailed && parsesTo(value)) {
                synced = value;
                formatFailed = false;
                if (binding.converted(Check.Status.MET)) {
                    binding.changed();
                }
                return;
            }
            if ((formatFailed || !Objects.equals(value, synced)) && setText(value)) {
                binding.changed();
            }
        }

        // Whether the field's text parses to a value. A rejection means it doesn't; any other exception is a bug in
        // the converter, which goes to the FailureHandler.
        private boolean parsesTo(T value) {
            String text = field.getText();
            AtomicReference<@Nullable T> parsed = new AtomicReference<>();
            Throwable thrown = Guarded.runCatchingExceptions(() ->
                    parsed.set(Objects.requireNonNull(converter.parse(text), "The converter's parse returned null")));
            if (thrown == null) {
                return Objects.equals(value, parsed.get());
            }
            if (!(thrown instanceof IllegalArgumentException || thrown instanceof DateTimeException)) {
                failures.failed(thrown);
            }
            return false;
        }

        // A converter whose format throws has a bug: the exception goes to the FailureHandler, and the text, which
        // then doesn't show the value, stays as it is, with the converter's check unknown. Returns whether the
        // converter's check changed.
        boolean setText(T value) {
            AtomicReference<@Nullable String> formatted = new AtomicReference<>();
            Throwable thrown = Guarded.runCatchingExceptions(() -> formatted.set(
                    Objects.requireNonNull(converter.format(value), "The converter's format returned null")));
            if (thrown != null) {
                failures.failed(thrown);
                formatFailed = true;
                return binding.converted(Check.Status.UNKNOWN);
            }
            String text = Objects.requireNonNull(formatted.get());
            own = true;
            try {
                field.setText(text);
            } finally {
                own = false;
            }
            synced = value;
            formatFailed = false;
            return binding.converted(Check.Status.MET);
        }
    }

    /** Keeps a button's selected state and a property in step. */
    private static final class ButtonLink implements ItemListener {
        private final AbstractButton button;
        private final Property<Boolean> property;
        private final Binding<Boolean> binding;
        private boolean own;

        ButtonLink(AbstractButton button, Property<Boolean> property, Binding<Boolean> binding) {
            this.button = button;
            this.property = property;
            this.binding = binding;
        }

        @Override
        public void itemStateChanged(ItemEvent event) {
            if (own) {
                return;
            }
            binding.userChanged(() -> {
                property.set(event.getStateChange() == ItemEvent.SELECTED);
                return true;
            });
        }

        // The update of the property, posted on the EDT. The property's change validates the binding, and the selected
        // state, which the validation doesn't read, needs none.
        void update() {
            boolean value = property.get();
            if (button.isSelected() != value) {
                select(value);
            }
        }

        void select(boolean value) {
            own = true;
            try {
                button.setSelected(value);
            } finally {
                own = false;
            }
        }
    }
}
