package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class NotificationsTest {

    private final DisposalScope scope = DisposalScope.root("test");

    @Test
    void runsTheActionAtOnceOutsideOfListeners() {
        List<String> calls = new CopyOnWriteArrayList<>();

        Notifications.whenDone(() -> calls.add("action"));

        assertThat(calls).containsExactly("action");
    }

    @Test
    void runsTheActionsOnceEveryListenerOfTheChangeIsCalled() {
        Property<Integer> property = Property.of(0);
        List<String> calls = new CopyOnWriteArrayList<>();
        property.subscribe(scope, value -> Notifications.whenDone(() -> calls.add("first's action")));
        property.subscribe(scope, value -> calls.add("second"));
        property.subscribe(scope, value -> Notifications.whenDone(() -> calls.add("third's action")));

        property.set(1);

        assertThat(calls).containsExactly("second", "first's action", "third's action");
    }

    @Test
    void waitsForTheOutermostChangeWhenAListenerCausesAnother() {
        Property<Integer> property = Property.of(0);
        Observable<Integer> doubled = property.map(value -> value * 2);
        List<String> calls = new CopyOnWriteArrayList<>();
        doubled.subscribe(scope, value -> Notifications.whenDone(() -> calls.add("derived's action")));
        property.subscribe(scope, value -> calls.add("property's listener"));

        property.set(1);

        assertThat(calls).containsExactly("property's listener", "derived's action");
    }

    @Test
    void runsTheActionsEvenWhenAListenerThrows() {
        Property<Integer> property = Property.of(0);
        List<String> calls = new CopyOnWriteArrayList<>();
        property.subscribe(scope, value -> Notifications.whenDone(() -> calls.add("action")));
        property.subscribe(scope, value -> {
            throw new IllegalStateException("listener");
        });

        assertThatIllegalStateException().isThrownBy(() -> property.set(1));

        assertThat(calls).containsExactly("action");
        Notifications.whenDone(() -> calls.add("later"));
        assertThat(calls).containsExactly("action", "later");
    }

    @Test
    void runsEveryActionEvenWhenSomeThrowThenThrowsTheFirstFailure() {
        Property<Integer> property = Property.of(0);
        List<String> calls = new CopyOnWriteArrayList<>();
        property.subscribe(
                scope,
                value -> Notifications.whenDone(() -> {
                    throw new IllegalStateException("first");
                }));
        property.subscribe(scope, value -> Notifications.whenDone(() -> calls.add("second's action")));
        property.subscribe(
                scope,
                value -> Notifications.whenDone(() -> {
                    throw new IllegalArgumentException("third");
                }));

        assertThatIllegalStateException()
                .isThrownBy(() -> property.set(1))
                .withMessage("first")
                .satisfies(failure -> assertThat(failure.getSuppressed())
                        .singleElement()
                        .extracting(Throwable::getMessage)
                        .isEqualTo("third"));

        assertThat(calls).containsExactly("second's action");
        Notifications.whenDone(() -> calls.add("later"));
        assertThat(calls).containsExactly("second's action", "later");
    }

    @Test
    void throwsTheListenersFailureWithTheActionsFailuresSuppressed() {
        Property<Integer> property = Property.of(0);
        List<String> calls = new CopyOnWriteArrayList<>();
        property.subscribe(
                scope,
                value -> Notifications.whenDone(() -> {
                    throw new IllegalArgumentException("action");
                }));
        property.subscribe(scope, value -> Notifications.whenDone(() -> calls.add("second's action")));
        property.subscribe(scope, value -> {
            throw new IllegalStateException("listener");
        });

        assertThatIllegalStateException()
                .isThrownBy(() -> property.set(1))
                .withMessage("listener")
                .satisfies(failure -> assertThat(failure.getSuppressed())
                        .singleElement()
                        .extracting(Throwable::getMessage)
                        .isEqualTo("action"));

        assertThat(calls).containsExactly("second's action");
    }

    @Test
    void runsAtOnceTheActionsGivenByAnAction() {
        Property<Integer> property = Property.of(0);
        List<String> calls = new CopyOnWriteArrayList<>();
        property.subscribe(
                scope,
                value -> Notifications.whenDone(() -> {
                    Notifications.whenDone(() -> calls.add("nested action"));
                    calls.add("action");
                }));

        property.set(1);

        assertThat(calls).containsExactly("nested action", "action");
    }
}
