package be.imgn.swing.ui.internal;

import be.imgn.gaia.core.time.Ticker;
import be.imgn.swing.Scope;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.RunningTask;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;

/**
 * A glass pane that blocks a window while tasks run: it consumes mouse and key events, shows a busy cursor and, after
 * 300 ms, a progress indicator with a Cancel button.
 */
public final class BlockingGlassPane extends JComponent {

    /** How long the glass pane waits before it shows the progress indicator. */
    public static final Duration INDICATOR_DELAY = Duration.ofMillis(300);

    private static final long serialVersionUID = 1L;
    private static final int POLL_MILLIS = 50;
    private static final float SHADE = 0.15f;
    private static final int PERCENT = 100;
    private static final int PADDING = 12;

    private final transient Ticker ticker;
    private final transient Deque<RunningTask> tasks = new ArrayDeque<>();
    private final JPanel indicator = new JPanel();
    private final JProgressBar progress = new JProgressBar(0, PERCENT);
    private final JLabel message = new JLabel(" ");
    private final JButton cancel = new JButton();
    private final Timer timer;
    private transient DisposalScope subscriptions = DisposalScope.root("glass pane");
    private long startedAt;

    /**
     * Creates the glass pane.
     *
     * @param ticker the source of time
     * @param cancelText the text of the Cancel button
     */
    public BlockingGlassPane(Ticker ticker, String cancelText) {
        this.ticker = ticker;
        setOpaque(false);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setLayout(new GridBagLayout());
        MouseAdapter mouse = new ConsumingMouseAdapter();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(event -> event.consume());
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                event.consume();
            }

            @Override
            public void keyReleased(KeyEvent event) {
                event.consume();
            }

            @Override
            public void keyTyped(KeyEvent event) {
                event.consume();
            }
        });
        cancel.setText(cancelText);
        cancel.setCursor(Cursor.getDefaultCursor());
        cancel.addActionListener(event -> {
            RunningTask task = tasks.peekLast();
            if (task != null) {
                task.cancel();
            }
        });
        indicator.setLayout(new BoxLayout(indicator, BoxLayout.Y_AXIS));
        indicator.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        indicator.add(message);
        indicator.add(progress);
        indicator.add(cancel);
        indicator.setVisible(false);
        add(indicator);
        timer = new Timer(POLL_MILLIS, event -> showIndicatorWhenDue());
    }

    /**
     * Returns whether the progress indicator is shown.
     *
     * @return {@code true} once shown
     */
    public boolean isIndicatorShown() {
        return indicator.isVisible();
    }

    /**
     * Returns the Cancel button of the progress indicator.
     *
     * @return the button
     */
    public JButton cancelButton() {
        return cancel;
    }

    /**
     * Starts blocking for a task: the first one shows the glass pane.
     *
     * @param task the task
     */
    public void block(RunningTask task) {
        boolean first = tasks.isEmpty();
        tasks.addLast(task);
        follow(task);
        if (first) {
            startedAt = ticker.read();
            setVisible(true);
            requestFocusInWindow();
            timer.start();
        }
    }

    /**
     * Stops blocking for a task: the last one hides the glass pane.
     *
     * @param task the task
     */
    public void unblock(RunningTask task) {
        tasks.remove(task);
        if (tasks.isEmpty()) {
            timer.stop();
            subscriptions.close();
            indicator.setVisible(false);
            setVisible(false);
        } else {
            RunningTask last = tasks.peekLast();
            if (last != null) {
                follow(last);
            }
        }
    }

    /** Shows the progress indicator if the first task has been blocking for long enough. */
    public void showIndicatorWhenDue() {
        if (!tasks.isEmpty() && Duration.ofNanos(ticker.read() - startedAt).compareTo(INDICATOR_DELAY) >= 0) {
            timer.stop();
            indicator.setVisible(true);
            revalidate();
            repaint();
        }
    }

    private void follow(RunningTask task) {
        subscriptions.close();
        subscriptions = DisposalScope.root("glass pane");
        Scope scope = subscriptions;
        showProgress(task.progressValue().get());
        message.setText(
                task.messageValue().get().isEmpty() ? " " : task.messageValue().get());
        task.progressValue().subscribe(scope, this::showProgress);
        task.messageValue().subscribe(scope, text -> message.setText(text.isEmpty() ? " " : text));
    }

    private void showProgress(double fraction) {
        progress.setIndeterminate(fraction < 0);
        if (fraction >= 0) {
            progress.setValue((int) Math.round(fraction * PERCENT));
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D shaded = (Graphics2D) graphics.create();
        try {
            shaded.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, SHADE));
            shaded.setColor(Color.BLACK);
            shaded.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            shaded.dispose();
        }
    }

    // Consumes every mouse event the glass pane receives, so that nothing under it acts on one.
    private static final class ConsumingMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent event) {
            event.consume();
        }

        @Override
        public void mousePressed(MouseEvent event) {
            event.consume();
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            event.consume();
        }

        @Override
        public void mouseEntered(MouseEvent event) {
            event.consume();
        }

        @Override
        public void mouseExited(MouseEvent event) {
            event.consume();
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            event.consume();
        }

        @Override
        public void mouseMoved(MouseEvent event) {
            event.consume();
        }
    }
}
