package be.imgn.swing.wizard;

import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.CommandRegistry;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.ViewScoped;
import be.imgn.swing.ui.internal.AddedViews;
import be.imgn.swing.ui.internal.FocusOrder;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import be.imgn.swing.wizard.internal.WizardTexts;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Shows the content of a dialog view as a sequence of steps, with Previous and Next, each step validated before the
 * next: the view injects it, returns what {@link #steps} returns from its {@code content()}, or places it, and returns
 * what {@link #buttons} returns from its {@code buttons}. Finish is the dialog's {@code ok()}, on every step, which
 * validates the whole form and shows the step of the first error.
 *
 * <p>The steps are components of the view, held by its {@code @Exposed} component fields: they share the view's
 * {@link Form}, its result and its scope, so that going back keeps what was entered. Each step's {@code step-title}
 * resource titles it, in the heading "Step 1 of 3: Contact Information". Its methods are called on the EDT.
 */
@ViewScoped
public final class Wizard {

    private static final int GAP = 8;

    private final Form form;
    private final Ui ui;
    private final AddedViews views;
    private final WizardTexts texts;
    private final ViewHost view;
    private final Property<Integer> step = Property.of(0);
    private final Property<Integer> count = Property.of(0);
    private final List<JComponent> steps = new ArrayList<>();
    // The steps shown once, whose accessibility the checks covered.
    private final Set<JComponent> checked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final JLabel heading = new JLabel();
    private final JPanel cards = new JPanel(new CardLayout());
    private final BoundCommand previous;
    private final BoundCommand next;

    @Inject
    Wizard(
            CommandRegistry registry,
            Form form,
            Ui ui,
            AddedViews views,
            WizardTexts texts,
            Scope scope,
            WindowRegistry windows) {
        this.form = form;
        this.ui = ui;
        this.views = views;
        this.texts = texts;
        this.view = windows.viewOf(scope);
        this.previous = registry.overridableCommand(Wizard.class, "previous")
                .handler(context -> show(step.get() - 1))
                .enabled(() -> step.map(index -> index > 0))
                .bind();
        this.next = registry.overridableCommand(Wizard.class, "next")
                .handler(context -> advance())
                .enabled(() -> Observable.combine(step, count, (index, steps) -> index < steps - 1))
                .bind();
        form.revealWith(this::reveal);
    }

    /**
     * Returns a new panel that shows the steps: a heading, and the steps on the cards of a {@link CardLayout}, in the
     * given order, the first shown. It is called once, during the build of the dialog view that injects this wizard.
     *
     * @param steps the steps, each held by an {@code @Exposed} component field of the view, nested ones included
     * @return the panel, which the view returns from its {@code content()} or places in it
     * @throws IllegalStateException if called again, outside the build of a dialog view, before its content is built,
     *     such as in its constructor, or outside the build of the view whose scope holds this wizard
     * @throws IllegalArgumentException for no step, a step given twice, or a step that no {@code @Exposed} component
     *     field of the view holds
     * @throws NullPointerException if the array or a step is {@code null}
     */
    public JComponent steps(JComponent... steps) {
        Objects.requireNonNull(steps, "steps");
        for (JComponent given : steps) {
            Objects.requireNonNull(given, "step");
        }
        if (!view.isBuilding() || view.dialog().isEmpty() || view.view().isEmpty()) {
            throw new IllegalStateException(
                    "The steps of a wizard are given during the build of the dialog view that injects it");
        }
        if (!this.steps.isEmpty()) {
            throw new IllegalStateException("The steps of a wizard are given once");
        }
        if (steps.length == 0) {
            throw new IllegalArgumentException("A wizard has at least one step");
        }
        Set<JComponent> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (JComponent given : steps) {
            if (!seen.add(given)) {
                throw new IllegalArgumentException("The step " + given + " is given twice");
            }
            if (views.fieldOf(view, given).isEmpty()) {
                throw new IllegalArgumentException(
                        "No @Exposed component field of " + view.type().getName() + " holds the step " + given);
            }
        }
        for (int index = 0; index < steps.length; index++) {
            this.steps.add(steps[index]);
            cards.add(steps[index], Integer.toString(index));
        }
        count.set(steps.length);
        heading.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
        ui.render(this::renderHeading, step);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(heading, BorderLayout.PAGE_START);
        panel.add(cards, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Returns the index of the shown step, from 0. It changes on the EDT.
     *
     * @return the index
     */
    public Observable<Integer> step() {
        return step;
    }

    /**
     * Returns the command that shows the step before the shown one, enabled while the shown step isn't the first.
     *
     * @return the command
     */
    public BoundCommand previous() {
        return previous;
    }

    /**
     * Returns the command that validates the shown step and shows the next one when it is valid, enabled while the
     * shown step isn't the last. Like {@code ok()}, it stays enabled while the step is invalid, so that activating it
     * shows what is wrong.
     *
     * @return the command
     */
    public BoundCommand next() {
        return next;
    }

    /**
     * Returns the dialog's buttons: Previous, Next, then the dialog's {@code ok()}, which finishes the wizard, and
     * {@code cancel()}.
     *
     * @param commands the dialog's standard commands
     * @return the commands
     * @throws NullPointerException if {@code commands} is {@code null}
     */
    public List<BoundCommand> buttons(StandardCommands commands) {
        Objects.requireNonNull(commands, "commands");
        return List.of(previous, next, commands.ok(), commands.cancel());
    }

    private void advance() {
        int index = step.get();
        if (index < steps.size() - 1 && form.validate(steps.get(index))) {
            show(index + 1);
        }
    }

    // Shows a step: its card, the index and the heading, then focuses its first component that takes the focus.
    private void show(int index) {
        JComponent shown = steps.get(index);
        ((CardLayout) cards.getLayout()).show(cards, Integer.toString(index));
        step.set(index);
        // The build checked the first step; the others were hidden then.
        if (index > 0 && checked.add(shown)) {
            views.check(view, shown);
        }
        FocusOrder.first(shown).ifPresent(ui.window()::focus);
    }

    // Shows the step that holds a component that the form is about to focus.
    private void reveal(Component component) {
        for (int index = 0; index < steps.size(); index++) {
            if (SwingUtilities.isDescendingFrom(component, steps.get(index))) {
                if (index != step.get()) {
                    show(index);
                }
                return;
            }
        }
    }

    private void renderHeading() {
        int index = step.get();
        String title = views.fieldString(view, steps.get(index), "step-title").orElse("");
        heading.setText(texts.heading(index + 1, steps.size(), title));
    }
}
