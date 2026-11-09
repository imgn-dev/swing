package be.imgn.swing.desktop.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.UiExecutor;
import be.imgn.swing.desktop.SecondInstanceHandler;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The single instance's startup step: exiting once the arguments are forwarded, and the launches held until ready. */
class SingleInstanceTest {

    private static final Path STATE = Path.of("state");

    private final List<String> events = new ArrayList<>();
    private final List<Runnable> posted = new ArrayList<>();
    private final SecondInstanceHandler handler = arguments -> events.add("launched " + arguments.values());
    private final UiExecutor executor = new UiExecutor() {
        @Override
        public void execute(Runnable task) {
            posted.add(task);
        }

        @Override
        public boolean isUiThread() {
            return true;
        }
    };
    private @Nullable Consumer<List<String>> receiver;

    private SingleInstance instance(boolean enabled, boolean first) {
        InstanceLock lock = (directory, arguments, received) -> {
            events.add("acquire " + directory + " " + arguments);
            receiver = received;
            return first ? Optional.<Closeable>of(() -> events.add("closed")) : Optional.empty();
        };
        return new SingleInstance(
                enabled,
                () -> handler,
                lock,
                () -> STATE,
                List.of("a.txt"),
                status -> events.add("exit " + status),
                executor);
    }

    @Test
    void doesNothingWithoutAHandler() {
        instance(false, true).start();

        assertThat(events).isEmpty();
    }

    @Test
    void exitsOnceTheArgumentsAreForwarded() {
        instance(true, false).start();

        assertThat(events).containsExactly("acquire state [a.txt]", "exit 0");
    }

    @Test
    void holdsTheLaunchesUntilReadyThenDeliversThemInOrderAndReleasesTheLock() throws Exception {
        SingleInstance instance = instance(true, true);
        instance.start();
        Consumer<List<String>> received = Objects.requireNonNull(receiver);

        received.accept(List.of("one.txt"));
        received.accept(List.of("two.txt"));

        assertThat(events).containsExactly("acquire state [a.txt]");

        instance.ready();
        received.accept(List.of("three.txt"));
        posted.forEach(Runnable::run);
        instance.close();

        assertThat(events)
                .containsExactly(
                        "acquire state [a.txt]",
                        "launched [one.txt]",
                        "launched [two.txt]",
                        "launched [three.txt]",
                        "closed");
    }
}
