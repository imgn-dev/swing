package be.imgn.swing.testing;

import static java.util.Objects.requireNonNull;

import be.imgn.swing.desktop.Notifications;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The tester's {@link Notifications}: supported, it records each notification. */
public final class FakeNotifications implements Notifications {

    private final List<Notification> notified = new CopyOnWriteArrayList<>();

    FakeNotifications() {}

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void notify(String caption, String text) {
        notified.add(new Notification(requireNonNull(caption, "caption"), requireNonNull(text, "text")));
    }

    /**
     * Returns the notifications shown, in order.
     *
     * @return the notifications
     */
    public List<Notification> notified() {
        return List.copyOf(notified);
    }

    /**
     * A notification shown.
     *
     * @param caption its caption
     * @param text its text
     */
    public record Notification(String caption, String text) {}
}
