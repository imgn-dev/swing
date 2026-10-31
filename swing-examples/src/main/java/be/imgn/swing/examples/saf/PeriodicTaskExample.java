package be.imgn.swing.examples.saf;

import be.imgn.gaia.core.time.Ticker;
import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.time.Duration;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * A task that runs until the application exits, and shows the time elapsed every half second. It starts once the main
 * window is shown, and belongs to the view's scope, which cancels it at shutdown.
 */
@Application(name = "PeriodicTaskExample")
public final class PeriodicTaskExample implements SingleFrame {

    /** The period of the task. */
    static final Duration PERIOD = Duration.ofMillis(500);

    private final Property<String> elapsed;
    private final Ui ui;
    private final Ticker ticker;
    private final ExampleMessages messages;

    @Inject
    PeriodicTaskExample(Ui ui, Ticker ticker, ExampleMessages messages) {
        this.ui = ui;
        this.ticker = ticker;
        this.messages = messages;
        this.elapsed = Property.of(messages.ready());
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(PeriodicTaskExample.class, args);
    }

    @Override
    public JComponent content() {
        JLabel label = new JLabel();
        ui.bind(label::setText, elapsed);
        return label;
    }

    @Override
    public void ready() {
        long start = ticker.read();
        ui.run(
                Task.of((TaskContext context) -> {
                    while (!context.isCancelled()) {
                        Thread.sleep(PERIOD);
                        elapsed.set(messages.elapsed(
                                Duration.ofNanos(ticker.read() - start).toMillis()));
                    }
                    return "stopped";
                }),
                Block.NONE);
    }

    Property<String> elapsedText() {
        return elapsed;
    }
}
