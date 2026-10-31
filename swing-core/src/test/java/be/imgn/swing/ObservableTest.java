package be.imgn.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.swing.internal.DisposalScope;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObservableTest {

    private final DisposalScope scope = DisposalScope.root("test");

    @Test
    void notifiesOnlyWhenTheValueChanges() {
        Property<Integer> count = Property.of(0);
        List<Integer> seen = new ArrayList<>();
        count.subscribe(scope, seen::add);

        count.set(1);
        count.set(1);
        count.set(2);

        assertThat(seen).containsExactly(1, 2);
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void stopsNotifyingWhenTheScopeCloses() {
        Property<Integer> count = Property.of(0);
        List<Integer> seen = new ArrayList<>();
        count.subscribe(scope, seen::add);

        scope.close();
        count.set(1);

        assertThat(seen).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> count.subscribe(scope, seen::add));
    }

    @Test
    void closingASubscriptionIsIdempotent() {
        Property<Integer> count = Property.of(0);
        List<Integer> seen = new ArrayList<>();
        Subscription subscription = count.subscribe(scope, seen::add);

        subscription.close();
        subscription.close();
        count.set(1);

        assertThat(seen).isEmpty();
    }

    @Test
    void mapsLazily() {
        Property<Integer> count = Property.of(1);
        AtomicInteger calls = new AtomicInteger();
        Observable<Boolean> even = count.map(value -> {
            calls.incrementAndGet();
            return value % 2 == 0;
        });

        assertThat(calls).hasValue(0);
        assertThat(even.get()).isFalse();
        count.set(2);
        assertThat(even.get()).isTrue();
    }

    @Test
    void notifiesAMappedValueOnlyWhenItChanges() {
        Property<Integer> count = Property.of(1);
        Observable<Boolean> even = count.map(value -> value % 2 == 0);
        List<Boolean> seen = new ArrayList<>();
        even.subscribe(scope, seen::add);

        count.set(3);
        count.set(4);
        count.set(6);
        count.set(7);

        assertThat(seen).containsExactly(true, false);
    }

    @Test
    void combinesTwoObservables() {
        Property<String> first = Property.of("a");
        Property<String> second = Property.of("b");
        Observable<String> both = Observable.combine(first, second, (a, b) -> a + b);
        List<String> seen = new ArrayList<>();
        both.subscribe(scope, seen::add);

        first.set("c");
        second.set("d");

        assertThat(seen).containsExactly("cb", "cd");
        assertThat(both.get()).isEqualTo("cd");
    }

    @Test
    void unsubscribesFromTheSourcesWithItsLastSubscriber() {
        Property<Integer> count = Property.of(1);
        AtomicInteger calls = new AtomicInteger();
        Observable<Integer> doubled = count.map(value -> {
            calls.incrementAndGet();
            return value * 2;
        });
        Subscription subscription = doubled.subscribe(scope, ignored -> {});
        subscription.close();
        calls.set(0);

        count.set(5);

        assertThat(calls).hasValue(0);
    }
}
