package be.imgn.swing.examples.password;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** An application whose {@code changePassword} command changes the {@link Account}'s password, and says so. */
@Application(name = "Password", vendor = "imgn", qualifier = "be")
public final class PasswordApp implements SingleFrame {

    private final JLabel status = new JLabel();
    private final PasswordAppCommands commands;
    private final Ui ui;
    private final Account account;
    private final PasswordMessages messages;

    @Inject
    PasswordApp(PasswordAppCommands commands, Ui ui, Account account, PasswordMessages messages) {
        this.commands = commands;
        this.ui = ui;
        this.account = account;
        this.messages = messages;
    }

    /**
     * Launches the application.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(PasswordApp.class, args);
    }

    @Override
    public JComponent content() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(commands.changePassword().createButton(), BorderLayout.CENTER);
        panel.add(status, BorderLayout.PAGE_END);
        return panel;
    }

    @Command
    void changePassword() {
        ui.showDialog(ChangePasswordDialog.class).ifPresent(password -> {
            account.password().set(password);
            status.setText(messages.changed());
        });
    }
}
