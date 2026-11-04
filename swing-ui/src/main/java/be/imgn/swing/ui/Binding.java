package be.imgn.swing.ui;

import be.imgn.swing.Check;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.Validation;
import be.imgn.swing.Validator;
import be.imgn.swing.internal.Guarded;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import org.jspecify.annotations.Nullable;

/**
 * The binding of a component to a property in a {@link Form}, with the rules its value must meet.
 *
 * <p>Its validation has a check per requirement: first the converter's, if it has one, then one per rule, in the order
 * they were added. Every rule is tested at each validation, whether or not an earlier one failed. When the converter
 * rejects the field's text, the rules' checks are {@link Check.Status#UNKNOWN}. A rule that throws is a bug in the
 * rule: the exception goes to the {@code FailureHandler}, and the rule's check is {@link Check.Status#UNKNOWN}; an
 * {@link Error} is not caught. The binding is validated when it is bound, at each change of its component and at each
 * change of its property.
 *
 * <p>Its error, the requirement of the first check that isn't met, is shown on its component by the
 * {@link ValidationFeedback} while {@link #shown()} is {@code true}, and hidden as soon as the binding has no error.
 *
 * <p>Every method is called on the EDT, and every subscription it makes belongs to the view's scope.
 *
 * @param <T> the type of the value
 */
public final class Binding<T> {

    private final Form form;
    private final JComponent component;
    private final Property<T> property;
    private final @Nullable String requirement;
    private final List<Rule<? super T>> rules = new ArrayList<>();
    private final Property<Validation> validation;
    private final Property<Boolean> shown = Property.of(false);
    private final Property<Boolean> validated = Property.of(false);
    private Check.Status conversion = Check.Status.MET;
    // Whether a user change of the component is setting the property, which then validates the binding once it is set.
    private boolean userChanging;
    private T baseline;
    // The error the feedback shows, and the component it shows it on, while it shows one.
    private @Nullable String displayed;
    private @Nullable JComponent target;

    Binding(Form form, JComponent component, Property<T> property, @Nullable String requirement) {
        this.form = form;
        this.component = component;
        this.property = property;
        this.requirement = requirement;
        this.baseline = property.get();
        this.validation = Property.of(compute());
    }

    /**
     * Adds a rule to this binding, whose check follows those of the rules added before.
     *
     * @param rule the rule
     * @return this binding
     * @throws NullPointerException if {@code rule} is {@code null}
     */
    public Binding<T> check(Rule<? super T> rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
        changed();
        return this;
    }

    /**
     * Adds the rules of a validator to this binding, in their order, after the rules added before.
     *
     * @param validator the validator
     * @return this binding
     * @throws NullPointerException if {@code validator} is {@code null}
     */
    public Binding<T> check(Validator<? super T> validator) {
        rules.addAll(Objects.requireNonNull(validator, "validator").rules());
        changed();
        return this;
    }

    /**
     * Validates this binding again at each change of another observable, on the EDT, such as the property of another
     * field that a rule compares the value with: before the change returns for a change made on the EDT, such as the
     * user's change of that field, once the list has released its lock for one delivered by an observable list, and in
     * a posted update for one made on another thread. A change of {@code other} is not a user change of this binding's
     * component: it doesn't change {@link #shown()} or {@link #validated()}.
     *
     * @param other the observable
     * @return this binding
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public Binding<T> revalidateOn(Observable<?> other) {
        form.subscribe(Objects.requireNonNull(other, "other"), this::changed);
        return this;
    }

    /**
     * Returns the last validation of this binding.
     *
     * @return the validation
     */
    public Observable<Validation> validation() {
        return validation;
    }

    /**
     * Returns whether this binding's error is shown: {@code false} until the user changes the component or
     * {@link Form#validate()} is called, then {@code true} for the binding's life.
     *
     * @return whether the error is shown
     */
    public Observable<Boolean> shown() {
        return shown;
    }

    /**
     * Returns whether {@link Form#validate()} was called since the user last changed the component: {@code false} until
     * it is called, then {@code true} until the user changes the component. A change of the property, or of an
     * observable the binding revalidates on, doesn't change it. A view that lists the requirements uses it to show them
     * as failed after {@code validate()}, such as on OK, even for a value it otherwise shows as not yet checked.
     *
     * @return whether the binding was validated since the user last changed the component
     */
    public Observable<Boolean> validated() {
        return validated;
    }

    JComponent component() {
        return component;
    }

    /**
     * Records whether the converter accepted the field's text, which the next validation reads.
     *
     * @param status the converter's check
     * @return whether the check changed
     */
    boolean converted(Check.Status status) {
        boolean changed = conversion != status;
        conversion = status;
        return changed;
    }

    /**
     * Handles a change of the component that may be the user's: runs what sets the property from it, then, for the
     * user's, shows the error from now on and validates the binding, once, the property's change leaving it to this.
     *
     * @param change sets the property from the component, and returns whether the change was the user's
     */
    void userChanged(BooleanSupplier change) {
        boolean user;
        userChanging = true;
        try {
            user = change.getAsBoolean();
        } finally {
            userChanging = false;
        }
        if (user) {
            shown.set(true);
            validated.set(false);
            changed();
        }
    }

    /**
     * Validates the binding at a change of its property, unless the user's change of the component made it, or the
     * converter doesn't accept the field's text: the validation then doesn't depend on the property, and the update of
     * the field that replaces the text validates the binding, once. The form's state is updated either way.
     */
    void propertyChanged() {
        if (userChanging) {
            return;
        }
        if (conversion == Check.Status.MET) {
            changed();
        } else {
            form.refresh();
        }
    }

    /** Shows the error, if any, from now on, for {@link Form#validate()}. */
    void show() {
        shown.set(true);
        validated.set(true);
        showFeedback();
    }

    /** Validates the binding again, and shows the feedback of the new validation. */
    void changed() {
        revalidate();
        showFeedback();
    }

    void revalidate() {
        validation.set(compute());
        form.refresh();
    }

    boolean isDirty() {
        return !Objects.equals(property.get(), baseline);
    }

    void markClean() {
        baseline = property.get();
    }

    private Validation compute() {
        List<Check> checks = new ArrayList<>(rules.size() + 1);
        if (requirement != null) {
            checks.add(new Check(requirement, conversion));
        }
        // When the converter rejects the text, the property doesn't hold it, so the rules can't be tested.
        boolean testable = conversion == Check.Status.MET;
        T value = property.get();
        for (Rule<? super T> rule : rules) {
            checks.add(new Check(rule.requirement(), testable ? test(rule, value) : Check.Status.UNKNOWN));
        }
        return new Validation(checks);
    }

    // A rule that throws has a bug: the exception goes to the FailureHandler, and its check can't be known.
    private Check.Status test(Rule<? super T> rule, T value) {
        AtomicBoolean met = new AtomicBoolean();
        Throwable thrown = Guarded.runCatchingExceptions(() -> met.set(rule.test(value)));
        if (thrown != null) {
            form.failed(thrown);
            return Check.Status.UNKNOWN;
        }
        return met.get() ? Check.Status.MET : Check.Status.UNMET;
    }

    private void showFeedback() {
        String error = shown.get() ? validation.get().error().orElse(null) : null;
        if (Objects.equals(error, displayed)) {
            return;
        }
        JComponent on = target;
        if (error == null) {
            if (on != null) {
                form.feedback().hide(on);
            }
            target = null;
        } else {
            if (on == null) {
                on = feedbackTarget();
                target = on;
            }
            form.feedback().show(on, error);
        }
        displayed = error;
    }

    // A component in the viewport of a scroll pane, such as a text area, has its border drawn by the scroll pane.
    private JComponent feedbackTarget() {
        // A viewport holds one component, its view; a scroll pane's headers are viewports too.
        if (component.getParent() instanceof JViewport viewport
                && viewport.getParent() instanceof JScrollPane pane
                && Objects.equals(pane.getViewport(), viewport)) {
            return pane;
        }
        return component;
    }
}
