package be.imgn.swing.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.EventQueue;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class EdtExecutorTest {

    @Test
    void runsEachTaskOnTheEdtInOrder() throws Exception {
        List<String> runs = new CopyOnWriteArrayList<>();
        EdtExecutor executor = new EdtExecutor();

        executor.execute(() -> runs.add("first on the EDT: " + EventQueue.isDispatchThread()));
        executor.execute(() -> runs.add("second on the EDT: " + EventQueue.isDispatchThread()));
        EventQueue.invokeAndWait(() -> {});

        assertThat(runs).containsExactly("first on the EDT: true", "second on the EDT: true");
    }
}
