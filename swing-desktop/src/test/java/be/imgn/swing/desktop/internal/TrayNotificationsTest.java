package be.imgn.swing.desktop.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.UiExecutor;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TrayNotificationsTest {

    private final FakeTray tray = new FakeTray();
    private final TrayNotifications notifications =
            new TrayNotifications(tray, new InlineExecutor(), Optional::empty, "Memo");

    @Test
    void addsTheIconAtTheFirstNotificationThenShowsEachOne() {
        tray.supported = true;

        notifications.notify("Saved", "notes.txt");
        notifications.notify("Saved", "todo.txt");
        notifications.remove();

        assertThat(notifications.isSupported()).isTrue();
        assertThat(tray.calls)
                .containsExactly("add Memo", "message Saved: notes.txt", "message Saved: todo.txt", "remove");
    }

    @Test
    void showsNothingWithoutASystemTray() {
        notifications.notify("Saved", "notes.txt");
        notifications.remove();

        assertThat(notifications.isSupported()).isFalse();
        assertThat(tray.calls).isEmpty();
    }

    private static final class FakeTray implements TrayAdaptor {
        private final List<String> calls = new ArrayList<>();
        private boolean supported;

        @Override
        public boolean isSupported() {
            return supported;
        }

        @Override
        public void add(@Nullable Image image, String tooltip) {
            calls.add("add " + tooltip);
        }

        @Override
        public void displayMessage(String caption, String text) {
            calls.add("message " + caption + ": " + text);
        }

        @Override
        public void remove() {
            calls.add("remove");
        }
    }

    private static final class InlineExecutor implements UiExecutor {
        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public boolean isUiThread() {
            return true;
        }
    }
}
