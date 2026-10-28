package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.StandardMenus;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.ToolBarEntry;
import be.imgn.swing.ui.Ui;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
 * An image viewer whose next, previous and refresh commands load their image in a task, with progress, and whose stop
 * command cancels it. Next and previous are enabled only where there is an image to go to. The original loads huge Mars
 * Rover images from JPL; the port loads images of its own resources, so that it works offline.
 */
@Application(name = "SingleFrameExample5", vendor = "Sun", version = "1.0")
// The framework reaches component fields and command targets from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class SingleFrameExample5 implements SingleFrame {

    private static final List<String> IMAGES =
            List.of("resources/mars-1.png", "resources/mars-2.png", "resources/mars-3.png");
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final double PERCENT = 100;

    final JLabel image = new JLabel();
    final Property<Boolean> nextImageEnabled = Property.of(true);
    final Property<Boolean> previousImageEnabled = Property.of(false);
    final Property<Boolean> stopLoadingEnabled = Property.of(false);
    // Each load gets a generation; stopping moves to the next one, which the running load sees.
    final Property<Integer> current = Property.of(0);
    private final SingleFrameExample5Commands commands;
    private final StandardMenus menus;
    private final Ui ui;
    private final ExampleMessages messages;
    private final Ready ready;
    private int imageIndex;
    private int loading;

    @Inject
    SingleFrameExample5(
            SingleFrameExample5Commands commands, StandardMenus menus, Ui ui, ExampleMessages messages, Ready ready) {
        this.commands = commands;
        this.menus = menus;
        this.ui = ui;
        this.messages = messages;
        this.ready = ready;
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
        // Once the main window is shown, the first image loads.
        ui.bind(
                isReady -> {
                    if (isReady && image.getIcon() == null) {
                        commands.refreshImage().execute();
                    }
                },
                ready.isReady());
        return scroll;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(menus.file()
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
        JToolBar toolBar = ui.toolBar(
                commands.previousImage(),
                commands.nextImage(),
                commands.refreshImage(),
                ToolBarEntry.SEPARATOR,
                commands.stopLoading());
        toolBar.setFloatable(false);
        return toolBar;
    }

    @Command(enabled = "nextImageEnabled")
    Task<BufferedImage> nextImage() {
        imageIndex = Math.min(imageIndex + 1, IMAGES.size() - 1);
        return show(IMAGES.get(imageIndex));
    }

    @Command(enabled = "previousImageEnabled")
    Task<BufferedImage> previousImage() {
        imageIndex = Math.max(imageIndex - 1, 0);
        return show(IMAGES.get(imageIndex));
    }

    @Command
    Task<BufferedImage> refreshImage() {
        return show(IMAGES.get(imageIndex));
    }

    @Command(enabled = "stopLoadingEnabled")
    void stopLoading() {
        current.set(current.get() + 1);
    }

    private Task<BufferedImage> show(String location) {
        nextImageEnabled.set(imageIndex < IMAGES.size() - 1);
        previousImageEnabled.set(imageIndex > 0);
        URL url = Objects.requireNonNull(SingleFrameExample5.class.getResource(location), location);
        int generation = current.get();
        loading++;
        stopLoadingEnabled.set(true);
        showMessage(messages.loadingWait());
        return Task.of((TaskContext context) -> load(url, generation, context))
                .onSuccess(loaded -> {
                    if (current.get() == generation) {
                        image.setToolTipText(messages.imageTooltip(location, loaded.getWidth(), loaded.getHeight()));
                        image.setText(null);
                        image.setIcon(new ImageIcon(loaded));
                    } else {
                        showMessage(messages.loadingCancelled());
                    }
                    loaded();
                })
                .onFailure(failure -> {
                    showMessage(messages.loadingFailed(location));
                    loaded();
                })
                .onCancel(() -> {
                    showMessage(messages.loadingCancelled());
                    loaded();
                });
    }

    private void loaded() {
        loading--;
        stopLoadingEnabled.set(loading > 0);
    }

    private void showMessage(String text) {
        image.setToolTipText("");
        image.setText(text);
        image.setIcon(null);
    }

    private BufferedImage load(URL url, int generation, TaskContext context) throws IOException {
        try (InputStream in = url.openStream();
                ImageInputStream input = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No reader for " + url);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                reader.addIIOReadProgressListener(new Progress(context, generation, reader));
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Reports the progress of a read, and aborts it once stopped. */
    private final class Progress implements IIOReadProgressListener {
        private final TaskContext context;
        private final int generation;
        private final ImageReader reader;

        Progress(TaskContext context, int generation, ImageReader reader) {
            this.context = context;
            this.generation = generation;
            this.reader = reader;
        }

        @Override
        public void imageProgress(ImageReader source, float percentageDone) {
            context.progress(Math.min(1, percentageDone / PERCENT));
            if (context.isCancelled() || current.get() != generation) {
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
