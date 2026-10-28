package be.imgn.swing.ui.internal;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.jspecify.annotations.Nullable;

/** The error dialog: a message, a details area holding the failure, and a Copy button. */
public final class ErrorDialog {

    private static final int PADDING = 12;
    private static final int WIDTH = 560;
    private static final int HEIGHT = 240;

    private ErrorDialog() {}

    /**
     * Returns the text of a failure: its stack trace.
     *
     * @param failure the failure
     * @return the stack trace
     */
    public static String details(Throwable failure) {
        StringWriter writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /**
     * Shows the error dialog, modal, and blocks until it closes.
     *
     * @param owner the component whose window owns the dialog, or {@code null}
     * @param texts the framework's texts
     * @param title the title
     * @param message the message
     * @param cause the failure
     */
    public static void show(@Nullable Component owner, UiTexts texts, String title, String message, Throwable cause) {
        show(owner, title, message, cause, texts.details(), texts.copy(), texts.close());
    }

    /**
     * Shows the error dialog, modal, and blocks until it closes.
     *
     * @param owner the component whose window owns the dialog, or {@code null}
     * @param title the title
     * @param message the message
     * @param cause the failure
     * @param detailsText the label of the details
     * @param copyText the text of the Copy button
     * @param closeText the text of the Close button
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static void show(
            @Nullable Component owner,
            String title,
            String message,
            Throwable cause,
            String detailsText,
            String copyText,
            String closeText) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, title, Dialog.ModalityType.APPLICATION_MODAL);
        String details = details(cause);
        JTextArea area = new JTextArea(details);
        area.setEditable(false);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        JButton copy = new JButton(copyText);
        copy.addActionListener(event ->
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(details), null));
        JButton close = new JButton(closeText);
        close.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        buttons.add(copy);
        buttons.add(close);
        JPanel center = new JPanel(new BorderLayout());
        center.add(new JLabel(detailsText), BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        JPanel panel = new JPanel(new BorderLayout(PADDING, PADDING));
        panel.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        panel.add(new JLabel(message), BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.getRootPane().setDefaultButton(close);
        dialog.pack();
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
    }
}
