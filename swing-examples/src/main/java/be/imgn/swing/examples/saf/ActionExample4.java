package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Concurrency;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.jspecify.annotations.Nullable;

/**
 * A command that lists the files of a directory in a task, which adds them to the list as it finds them and tells which
 * directory it lists, and a command that stops it, enabled only while it runs. A new listing replaces the one that
 * runs.
 */
@Application(vendor = "Sun", version = "1.0")
public final class ActionExample4 implements SingleFrame {

    private static final int COLUMNS = 32;
    private static final int ROWS = 12;
    private static final int MARGIN = 2;
    private static final int PADDING = 4;

    private final JTextField root = new JTextField(System.getProperty("java.home"), COLUMNS);
    private final DefaultListModel<Path> files = new DefaultListModel<>();
    private final Property<String> message = Property.of(" ");
    private final ActionExample4Commands commands;
    private final Ui ui;
    private final ExampleMessages messages;
    private volatile @Nullable CountDownLatch hold;

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
        JList<Path> list = new JList<>(files);
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

    @Command(concurrency = Concurrency.RESTART)
    Task<Integer> go() {
        files.clear();
        Path directory = Path.of(root.getText());
        return Task.<Integer, Path>of(
                        (context, publish) -> expand(directory, context, publish),
                        found -> found.forEach(files::addElement))
                .onSuccess(count -> message.set(messages.listed(count)));
    }

    @Command(cancels = "go")
    void stop() {}

    /**
     * Makes the listing wait for a latch after each file it publishes, so that tests act while it runs.
     *
     * @param latch the latch
     */
    void hold(CountDownLatch latch) {
        this.hold = latch;
    }

    // Lists the files under a directory, publishing each one; the cancellation of the task ends it.
    private int expand(Path file, TaskContext context, Consumer<? super Path> publish) throws InterruptedException {
        if (context.isCancelled()) {
            return 0;
        }
        if (!Files.isDirectory(file)) {
            publish.accept(file);
            CountDownLatch latch = hold;
            if (latch != null) {
                latch.await();
            }
            return 1;
        }
        message.set(messages.listing(file.toString()));
        int count = 0;
        // A directory that can't be read lists nothing, as the original's listFiles does.
        try (DirectoryStream<Path> children = Files.newDirectoryStream(file)) {
            for (Path child : children) {
                count += expand(child, context, publish);
            }
        } catch (IOException | DirectoryIteratorException e) {
            return count;
        }
        return count;
    }
}
