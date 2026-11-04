package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The application's bus and the counter's subscribers, through the tester. */
class EventBusTest {

    @Test
    void publishReturnsOnceTheOnVirtualThreadDeliveriesHaveRun() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            CountLog log = app.bean(CountLog.class);

            app.publish(new Counted(1));
            assertThat(log.counts()).containsExactly(1);

            app.publish(new Counted(2));
            assertThat(log.counts()).containsExactlyInAnyOrder(1, 2);
        }
    }

    @Test
    void publishReturnsOnceTheOnEdtDeliveriesHaveRunOnTheEdt() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            CountEcho echo = app.bean(CountEcho.class);

            app.publish(new Counted(3));

            assertThat(echo.echoes()).containsExactly("3 on the EDT");
        }
    }

    @Test
    void aViewScopedSubscriberHearsTheEventsWhileItsViewIsOpenOnly() {
        AtomicReference<CountWatcher> watcher = new AtomicReference<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            EventBus bus = app.bean(EventBus.class);
            app.dialogs().answerDialog(CountDialog.class, dialog -> {
                watcher.set(dialog.view().watcher());
                bus.publish(new Counted(1));
            });
            Ui ui = app.bean(Ui.class);

            app.call(() -> ui.showDialog(CountDialog.class));
            app.publish(new Counted(2));

            assertThat(Objects.requireNonNull(watcher.get()).heard()).containsExactly(new Counted(1));
        }
    }

    @Test
    void closingAViewDoesNotCancelARunningOnVirtualThreadDeliveryToItsInstances() {
        AtomicReference<CountHolder> holder = new AtomicReference<>();
        AtomicBoolean started = new AtomicBoolean();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            EventBus bus = app.bean(EventBus.class);
            app.dialogs().answerDialog(CountDialog.class, dialog -> {
                CountHolder created = dialog.bean(CountHolder.class);
                holder.set(created);
                bus.publish(new Counted(4));
                try {
                    started.set(created.awaitStarted());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                dialog.commands(StandardCommands.class).cancel().execute();
            });
            Ui ui = app.bean(Ui.class);

            // The delivery waits for the view to close, then the call waits for the delivery.
            app.call(() -> ui.showDialog(CountDialog.class));

            assertThat(started).isTrue();
            assertThat(Objects.requireNonNull(holder.get()).kept()).containsExactly("4 after its view closed");
        }
    }

    @Test
    void anEventPublishedDuringAViewsBuildDoesNotReachTheSubscribersBuiltInThatBuild() {
        AtomicReference<CountWatcher> watcher = new AtomicReference<>();
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            app.dialogs()
                    .answerDialog(
                            CountDialog.class,
                            dialog -> watcher.set(dialog.view().watcher()));
            Ui ui = app.bean(Ui.class);

            app.call(() -> ui.showDialog(CountDialog.class));

            assertThat(app.published(CountDialog.Opened.class)).containsExactly(new CountDialog.Opened());
            assertThat(Objects.requireNonNull(watcher.get()).heard()).isEmpty();
        }
    }
}
