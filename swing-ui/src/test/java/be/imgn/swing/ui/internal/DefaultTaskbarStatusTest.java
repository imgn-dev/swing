package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.UiExecutor;
import java.awt.Image;
import java.awt.Taskbar.Feature;
import java.awt.Window;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DefaultTaskbarStatusTest {

    private final FakeAdaptor taskbar = new FakeAdaptor();
    private final DefaultTaskbarStatus status = new DefaultTaskbarStatus(
            taskbar,
            new UiExecutor() {
                @Override
                public void execute(Runnable task) {
                    task.run();
                }

                @Override
                public boolean isUiThread() {
                    return true;
                }
            },
            Optional::empty);

    @Test
    void showsTheProgressOnTheApplicationsEntryWhenSupported() {
        taskbar.supported.add(Feature.PROGRESS_VALUE);

        status.progress(40);
        status.clearProgress();

        assertThat(taskbar.calls).containsExactly("progress 40", "progress -1");
    }

    // A headless system can't create a window, so the features of windows are checked without an active frame.
    @Test
    void showsNothingThroughTheFeaturesOfWindowsWithoutAnActiveFrame() {
        taskbar.supported.add(Feature.PROGRESS_VALUE_WINDOW);
        taskbar.supported.add(Feature.USER_ATTENTION_WINDOW);

        status.progress(100);
        status.requestAttention();

        assertThat(taskbar.calls).isEmpty();
    }

    @Test
    void doesNothingWithoutTheFeatures() {
        status.progress(10);
        status.badge("3");
        status.requestAttention();

        assertThat(taskbar.calls).isEmpty();
    }

    @Test
    void showsAndRemovesTheBadge() {
        taskbar.supported.add(Feature.ICON_BADGE_TEXT);

        status.badge("3");
        status.clearBadge();

        assertThat(taskbar.calls).containsExactly("badge 3", "badge null");
    }

    @Test
    void requestsTheAttentionOfTheUserOnTheApplication() {
        taskbar.supported.add(Feature.USER_ATTENTION);

        status.requestAttention();

        assertThat(taskbar.calls).containsExactly("attention");
    }

    @Test
    void rejectsAProgressOutsideZeroToOneHundredAndABlankBadge() {
        assertThatThrownBy(() -> status.progress(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> status.progress(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> status.badge(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeAdaptor implements TaskbarAdaptor {
        private final Set<Feature> supported = EnumSet.noneOf(Feature.class);
        private final List<String> calls = new ArrayList<>();

        @Override
        public boolean isSupported(Feature feature) {
            return supported.contains(feature);
        }

        @Override
        public void setProgressValue(int value) {
            calls.add("progress " + value);
        }

        @Override
        public void setWindowProgressValue(Window window, int value) {
            calls.add("window progress " + value);
        }

        @Override
        public void setIconBadge(@Nullable String badge) {
            calls.add("badge " + badge);
        }

        @Override
        public void requestUserAttention() {
            calls.add("attention");
        }

        @Override
        public void requestWindowUserAttention(Window window) {
            calls.add("window attention");
        }

        @Override
        public void setIconImage(Image image) {
            calls.add("icon");
        }
    }
}
