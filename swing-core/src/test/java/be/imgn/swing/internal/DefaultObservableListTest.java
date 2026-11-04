package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.swing.ListChange;
import be.imgn.swing.ObservableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultObservableListTest {

    private final DisposalScope scope = DisposalScope.root("test");
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    @Test
    void isTheListOfTheFactories() {
        assertThat(ObservableList.of("a")).isInstanceOf(DefaultObservableList.class);
        assertThat(ObservableList.copyOf(List.of("a"))).isInstanceOf(DefaultObservableList.class);
    }

    @Test
    void isDeliveringWhileAListCallsItsListenersOnTheCurrentThread() {
        var list = ObservableList.of("a");
        var mapped = list.map(List::size);
        var seen = new ArrayList<Boolean>();
        list.subscribeChanges(scope, change -> seen.add(DefaultObservableList.isDelivering()));
        mapped.subscribe(scope, size -> seen.add(DefaultObservableList.isDelivering()));

        list.add("b");

        assertThat(seen).containsExactly(true, true);
        assertThat(DefaultObservableList.isDelivering()).isFalse();
    }

    @Test
    void runsAnActionAfterDeliveryOnTheModifyingThreadOnceTheListsLockIsReleased() {
        var list = ObservableList.of("a");
        var other = ObservableList.<String>of();
        var seen = new ArrayList<String>();
        list.subscribeChanges(
                scope,
                change -> DefaultObservableList.afterDelivery(() -> {
                    seen.add("action on " + Thread.currentThread().getName());
                    // Another thread can read the list, and the action can modify one.
                    seen.add("read " + readOnAnotherThread(list));
                    other.add("x");
                }));
        seen.add("listener subscribed");

        list.add("b");
        seen.add("returned");

        assertThat(seen)
                .containsExactly(
                        "listener subscribed",
                        "action on " + Thread.currentThread().getName(),
                        "read 2",
                        "returned");
        assertThat(other.get()).containsExactly("x");
    }

    @Test
    void runsAnActionAfterDeliveryOnceTheOutermostModificationReleasesTheLock() {
        var list = new DefaultObservableList<>(List.of("a"));
        var reads = new ArrayList<Integer>();
        list.subscribeChanges(
                scope, change -> DefaultObservableList.afterDelivery(() -> reads.add(readOnAnotherThread(list))));

        // setIf sets through set, both holding the lock.
        assertThat(list.setIf(0, "a", "b")).isTrue();

        assertThat(reads).containsExactly(1);
    }

    @Test
    void runsAnActionAfterDeliveryAtOnceWhenNoListIsDelivering() {
        var ran = new AtomicBoolean();

        DefaultObservableList.afterDelivery(() -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    void runsTheActionsAfterDeliveryInOrderEvenIfOneThrowsThenRethrowsTheFirst() {
        var list = ObservableList.of("a");
        var first = new IllegalStateException("first");
        var second = new IllegalArgumentException("second");
        var ran = new ArrayList<String>();
        list.subscribeChanges(scope, change -> {
            DefaultObservableList.afterDelivery(() -> {
                ran.add("1");
                throw first;
            });
            DefaultObservableList.afterDelivery(() -> ran.add("2"));
        });
        list.subscribeChanges(
                scope,
                change -> DefaultObservableList.afterDelivery(() -> {
                    ran.add("3");
                    throw second;
                }));

        assertThatIllegalStateException().isThrownBy(() -> list.add("b")).isSameAs(first);

        assertThat(ran).containsExactly("1", "2", "3");
        assertThat(first.getSuppressed()).containsExactly(second);
        assertThat(list.get()).containsExactly("a", "b");
    }

    @Test
    void runsTheActionsAfterDeliveryOfAModificationMadeByAnActionWithThatModification() {
        var list = ObservableList.of("a");
        var other = ObservableList.<String>of();
        var ran = new ArrayList<String>();
        other.subscribeChanges(scope, change -> DefaultObservableList.afterDelivery(() -> ran.add("other")));
        list.subscribeChanges(scope, change -> {
            DefaultObservableList.afterDelivery(() -> {
                ran.add("first");
                other.add("x");
            });
            DefaultObservableList.afterDelivery(() -> ran.add("second"));
        });

        list.add("b");

        assertThat(ran).containsExactly("first", "other", "second");
    }

    @Test
    void copiesAndSubscribesTogether() {
        var list = new DefaultObservableList<>(List.of("a"));
        var changes = new ArrayList<ListChange<String>>();

        var copy = list.copyAndSubscribeChanges(scope, changes::add);
        list.add("b");

        assertThat(copy.elements()).containsExactly("a");
        assertThat(changes).containsExactly(new ListChange.Added<>(1, List.of("b")));

        copy.subscription().close();
        list.add("c");

        assertThat(changes).hasSize(1);
    }

    @Test
    void neitherMissesNorRepeatsAChangeMadeOnAnotherThread() throws Exception {
        for (int round = 0; round < 50; round++) {
            var list = new DefaultObservableList<Integer>(List.of());
            var stop = new AtomicBoolean();
            var started = new CountDownLatch(1);
            var modifying = executor.submit(() -> {
                int value = 0;
                while (!stop.get()) {
                    list.add(value++);
                    if (value % 3 == 0) {
                        list.remove(0);
                    }
                    started.countDown();
                }
            });
            started.await();
            // Queued, then applied to the copy, as the UI layer does on the EDT.
            var queued = Collections.synchronizedList(new ArrayList<ListChange<Integer>>());

            var copy = list.copyAndSubscribeChanges(scope, queued::add);
            Thread.sleep(5);
            stop.set(true);
            modifying.get(10, TimeUnit.SECONDS);
            copy.subscription().close();

            var replayed = new ArrayList<>(copy.elements());
            for (var change : queued) {
                apply(change, replayed);
            }
            assertThat(replayed).isEqualTo(list.get());
        }
    }

    @Test
    void setsOnlyWhenTheListHoldsTheExpectedElementAtTheIndex() {
        var list = new DefaultObservableList<>(List.of("a", "b"));
        var changes = new ArrayList<ListChange<String>>();
        list.subscribeChanges(scope, changes::add);

        assertThat(list.setIf(1, "b", "B")).isTrue();
        assertThat(list.setIf(0, "b", "x")).isFalse();
        assertThat(list.setIf(2, "b", "x")).isFalse();

        assertThat(list.get()).containsExactly("a", "B");
        assertThat(changes).containsExactly(new ListChange.Replaced<>(1, List.of("b"), List.of("B")));
    }

    @Test
    void setsAnEqualElementWithoutAChange() {
        var list = new DefaultObservableList<>(List.of("a"));
        var changes = new ArrayList<ListChange<String>>();
        list.subscribeChanges(scope, changes::add);

        assertThat(list.setIf(0, "a", "a")).isTrue();

        assertThat(changes).isEmpty();
    }

    @Test
    void setIfRejectsANullElementAndAListenersModification() {
        var list = new DefaultObservableList<>(List.of("a"));
        // Through a list, which NullAway doesn't check, to reach the list's own check.
        var nulls = new ArrayList<String>();
        nulls.add(null);

        assertThatNullPointerException().isThrownBy(() -> list.setIf(1, "b", nulls.getFirst()));

        list.subscribeChanges(scope, change -> list.setIf(0, "b", "c"));

        assertThatIllegalStateException().isThrownBy(() -> list.add("b"));
    }

    @Test
    void cannotSubscribeInAClosedScope() {
        var list = new DefaultObservableList<>(List.of("a"));
        scope.close();

        assertThatIllegalStateException().isThrownBy(() -> list.copyAndSubscribeChanges(scope, change -> {}));
    }

    // The size another thread reads, which waits for the list's lock: it times out while the current thread holds it.
    private int readOnAnotherThread(ObservableList<String> list) {
        try {
            return executor.submit(list::size).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError("Another thread couldn't read the list", e);
        }
    }

    private static <E> void apply(ListChange<E> change, List<E> copy) {
        switch (change) {
            case ListChange.Added<E>(int index, List<E> elements) -> copy.addAll(index, elements);
            case ListChange.Removed<E>(int index, List<E> elements) ->
                copy.subList(index, index + elements.size()).clear();
            case ListChange.Replaced<E>(int index, List<E> removed, List<E> added) -> {
                copy.subList(index, index + removed.size()).clear();
                copy.addAll(index, added);
            }
        }
    }
}
