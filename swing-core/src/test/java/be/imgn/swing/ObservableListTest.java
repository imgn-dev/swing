package be.imgn.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.swing.internal.DisposalScope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservableListTest {

    private final DisposalScope scope = DisposalScope.root("test");
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    @Test
    void holdsItsElements() {
        var list = ObservableList.of("a", "b");

        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(1)).isEqualTo("b");
        assertThat(list.get()).containsExactly("a", "b");
        assertThat(ObservableList.copyOf(List.of("c")).get()).containsExactly("c");
    }

    @Test
    void givesSnapshotsThatNeverChange() {
        var list = ObservableList.of("a");
        var snapshot = list.get();

        list.add("b");

        assertThat(snapshot).containsExactly("a");
        assertThat(list.get()).containsExactly("a", "b");
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> snapshot.add("c"));
    }

    @Test
    void doesNotKeepTheCollectionItWasCopiedFrom() {
        var source = new ArrayList<>(List.of("a"));
        var list = ObservableList.copyOf(source);

        source.add("b");

        assertThat(list.get()).containsExactly("a");
    }

    @Test
    void rejectsNullElements() {
        var list = ObservableList.of("a");
        // Through a list, which the compiler and NullAway don't check, to reach the observable list's own checks.
        var withNull = Arrays.asList("b", null);
        var nothing = withNull.get(1);

        assertThatNullPointerException().isThrownBy(() -> ObservableList.of("a", nothing));
        assertThatNullPointerException().isThrownBy(() -> ObservableList.copyOf(withNull));
        assertThatNullPointerException().isThrownBy(() -> list.add(nothing));
        assertThatNullPointerException().isThrownBy(() -> list.add(0, nothing));
        assertThatNullPointerException().isThrownBy(() -> list.addAll(withNull));
        assertThatNullPointerException().isThrownBy(() -> list.set(0, nothing));
        assertThatNullPointerException().isThrownBy(() -> list.remove(nothing));
        assertThatNullPointerException().isThrownBy(() -> list.setAll(withNull));
        assertThat(list.get()).containsExactly("a");
    }

    @Test
    void rejectsChangesWithNullLists() {
        // Through a list, which the compiler and NullAway don't check, to reach the changes' own checks.
        List<String> nothing = Collections.<List<String>>singletonList(null).get(0);

        assertThatNullPointerException().isThrownBy(() -> new ListChange.Added<>(0, nothing));
        assertThatNullPointerException().isThrownBy(() -> new ListChange.Removed<>(0, nothing));
        assertThatNullPointerException().isThrownBy(() -> new ListChange.Replaced<>(0, nothing, List.of("a")));
        assertThatNullPointerException().isThrownBy(() -> new ListChange.Replaced<>(0, List.of("a"), nothing));
    }

    @Test
    void rejectsIndicesOutOfRange() {
        var list = ObservableList.of("a");

        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> list.get(1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> list.get(-1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> list.add(2, "b"));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> list.add(-1, "b"));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> list.set(1, "b"));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> list.set(-1, "b"));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> list.remove(1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> list.remove(-1));
        assertThat(list.get()).containsExactly("a");
    }

    @Test
    void addsAtTheEnd() {
        var list = ObservableList.of("a");
        var changes = changesOf(list);

        list.add("b");

        assertThat(list.get()).containsExactly("a", "b");
        assertThat(changes).containsExactly(new ListChange.Added<>(1, List.of("b")));
    }

    @Test
    void insertsAtAnIndex() {
        var list = ObservableList.of("a", "c");
        var changes = changesOf(list);

        list.add(1, "b");
        list.add(3, "d");

        assertThat(list.get()).containsExactly("a", "b", "c", "d");
        assertThat(changes)
                .containsExactly(new ListChange.Added<>(1, List.of("b")), new ListChange.Added<>(3, List.of("d")));
    }

    @Test
    void addsAllInOneChange() {
        var list = ObservableList.of("a");
        var changes = changesOf(list);

        list.addAll(List.of("b", "c"));

        assertThat(list.get()).containsExactly("a", "b", "c");
        assertThat(changes).containsExactly(new ListChange.Added<>(1, List.of("b", "c")));
    }

    @Test
    void replacesAnElement() {
        var list = ObservableList.of("a", "b");
        var changes = changesOf(list);

        assertThat(list.set(1, "c")).isEqualTo("b");

        assertThat(list.get()).containsExactly("a", "c");
        assertThat(changes).containsExactly(new ListChange.Replaced<>(1, List.of("b"), List.of("c")));
    }

    @Test
    void removesAtAnIndex() {
        var list = ObservableList.of("a", "b");
        var changes = changesOf(list);

        assertThat(list.remove(0)).isEqualTo("a");

        assertThat(list.get()).containsExactly("b");
        assertThat(changes).containsExactly(new ListChange.Removed<>(0, List.of("a")));
    }

    @Test
    void removesTheFirstEqualElement() {
        var list = ObservableList.of("a", "b", "a");
        var changes = changesOf(list);

        assertThat(list.remove("a")).isTrue();

        assertThat(list.get()).containsExactly("b", "a");
        assertThat(changes).containsExactly(new ListChange.Removed<>(0, List.of("a")));
    }

    @Test
    void replacesAllTheElements() {
        var list = ObservableList.of("a", "b");
        var changes = changesOf(list);

        list.setAll(List.of("c"));

        assertThat(list.get()).containsExactly("c");
        assertThat(changes).containsExactly(new ListChange.Replaced<>(0, List.of("a", "b"), List.of("c")));
    }

    @Test
    void addsWhenItReplacesTheElementsOfAnEmptyList() {
        var list = ObservableList.<String>of();
        var changes = changesOf(list);

        list.setAll(List.of("a", "b"));

        assertThat(changes).containsExactly(new ListChange.Added<>(0, List.of("a", "b")));
    }

    @Test
    void removesWhenItReplacesTheElementsByNone() {
        var list = ObservableList.of("a", "b");
        var changes = changesOf(list);

        list.setAll(List.of());

        assertThat(list.get()).isEmpty();
        assertThat(changes).containsExactly(new ListChange.Removed<>(0, List.of("a", "b")));
    }

    @Test
    void clears() {
        var list = ObservableList.of("a", "b");
        var changes = changesOf(list);

        list.clear();

        assertThat(list.get()).isEmpty();
        assertThat(changes).containsExactly(new ListChange.Removed<>(0, List.of("a", "b")));
    }

    @Test
    void givesUnmodifiableListsOfElements() {
        var list = ObservableList.of("a", "b");
        var changes = changesOf(list);

        list.add("c");
        list.addAll(new ArrayList<>(List.of("d")));
        list.set(0, "e");
        list.remove("e");
        list.setAll(new ArrayList<>(List.of("f")));
        list.clear();

        assertThat(changes).hasSize(6).allSatisfy(change -> {
            for (var elements : elementsOf(change)) {
                assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> elements.add("x"));
            }
        });
    }

    @Test
    void makesNoChangeWhenTheListStaysEqual() {
        var list = ObservableList.of("a", "b");
        var changes = changesOf(list);
        var values = new ArrayList<List<String>>();
        list.subscribe(scope, values::add);

        assertThat(list.set(0, new String("a"))).isEqualTo("a");
        list.setAll(List.of("a", "b"));
        list.addAll(List.of());
        assertThat(list.remove("c")).isFalse();
        var empty = ObservableList.<String>of();
        var emptyChanges = changesOf(empty);
        empty.clear();
        empty.setAll(List.of());

        assertThat(changes).isEmpty();
        assertThat(values).isEmpty();
        assertThat(emptyChanges).isEmpty();
        assertThat(list.get()).containsExactly("a", "b");
    }

    @Test
    void storesTheEqualElementsItIsGiven() {
        var list = ObservableList.of(new Entry(1, "old"), new Entry(2, "old"));
        var changes = changesOf(list);
        var values = new ArrayList<List<Entry>>();
        list.subscribe(scope, values::add);
        var before = list.get();

        assertThat(list.set(0, new Entry(1, "set"))).extracting(Entry::text).isEqualTo("old");
        assertThat(list.get(0).text()).isEqualTo("set");
        assertThat(list.get()).extracting(Entry::text).containsExactly("set", "old");

        list.setAll(List.of(new Entry(1, "all"), new Entry(2, "all")));

        assertThat(list.get()).extracting(Entry::text).containsExactly("all", "all");
        assertThat(before).extracting(Entry::text).containsExactly("old", "old");
        assertThat(changes).isEmpty();
        assertThat(values).isEmpty();
    }

    @Test
    void givesTheNewSnapshotToItsSubscribers() {
        var list = ObservableList.of("a");
        var values = new ArrayList<List<String>>();
        list.subscribe(scope, values::add);

        list.add("b");
        list.remove(0);

        assertThat(values).containsExactly(List.of("a", "b"), List.of("b"));
    }

    @Test
    void callsItsListenersInTheOrderTheySubscribed() {
        var list = ObservableList.of("a");
        var calls = new ArrayList<String>();
        list.subscribeChanges(scope, change -> calls.add("first"));
        list.subscribe(scope, value -> calls.add("second"));
        list.subscribeChanges(scope, change -> calls.add("third"));
        list.subscribe(scope, value -> calls.add("fourth"));

        list.add("b");

        assertThat(calls).containsExactly("first", "second", "third", "fourth");
    }

    @Test
    void stopsCallingItsListenersWhenTheyAreUnsubscribed() {
        var list = ObservableList.of("a");
        var changes = new ArrayList<ListChange<String>>();
        var values = new ArrayList<List<String>>();
        var child = scope.child("child");
        list.subscribeChanges(child, changes::add);
        var subscription = list.subscribe(scope, values::add);

        child.close();
        subscription.close();
        list.add("b");

        assertThat(changes).isEmpty();
        assertThat(values).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> list.subscribeChanges(child, changes::add));
    }

    @Test
    void forbidsItsListenersToModifyIt() {
        var list = ObservableList.of("a");
        var later = new ArrayList<ListChange<String>>();
        list.subscribeChanges(scope, change -> list.add("c"));
        list.subscribeChanges(scope, later::add);

        assertThatIllegalStateException().isThrownBy(() -> list.add("b"));

        assertThat(list.get()).containsExactly("a", "b");
        assertThat(later).containsExactly(new ListChange.Added<>(1, List.of("b")));
    }

    @Test
    void callsEveryListenerWhenOneThrowsThenRethrowsTheFirstFailure() {
        var list = ObservableList.of("a");
        var changes = new ArrayList<ListChange<String>>();
        var snapshots = new ArrayList<List<String>>();
        var first = new IllegalArgumentException("first");
        var second = new IllegalStateException("second");
        list.subscribeChanges(scope, change -> {
            throw first;
        });
        list.subscribeChanges(scope, changes::add);
        list.subscribe(scope, value -> {
            throw second;
        });
        list.subscribe(scope, snapshots::add);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> list.add("b"))
                .isSameAs(first)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(second));

        assertThat(list.get()).containsExactly("a", "b");
        assertThat(changes).containsExactly(new ListChange.Added<>(1, List.of("b")));
        assertThat(snapshots).containsExactly(List.of("a", "b"));
    }

    @Test
    void forbidsItsListenersToModifyAnotherList() {
        var list = ObservableList.of("a");
        var other = ObservableList.of("x");
        var failure = new AtomicReference<RuntimeException>();
        list.subscribe(scope, value -> {
            try {
                other.clear();
            } catch (IllegalStateException e) {
                failure.set(e);
            }
        });

        list.add("b");

        assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
        assertThat(other.get()).containsExactly("x");
    }

    @Test
    void forbidsEveryModificationToItsListeners() {
        var list = ObservableList.of("a", "b");
        var other = ObservableList.of("x");
        var failures = new ArrayList<RuntimeException>();
        List<Runnable> modifications = List.of(
                () -> other.add("y"),
                () -> other.add(0, "y"),
                () -> other.addAll(List.of("y")),
                () -> other.set(0, "y"),
                () -> other.remove(0),
                () -> other.remove("x"),
                () -> other.setAll(List.of("y")),
                other::clear);
        list.subscribeChanges(scope, change -> {
            for (var modification : modifications) {
                try {
                    modification.run();
                } catch (IllegalStateException e) {
                    failures.add(e);
                }
            }
        });

        list.add("c");

        assertThat(failures).hasSize(modifications.size());
        assertThat(other.get()).containsExactly("x");
    }

    @Test
    void canBeModifiedAgainAfterAListenerFailed() {
        var list = ObservableList.of("a");
        var later = new ArrayList<ListChange<String>>();
        var subscription = list.subscribeChanges(scope, change -> {
            throw new IllegalArgumentException("listener");
        });
        list.subscribeChanges(scope, later::add);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> list.add("b"));
        subscription.close();

        list.add("c");

        assertThat(list.get()).containsExactly("a", "b", "c");
        assertThat(later)
                .containsExactly(new ListChange.Added<>(1, List.of("b")), new ListChange.Added<>(2, List.of("c")));
    }

    @Test
    void mapsAndCombinesLikeAnyObservable() {
        var list = ObservableList.of("a");
        var filter = Property.of("");
        Observable<Integer> size = list.map(List::size);
        Observable<Long> matching = Observable.combine(
                list,
                filter,
                (elements, text) ->
                        elements.stream().filter(e -> e.contains(text)).count());
        var sizes = new ArrayList<Integer>();
        var matches = new ArrayList<Long>();
        size.subscribe(scope, sizes::add);
        matching.subscribe(scope, matches::add);

        list.add("b");
        list.set(0, "c");
        filter.set("b");
        list.add("ab");

        assertThat(sizes).containsExactly(2, 3);
        assertThat(matches).containsExactly(2L, 1L, 2L);
        assertThat(size.get()).isEqualTo(3);
    }

    @Test
    void givesItsChangesInOrderAcrossThreads() throws Exception {
        var list = ObservableList.<Integer>of();
        var replayed = new ArrayList<Integer>();
        var invalid = Collections.synchronizedList(new ArrayList<ListChange<Integer>>());
        list.subscribeChanges(scope, change -> {
            if (!apply(change, replayed)) {
                invalid.add(change);
            }
        });
        int threads = 8;
        int perThread = 500;
        var start = new CyclicBarrier(threads);
        var futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            int thread = t;
            futures.add(executor.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    int value = thread * perThread + i;
                    switch (i % 4) {
                        case 0 -> list.add(value);
                        case 1 -> list.add(0, value);
                        case 2 -> list.set(0, value);
                        default -> list.remove(0);
                    }
                }
                return null;
            }));
        }
        for (var future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        assertThat(invalid).isEmpty();
        assertThat(replayed).isEqualTo(list.get());
        assertThat(list.size()).isEqualTo(threads * perThread / 4);
    }

    @Test
    void readsWaitForTheModificationInProgress() throws Exception {
        var sized = ObservableList.of("a");
        var copied = ObservableList.of("a");
        var indexed = ObservableList.of("a");

        int size = readWhileAListenerRuns(
                sized, blocking -> sized.subscribeChanges(scope, change -> blocking.run()), sized::size);
        List<String> snapshot = readWhileAListenerRuns(
                copied, blocking -> copied.subscribeChanges(scope, change -> blocking.run()), copied::get);
        String element = readWhileAListenerRuns(
                indexed, blocking -> indexed.subscribeChanges(scope, change -> blocking.run()), () -> indexed.get(1));

        assertThat(size).isEqualTo(2);
        assertThat(snapshot).containsExactly("a", "b");
        assertThat(element).isEqualTo("b");
    }

    @Test
    void callsItsSubscribersWhileItHoldsTheLock() throws Exception {
        var list = ObservableList.of("a");

        assertThat(readWhileAListenerRuns(list, blocking -> list.subscribe(scope, value -> blocking.run()), list::size))
                .isEqualTo(2);
    }

    @Test
    void combinesAListWithAPropertyChangedOnAnotherThreadWithoutDeadlock() throws Exception {
        var list = ObservableList.<Integer>of();
        var property = Property.of(0);
        Observable<Integer> sum = Observable.combine(list, property, (elements, value) -> elements.size() + value);
        sum.subscribe(scope, ignored -> {});
        int changes = 2000;
        var start = new CyclicBarrier(2);
        var adding = executor.submit(() -> {
            start.await();
            for (int i = 0; i < changes; i++) {
                list.add(i);
            }
            return null;
        });
        var setting = executor.submit(() -> {
            start.await();
            for (int i = 1; i <= changes; i++) {
                property.set(i);
            }
            return null;
        });

        adding.get(30, TimeUnit.SECONDS);
        setting.get(30, TimeUnit.SECONDS);

        assertThat(sum.get()).isEqualTo(2 * changes);
    }

    @Test
    void combinesTwoListsModifiedOnTwoThreadsWithoutDeadlock() throws Exception {
        var first = ObservableList.<Integer>of();
        var second = ObservableList.<Integer>of();
        Observable<Integer> sizes = Observable.combine(first, second, (a, b) -> a.size() + b.size());
        sizes.subscribe(scope, ignored -> {});
        int changes = 2000;
        var start = new CyclicBarrier(2);
        var futures = new ArrayList<Future<?>>();
        for (var list : List.of(first, second)) {
            futures.add(executor.submit(() -> {
                start.await();
                for (int i = 0; i < changes; i++) {
                    list.add(i);
                }
                return null;
            }));
        }
        for (var future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        assertThat(sizes.get()).isEqualTo(2 * changes);
    }

    // Applies a change to a copy, and returns whether its indices were valid for the copy.
    private static <E> boolean apply(ListChange<E> change, List<E> copy) {
        switch (change) {
            case ListChange.Added<E>(int index, List<E> elements) -> {
                if (index > copy.size()) {
                    return false;
                }
                copy.addAll(index, elements);
            }
            case ListChange.Removed<E>(int index, List<E> elements) -> {
                if (index + elements.size() > copy.size()
                        || !copy.subList(index, index + elements.size()).equals(elements)) {
                    return false;
                }
                copy.subList(index, index + elements.size()).clear();
            }
            case ListChange.Replaced<E>(int index, List<E> removed, List<E> added) -> {
                if (index + removed.size() > copy.size()
                        || !copy.subList(index, index + removed.size()).equals(removed)) {
                    return false;
                }
                copy.subList(index, index + removed.size()).clear();
                copy.addAll(index, added);
            }
        }
        return true;
    }

    private <E> List<ListChange<E>> changesOf(ObservableList<E> list) {
        var changes = new ArrayList<ListChange<E>>();
        list.subscribeChanges(scope, changes::add);
        return changes;
    }

    private static List<List<String>> elementsOf(ListChange<String> change) {
        return switch (change) {
            case ListChange.Added<String> added -> List.of(added.elements());
            case ListChange.Removed<String> removed -> List.of(removed.elements());
            case ListChange.Replaced<String> replaced -> List.of(replaced.removed(), replaced.added());
        };
    }

    // Adds an element on one thread, and reads the list on another while a listener of that addition runs.
    private <T> T readWhileAListenerRuns(
            ObservableList<String> list, Consumer<Runnable> subscribeBlocking, Callable<T> read) throws Exception {
        var delivering = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        subscribeBlocking.accept(() -> {
            delivering.countDown();
            await(release);
        });
        var modification = executor.submit(() -> list.add("b"));
        delivering.await();
        var readerThread = new AtomicReference<Thread>();
        var reading = executor.submit(() -> {
            readerThread.set(Thread.currentThread());
            return read.call();
        });

        awaitWaiting(readerThread);
        assertThat(reading).isNotDone();
        release.countDown();

        T value = reading.get(10, TimeUnit.SECONDS);
        modification.get(10, TimeUnit.SECONDS);
        return value;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitWaiting(AtomicReference<Thread> thread) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var current = thread.get();
            if (current != null && current.getState() == Thread.State.WAITING) {
                return;
            }
            Thread.sleep(1);
        }
        throw new AssertionError("The reader never waited for the lock.");
    }

    // Equal by id, as entities often are, whatever its text.
    private record Entry(int id, String text) {
        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Entry entry && entry.id == id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }
}
