package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Duration;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JToolBar;

/**
 * Four commands that run the same task, each blocking something else while it runs: the command, the component that
 * started it, the window, or the application. The task reports its progress and its steps, which the status label
 * shows.
 */
@Application(name = "BlockingExample1", vendor = "Sun", version = "1.0")
public final class BlockingExample1 implements SingleFrame {

    /** The number of steps of the task. */
    static final int STEPS = 50;

    private static final Duration STEP = Duration.ofMillis(150);
    private static final int GAP = 10;

    private final Property<String> status = Property.of(" ");
    private final BlockingExample1Commands commands;
    private final BlockingExample1Menus demos;
    private final Ui ui;
    private final ExampleMessages messages;
    private Duration step = STEP;

    @Inject
    BlockingExample1(BlockingExample1Commands commands, BlockingExample1Menus demos, Ui ui, ExampleMessages messages) {
        this.commands = commands;
        this.demos = demos;
        this.ui = ui;
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(BlockingExample1.class, args);
    }

    @Override
    public JComponent content() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, GAP, GAP));
        buttons.add(commands.blockAction().createButton());
        buttons.add(commands.blockComponent().createButton());
        buttons.add(commands.blockWindow().createButton());
        buttons.add(commands.blockApplication().createButton());
        JLabel label = new JLabel();
        ui.bind(label::setText, status);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        panel.add(buttons, BorderLayout.CENTER);
        panel.add(label, BorderLayout.PAGE_END);
        return panel;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(demos.blockingDemos()
                .with(
                        commands.blockAction(),
                        commands.blockComponent(),
                        commands.blockWindow(),
                        commands.blockApplication(),
                        MenuEntry.SEPARATOR,
                        commands.exit()));
    }

    @Override
    public JToolBar toolBar() {
        JToolBar toolBar = ui.toolBar(
                commands.blockAction(), commands.blockComponent(), commands.blockWindow(), commands.blockApplication());
        toolBar.setFloatable(false);
        return toolBar;
    }

    @Command(block = Block.COMMAND)
    Task<Integer> blockAction() {
        return doNothing();
    }

    @Command(block = Block.COMPONENT)
    Task<Integer> blockComponent() {
        return doNothing();
    }

    @Command(block = Block.WINDOW)
    Task<Integer> blockWindow() {
        return doNothing();
    }

    @Command(block = Block.APPLICATION)
    Task<Integer> blockApplication() {
        return doNothing();
    }

    // A task that briefly does nothing, step by step, and returns the number of steps it took.
    private Task<Integer> doNothing() {
        Duration pause = step;
        return Task.<Integer>of((TaskContext context) -> {
                    for (int index = 0; index < STEPS; index++) {
                        status.set(messages.step(index));
                        context.message(messages.step(index));
                        Thread.sleep(pause);
                        context.progress((index + 1) / (double) STEPS);
                    }
                    return STEPS;
                })
                .onSuccess(ignored -> status.set(messages.succeeded()))
                .onCancel(() -> status.set(messages.cancelled()));
    }

    /**
     * Sets the duration of each step of the task.
     *
     * @param duration the duration
     */
    void step(Duration duration) {
        this.step = duration;
    }

    /**
     * Returns the status the label shows.
     *
     * @return the status
     */
    Property<String> status() {
        return status;
    }
}
