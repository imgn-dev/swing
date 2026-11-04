package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.swing.Property;
import be.imgn.swing.Subscription;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.Notifications;
import java.awt.EventQueue;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The delivery of the changes of observables to the EDT, for changes made off the EDT and during a drain. */
class EdtDeliveryTest {

    private final DisposalScope scope = DisposalScope.root("test");
    private final List<String> seen = new CopyOnWriteArrayList<>();
    private final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    private Thread.@Nullable UncaughtExceptionHandler previousHandler;

    @BeforeEach
    void catchTheFailuresOfTheEdt() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
    }

    @AfterEach
    void restore() throws Exception {
        drained();
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        scope.close();
    }

    @Test
    void deliversTheUpdatesOfAChangeEvenWhenAnActionGivenBeforeThemThrows() throws Exception {
        Property<Integer> property = Property.of(0);
        Subscription throwing = property.subscribe(
                scope,
                value -> Notifications.whenDone(() -> {
                    throw new IllegalStateException("action");
                }));
        EdtDelivery.subscribe(scope, property, value -> seen.add("view " + value), EdtDelivery.Order.VIEW);

        assertThatIllegalStateException().isThrownBy(() -> property.set(1)).withMessage("action");
        drained();
        throwing.close();
        property.set(2);
        drained();

        assertThat(seen).containsExactly("view 1", "view 2");
    }

    @Test
    void deliversAnUpdateQueuedBeforeAListenerThatThrows() throws Exception {
        Property<Integer> property = Property.of(0);
        EdtDelivery.subscribe(scope, property, value -> seen.add("view " + value), EdtDelivery.Order.VIEW);
        Subscription throwing = property.subscribe(scope, value -> {
            throw new IllegalStateException("listener");
        });

        assertThatIllegalStateException().isThrownBy(() -> property.set(1)).withMessage("listener");
        drained();
        throwing.close();
        property.set(2);
        drained();

        assertThat(seen).containsExactly("view 1", "view 2");
    }

    @Test
    void runsAnUpdateThatATargetQueuesInTheSameDrainInItsPlace() throws Exception {
        Property<Integer> property = Property.of(0);
        Property<Integer> derived = Property.of(0);
        EdtDelivery.subscribe(
                scope,
                property,
                value -> {
                    seen.add("first view " + value);
                    derived.set(value * 10);
                    // Runs after the drain, unless the derived update waited for a drain posted after it.
                    EventQueue.invokeLater(() -> seen.add("next event"));
                },
                EdtDelivery.Order.VIEW);
        EdtDelivery.subscribe(scope, derived, value -> seen.add("framework " + value), EdtDelivery.Order.FRAMEWORK);
        EdtDelivery.subscribe(scope, property, value -> seen.add("second view " + value), EdtDelivery.Order.VIEW);

        property.set(1);
        drained();

        assertThat(seen).containsExactly("first view 1", "framework 10", "second view 1", "next event");
    }

    @Test
    void runsTheRemainingUpdatesInTheNextDrainWhenATargetThrows() throws Exception {
        Property<Integer> property = Property.of(0);
        EdtDelivery.subscribe(
                scope,
                property,
                value -> {
                    seen.add("throwing view " + value);
                    throw new IllegalStateException("target");
                },
                EdtDelivery.Order.VIEW);
        EdtDelivery.subscribe(scope, property, value -> seen.add("second view " + value), EdtDelivery.Order.VIEW);

        property.set(1);
        drained();

        assertThat(seen).containsExactly("throwing view 1", "second view 1");
        assertThat(uncaught).singleElement().extracting(Throwable::getMessage).isEqualTo("target");
    }

    // Waits for the drains posted so far, and for those they post.
    private static void drained() throws Exception {
        for (int round = 0; round < 3; round++) {
            EventQueue.invokeAndWait(() -> {});
        }
    }
}
