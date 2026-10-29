package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * A command that lists the files of a directory in a task, which adds them to the list as it finds them and tells which
 * directory it lists, and a command that stops it, enabled only while it runs.
 */
@Application(name = "ActionExample4", vendor = "Sun", version = "1.0")
public final class ActionExample4 implements SingleFrame {

    private static final int COLUMNS = 32;
    private static final int ROWS = 12;
    private static final int BUFFER = 10;
    private static final int MARGIN = 2;
    private static final int PADDING = 4;

    @Exposed
    final Property<Boolean> stopEnabled = Property.of(false);

    private final JTextField root = new JTextField(System.getProperty("java.home"), COLUMNS);
    private final DefaultListModel<File> files = new DefaultListModel<>();
    private final Property<String> message = Property.of(" ");
    private final ActionExample4Commands commands;
    private final Ui ui;
    private final ExampleMessages messages;
    private int generation;

    @Inject
    ActionExample4(ActionExample4Commands commands, Ui ui, ExampleMessages messages) {
        this.commands = commands;
        this.ui = ui;
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(ActionExample4.class, args);
    }

    @Override
    public JComponent content() {
        root.setAction(commands.go().asAction());
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(root);
        buttons.add(commands.go().createButton());
        buttons.add(commands.stop().createButton());
        JList<File> list = new JList<>(files);
        list.setVisibleRowCount(ROWS);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder(MARGIN, PADDING, MARGIN, PADDING));
        JLabel status = new JLabel();
        status.setBorder(BorderFactory.createEmptyBorder(MARGIN, PADDING, MARGIN, PADDING));
        ui.bind(status::setText, message);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.PAGE_START);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(status, BorderLayout.PAGE_END);
        return panel;
    }

    @Command
    Task<Integer> go() {
        stop();
        files.clear();
        int listing = ++generation;
        File directory = new File(root.getText());
        stopEnabled.set(true);
        return Task.of((TaskContext context) -> list(directory, listing, context))
                .onSuccess(count -> {
                    message.set(messages.listed(count));
                    done(listing);
                })
                .onCancel(() -> done(listing))
                .onFailure(failure -> done(listing));
    }

    @Command(enabled = "stopEnabled")
    void stop() {
        generation++;
        stopEnabled.set(false);
    }

    private void done(int listing) {
        if (listing == generation) {
            stopEnabled.set(false);
        }
    }

    // Lists the files under a directory, ten at a time; a newer listing or stop ends it.
    private int list(File directory, int listing, TaskContext context) {
        List<File> buffer = new ArrayList<>();
        int count = expand(directory, listing, context, buffer);
        publish(buffer, listing);
        return count;
    }

    private int expand(File file, int listing, TaskContext context, List<File> buffer) {
        if (context.isCancelled() || listing != generation) {
            return 0;
        }
        if (!file.isDirectory()) {
            buffer.add(file);
            if (buffer.size() >= BUFFER) {
                publish(buffer, listing);
            }
            return 1;
        }
        message.set(messages.listing(file.toString()));
        File[] children = file.listFiles();
        int count = 0;
        if (children != null) {
            for (File child : children) {
                count += expand(child, listing, context, buffer);
            }
        }
        return count;
    }

    private void publish(List<File> buffer, int listing) {
        List<File> found = List.copyOf(buffer);
        buffer.clear();
        EventQueue.invokeLater(() -> {
            if (listing == generation) {
                found.forEach(files::addElement);
            }
        });
    }
}
