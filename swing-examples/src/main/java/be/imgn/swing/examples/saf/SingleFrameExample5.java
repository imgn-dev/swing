package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Concurrency;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.ToolBarEntry;
import be.imgn.swing.ui.Ui;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;

/**
 * An image viewer whose next, previous and refresh commands choose an image that one load command reads in a task, with
 * progress, and whose stop command cancels it. A new load replaces the one that runs. Next and previous are enabled
 * only where there is an image to go to. The images are the original's huge Mars Rover images, in its order: the
 * original loads them from JPL's Photojournal; the port bundles them in its resources, so that it works offline. As
 * reading them locally is much faster than downloading them, each step of a read's progress pauses a little, so that
 * the progress, the restart and the stop stay visible.
 */
@Application(vendor = "Sun", version = "1.0")
public final class SingleFrameExample5 implements SingleFrame {

    private static final List<String> IMAGES = List.of(
            "resources/PIA03171.jpg",
            "resources/PIA02652.jpg",
            "resources/PIA05108.jpg",
            "resources/PIA02696.jpg",
            "resources/PIA05049.jpg",
            "resources/PIA05460.jpg",
            "resources/PIA07327.jpg",
            "resources/PIA05117.jpg",
            "resources/PIA05199.jpg",
            "resources/PIA05990.jpg",
            "resources/PIA03623.jpg");
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final double PERCENT = 100;
    // The reader reports about 20 steps per image, so a read lasts about a second.
    private static final Duration STEP_PAUSE = Duration.ofMillis(50);

    @Exposed
    final JLabel image = new JLabel();

    @Exposed
    final Property<Boolean> nextImageEnabled = Property.of(true);

    @Exposed
    final Property<Boolean> previousImageEnabled = Property.of(false);

    private final SingleFrameExample5Commands commands;
    private final Ui ui;
    private final ExampleMessages messages;
    private int imageIndex;
    private volatile Hold hold = percentageDone -> Thread.sleep(STEP_PAUSE);

    @Inject
    SingleFrameExample5(SingleFrameExample5Commands commands, Ui ui, ExampleMessages messages) {
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
        Swing.launch(SingleFrameExample5.class, args);
    }

    @Override
    public JComponent content() {
        image.setOpaque(true);
        image.setHorizontalAlignment(SwingConstants.CENTER);
        image.setVerticalAlignment(SwingConstants.CENTER);
        JScrollPane scroll = new JScrollPane(image);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 2));
        scroll.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        return scroll;
    }

    // As in the original, the first image loads once the main window is shown, so that loading it doesn't delay it.
    @Override
    public void ready() {
        commands.refreshImage().execute();
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(ui.menus()
                .file()
                .with(
                        commands.previousImage(),
                        commands.nextImage(),
                        commands.refreshImage(),
                        commands.stopLoading(),
                        MenuEntry.SEPARATOR,
                        commands.exit()));
    }

    @Override
    public JToolBar toolBar() {
        return ui.toolBar(
                commands.previousImage(),
                commands.nextImage(),
                commands.refreshImage(),
                ToolBarEntry.SEPARATOR,
                commands.stopLoading());
    }

    @Command(enabled = "nextImageEnabled")
    void nextImage() {
        show(Math.min(imageIndex + 1, IMAGES.size() - 1));
    }

    @Command(enabled = "previousImageEnabled")
    void previousImage() {
        show(Math.max(imageIndex - 1, 0));
    }

    @Command
    void refreshImage() {
        show(imageIndex);
    }

    @Command(concurrency = Concurrency.RESTART)
    Task<BufferedImage> load() {
        String location = IMAGES.get(imageIndex);
        URL url = Objects.requireNonNull(SingleFrameExample5.class.getResource(location), location);
        showMessage(messages.loadingWait());
        return Task.of((TaskContext context) -> read(url, context))
                .onSuccess(loaded -> {
                    image.setToolTipText(messages.imageTooltip(location, loaded.getWidth(), loaded.getHeight()));
                    image.setText(null);
                    image.setIcon(new ImageIcon(loaded));
                })
                .onFailure(failure -> showMessage(messages.loadingFailed(location)));
    }

    // Shows the cancelled message here, once the load is cancelled, rather than in its onCancel: a load that a new one
    // replaces is cancelled too, after the new one showed its message, which must stay.
    @Command(cancels = "load")
    void stopLoading() {
        showMessage(messages.loadingCancelled());
    }

    /**
     * Makes each load wait for a hold at each step of its read's progress, from the first, in the middle of the read,
     * rather than pause, so that tests act while it runs.
     *
     * @param hold the hold
     */
    void hold(Hold hold) {
        this.hold = hold;
    }

    private void show(int index) {
        imageIndex = index;
        nextImageEnabled.set(imageIndex < IMAGES.size() - 1);
        previousImageEnabled.set(imageIndex > 0);
        commands.load().execute();
    }

    private void showMessage(String text) {
        image.setToolTipText("");
        image.setText(text);
        image.setIcon(null);
    }

    private BufferedImage read(URL url, TaskContext context) throws IOException {
        try (InputStream in = url.openStream();
                ImageInputStream input = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No reader for " + url);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                reader.addIIOReadProgressListener(new Progress(context, reader, hold));
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** What a load waits for at each step of its read's progress, for tests. */
    @FunctionalInterface
    interface Hold {

        /**
         * Waits until the read may go on.
         *
         * @param percentageDone how much of the image the read has decoded, from 0 to 100
         * @throws InterruptedException if the load is cancelled while it waits
         */
        void await(float percentageDone) throws InterruptedException;
    }

    /** Reports the progress of a read, and aborts it once the task is cancelled. */
    private static final class Progress implements IIOReadProgressListener {
        private final TaskContext context;
        private final ImageReader reader;
        private final Hold hold;

        Progress(TaskContext context, ImageReader reader, Hold hold) {
            this.context = context;
            this.reader = reader;
            this.hold = hold;
        }

        @Override
        public void imageProgress(ImageReader source, float percentageDone) {
            context.progress(Math.min(1, percentageDone / PERCENT));
            try {
                hold.await(percentageDone);
            } catch (InterruptedException e) {
                // Cancelling the task interrupts its thread, which the reader ignores: the abort below stops it.
                Thread.currentThread().interrupt();
            }
            if (context.isCancelled()) {
                reader.abort();
            }
        }

        @Override
        public void sequenceStarted(ImageReader source, int minIndex) {}

        @Override
        public void sequenceComplete(ImageReader source) {}

        @Override
        public void imageStarted(ImageReader source, int imageIndex) {}

        @Override
        public void imageComplete(ImageReader source) {}

        @Override
        public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {}

        @Override
        public void thumbnailProgress(ImageReader source, float percentageDone) {}

        @Override
        public void thumbnailComplete(ImageReader source) {}

        @Override
        public void readAborted(ImageReader source) {}
    }
}
