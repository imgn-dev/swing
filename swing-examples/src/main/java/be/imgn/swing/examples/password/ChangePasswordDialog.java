package be.imgn.swing.examples.password;

import be.imgn.inject.Inject;
import be.imgn.swing.Check;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.ui.Binding;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.Ui;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import org.jspecify.annotations.Nullable;

/**
 * The dialog that changes the account's password, and returns the new one: the current password, the new one and its
 * confirmation. Below the new password, a checklist shows each of its requirements turning from unmet to met as the
 * user types.
 */
public final class ChangePasswordDialog implements DialogView<String> {

    private static final int COLUMNS = 20;

    @Exposed
    final JLabel currentLabel = new JLabel();

    @Exposed
    final JLabel newPasswordLabel = new JLabel();

    @Exposed
    final JLabel confirmationLabel = new JLabel();

    private final JPasswordField currentField = new JPasswordField(COLUMNS);
    private final JPasswordField newPasswordField = new JPasswordField(COLUMNS);
    private final JPasswordField confirmationField = new JPasswordField(COLUMNS);
    private final Property<String> current = Property.of("");
    private final Property<String> newPassword = Property.of("");
    private final Property<String> confirmation = Property.of("");
    private final Form form;
    private final Ui ui;
    private final PasswordMessages messages;
    private final Account account;

    @Inject
    ChangePasswordDialog(Form form, Ui ui, PasswordMessages messages, Account account) {
        this.form = form;
        this.ui = ui;
        this.messages = messages;
        this.account = account;
    }

    @Override
    public JComponent content() {
        form.bind(currentField, current)
                .check(Rule.required(messages.currentRequired()))
                .check(Rule.of(
                        messages.currentMatches(),
                        (String s) -> s.equals(account.password().get())));
        Binding<String> rules = form.bind(newPasswordField, newPassword).check(PasswordRules.newPassword(messages));
        form.bind(confirmationField, confirmation)
                .check(Rule.of(messages.confirmationMatches(), (String s) -> s.equals(newPassword.get())))
                .revalidateOn(newPassword);
        return FormPanel.create()
                .row(currentLabel, currentField)
                .row(newPasswordLabel, newPasswordField)
                .wide(checklist(rules))
                .row(confirmationLabel, confirmationField)
                .build();
    }

    @Override
    public Optional<String> result() {
        return Optional.of(newPassword.get());
    }

    // A label per check of the new password's validation. Before the user has typed anything, and once the field is
    // empty again, the requirements are not yet checked, rather than failed, unless OK has validated the field since.
    private JPanel checklist(Binding<String> rules) {
        Observable<Boolean> emptyUnvalidated =
                Observable.combine(rules.validated(), newPassword, (validated, text) -> !validated && text.isEmpty());
        Observable<Boolean> unchecked =
                Observable.combine(rules.shown(), emptyUnvalidated, (shown, empty) -> !shown || empty);
        int count = rules.validation().get().checks().size();
        JPanel panel = new JPanel(new GridLayout(count, 1));
        for (int i = 0; i < count; i++) {
            int index = i;
            JLabel label = new JLabel();
            Color foreground = label.getForeground();
            ui.bind(
                    line -> {
                        Color color = line.mark().color;
                        label.setText(line.mark().symbol + " " + line.requirement());
                        label.setForeground(color == null ? foreground : color);
                    },
                    Observable.combine(
                            unchecked,
                            rules.validation(),
                            (pending, validation) ->
                                    Line.of(pending, validation.checks().get(index))));
            panel.add(label);
        }
        return panel;
    }

    /** How a requirement shows in the checklist. The marks keep the status readable without the colors. */
    private enum Mark {
        UNCHECKED("•", null),
        MET("✓", new Color(0x2e7d32)),
        UNMET("✗", new Color(0xc62828));

        private final String symbol;
        // Null for the label's default foreground.
        private final @Nullable Color color;

        Mark(String symbol, @Nullable Color color) {
            this.symbol = symbol;
            this.color = color;
        }
    }

    /** A line of the checklist. */
    private record Line(Mark mark, String requirement) {

        static Line of(boolean unchecked, Check check) {
            Mark mark = unchecked
                    ? Mark.UNCHECKED
                    : switch (check.status()) {
                        case MET -> Mark.MET;
                        case UNMET -> Mark.UNMET;
                        // A rule that threw could not be tested: its requirement is not checked.
                        case UNKNOWN -> Mark.UNCHECKED;
                    };
            return new Line(mark, check.requirement());
        }
    }
}
