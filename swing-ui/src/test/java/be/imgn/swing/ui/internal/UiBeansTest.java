package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.EventExecutor;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.ui.OnEdt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class UiBeansTest {

    @Test
    void handsTheDeliveriesOfOnEdtToTheUiExecutor() {
        List<Runnable> posted = new ArrayList<>();
        UiExecutor recording = new UiExecutor() {
            @Override
            public void execute(Runnable task) {
                posted.add(task);
            }

            @Override
            public boolean isUiThread() {
                return false;
            }
        };
        Runnable delivery = () -> {};

        EventExecutor executor = UiBeans.onEdt(recording);
        executor.executor().execute(delivery);

        assertThat(executor.annotation()).isEqualTo(OnEdt.class);
        assertThat(posted).containsExactly(delivery);
    }

    @Test
    void doesntCatchAnErrorThrownByADeliveryOfOnEdt() {
        UiExecutor inline = new UiExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }

            @Override
            public boolean isUiThread() {
                return true;
            }
        };
        Error error = new Error("thrown by an @OnEdt method");

        Executor executor = UiBeans.onEdt(inline).executor();

        assertThatThrownBy(() -> executor.execute(() -> {
                    throw error;
                }))
                .isSameAs(error);
    }
}
