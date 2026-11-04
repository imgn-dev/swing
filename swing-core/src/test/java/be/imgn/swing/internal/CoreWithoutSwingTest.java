package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.gaia.core.event.OnVirtualThread;
import be.imgn.gaia.core.event.Subscribe;
import be.imgn.gaia.core.event.SubscribingAnnotation;
import be.imgn.gaia.core.event.Subscription;
import be.imgn.inject.Eager;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.EventExecutor;
import be.imgn.swing.Exit;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.ShutdownStep;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// The counterpart of Guts' event bus demo: core's beans and the application scope, as the launch binds them, and
// eager singletons that only subscribe, which nothing injects.
class CoreWithoutSwingTest {

    private static final long TIMEOUT_SECONDS = 10;

    private final DisposalScope application = DisposalScope.root("application");
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final List<Integer> exits = new CopyOnWriteArrayList<>();
    private final Injector root = TestInjectors.root(
            application,
            builder -> builder.bind(FailureHandler.class, failures::add).bind(Exit.class, exits::add));
    private final EventBus bus = root.getInstance(EventBus.class);
    private final Journal journal = root.getInstance(Journal.class);
    private final TaskRunner runner = root.getInstance(TaskRunner.class);

    @AfterEach
    void close() {
        if (!application.isClosed()) {
            root.getInstance(DefaultLifecycle.class).close();
        }
    }

    @Test
    void deliversToTheSubscribersOfTheEventsTypeBeforeThoseOfItsSupertypes() {
        bus.publish(new ContactSelected("Ada"));

        assertThat(journal.entries())
                .containsExactly(
                        "list selected Ada", "list selected again Ada", "list event ContactSelected[name=Ada]");
    }

    @Test
    void deliversEveryVariantOfASealedInterface() {
        bus.publish(new ContactRenamed("Bob"));

        assertThat(journal.entries()).contains("list event ContactRenamed[name=Bob]");
    }

    @Test
    void deliversAnEventPublishedFromASubscriberOnceTheEventBeingDeliveredReachedAllItsSubscribers() {
        bus.publish(new ContactOpened("Ada"));

        assertThat(journal.entries())
                .containsExactly(
                        "opener Ada",
                        "opener published",
                        "list selected Ada",
                        "list selected again Ada",
                        "list event ContactSelected[name=Ada]");
    }

    @Test
    void runsASubscribeMethodOnThePublishingThread() throws InterruptedException {
        Thread publisher = Thread.ofPlatform().name("publisher").start(() -> bus.publish(new Ping()));
        publisher.join();

        assertThat(journal.entries()).containsExactly("ping on publisher");
    }

    @Test
    void deliversToAnOnVirtualThreadMethod() throws InterruptedException {
        bus.publish(new ContactRenamed("Bob"));

        assertThat(journal.background().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo("indexed Bob on a virtual thread");
    }

    @Test
    void sendsTheFailureOfASubscriberToTheFailureHandlerAndGoesOn() {
        bus.publish(new Broken());

        assertThat(failures).singleElement().isInstanceOf(IllegalStateException.class);
        assertThat(journal.entries()).containsExactly("broken thrown", "broken survived");
    }

    @Test
    void doesntCatchAnErrorThrownByASubscribeMethod() {
        assertThatThrownBy(() -> bus.publish(new Doomed()))
                .isInstanceOf(DeliveryError.class)
                .hasMessage("thrown by a @Subscribe method");

        assertThat(journal.entries()).containsExactly("doomed thrown");
        assertThat(failures).isEmpty();
    }

    @Test
    void runsTheDeliveriesOfASubscribingAnnotationWithItsContributedExecutor() {
        bus.publish(new ContactRenamed("Ada"));
        bus.publish(new ContactRenamed("Bob"));
        assertThat(journal.entries()).doesNotContain("archived Ada");

        queued().runAll();

        assertThat(journal.entries()).containsSubsequence("archived Ada", "archived Bob");
    }

    @Test
    void closingTheApplicationScopeUnsubscribes() throws InterruptedException {
        bus.publish(new ContactRenamed("Ada"));
        assertThat(journal.background().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo("indexed Ada on a virtual thread");
        QueuedExecutor queued = queued();

        root.getInstance(DefaultLifecycle.class).close();
        bus.publish(new ContactSelected("Bob"));
        bus.publish(new ContactRenamed("Bob"));
        // The delivery of Ada was handed to the executor before the scope closed, but runs after.
        queued.runAll();

        assertThat(application.isClosed()).isTrue();
        assertThat(journal.entries()).containsExactly("list event ContactRenamed[name=Ada]");
        assertThat(journal.background().poll(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void runsEachOnVirtualThreadDeliveryAsATaskTaggedAsADelivery() throws InterruptedException {
        Held held = new Held(new CountDownLatch(1), new CountDownLatch(1));

        bus.publish(held);

        assertThat(held.started().await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(runner.running()).singleElement().satisfies(task -> {
            assertThat(task.isDelivery()).isTrue();
            assertThat(task.isCommand()).isFalse();
            assertThat(task.name()).isEqualTo("task");
        });
        held.release().countDown();
        awaitDeliveries();
        assertThat(runner.running()).isEmpty();
        assertThat(journal.entries()).containsExactly("held released");
    }

    @Test
    void theShutdownCancelsTheRunningDeliveriesAndWaitsForThem() throws InterruptedException {
        Held held = new Held(new CountDownLatch(1), new CountDownLatch(1));
        bus.publish(held);
        assertThat(held.started().await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        root.getInstance(DefaultLifecycle.class).requestExit();

        assertThat(journal.entries()).containsExactly("held interrupted");
        assertThat(exits).containsExactly(0);
        assertThat(application.isClosed()).isTrue();
    }

    @Test
    void dropsTheDeliveriesOfAnEventPublishedByAShutdownStep() throws InterruptedException {
        root.getInstance(DefaultLifecycle.class).requestExit();

        assertThat(exits).containsExactly(0);
        assertThat(journal.background().poll(100, TimeUnit.MILLISECONDS)).isNull();
        assertThat(failures).isEmpty();
    }

    @Test
    void dropsTheDeliveriesOfAnEventPublishedOnceTheApplicationScopeIsClosing() {
        // Registered after the subscriptions, so closed before them: the bus still hands the delivery to the executor.
        application.onClose(() -> bus.publish(new ContactRenamed("Late")));
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Logger logger = Logger.getLogger(VirtualThreadDeliveries.class.getName());
        Level previous = logger.getLevel();
        logger.setLevel(Level.FINE);
        logger.addHandler(handler);
        try {
            root.getInstance(DefaultLifecycle.class).close();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }

        assertThat(journal.entries()).containsExactly("list event ContactRenamed[name=Late]");
        assertThat(journal.background()).isEmpty();
        assertThat(runner.running()).isEmpty();
        assertThat(failures).isEmpty();
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.FINE);
            assertThat(record.getMessage()).contains("dropped");
        });
    }

    @Test
    void sendsAnErrorThrownByAnOnVirtualThreadMethodToTheFailureHandler() throws InterruptedException {
        bus.publish(new Fatal());
        awaitDeliveries();

        assertThat(failures).singleElement().isInstanceOf(DeliveryError.class);
    }

    @Test
    void sendsARuntimeExceptionThrownByAnOnVirtualThreadMethodToTheFailureHandler() throws InterruptedException {
        bus.publish(new Faulty());
        awaitDeliveries();

        assertThat(failures)
                .singleElement()
                .satisfies(failure -> assertThat(failure)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("thrown by an @OnVirtualThread method"));
        assertThat(journal.entries()).containsExactly("faulty thrown");
    }

    @Test
    void aDeliveryWhoseSubscriptionClosedBeforeItStartedDoesNotCallTheMethod() throws InterruptedException {
        List<Runnable> handed = new ArrayList<>();
        EventBus holding = EventBus.newBuilder()
                .executor(OnVirtualThread.class, handed::add)
                .build();
        Subscription closed = holding.register(new ContactIndexer(journal));
        holding.register(new ContactIndexer(journal));
        holding.publish(new ContactRenamed("Ada"));
        closed.close();

        handed.forEach(onVirtualThread().executor()::execute);
        awaitDeliveries();

        assertThat(handed).hasSize(2);
        assertThat(journal.background()).containsExactly("indexed Ada on a virtual thread");
    }

    @Test
    void runsTheDeliveriesToOneMethodInParallel() throws InterruptedException {
        Met met = new Met(new CountDownLatch(2));

        bus.publish(met);
        bus.publish(met);

        assertThat(journal.background().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("met true");
        assertThat(journal.background().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("met true");
    }

    @Test
    void sendsTheFailureOfAnExecutorToTheFailureHandlerAndGoesOn() {
        bus.publish(new Refused());

        assertThat(failures).singleElement().isInstanceOf(RejectedExecutionException.class);
        assertThat(journal.entries()).containsExactly("refused survived");
    }

    @Test
    void registersAnInstanceOfASubclassThatDeclaresNoSubscriberThroughItsSuperclass() {
        bus.publish(new Greeting());

        assertThat(journal.entries()).containsExactly("greeted by PoliteGreeter");
    }

    @Test
    void runsWithoutJavaDesktop() {
        assertThat(ModuleLayer.boot().findModule("java.desktop")).isEmpty();
    }

    private EventExecutor onVirtualThread() {
        return Contributions.of(root, EventExecutor.class).stream()
                .filter(executor -> executor.annotation() == OnVirtualThread.class)
                .findFirst()
                .orElseThrow();
    }

    private void awaitDeliveries() throws InterruptedException {
        for (RunningTask task : runner.running()) {
            assertThat(task.await(Duration.ofSeconds(TIMEOUT_SECONDS))).isTrue();
        }
    }

    private QueuedExecutor queued() {
        return Contributions.of(root, EventExecutor.class).stream()
                .filter(QueuedExecutor.class::isInstance)
                .map(QueuedExecutor.class::cast)
                .findFirst()
                .orElseThrow();
    }

    sealed interface ContactEvent permits ContactSelected, ContactRenamed {}

    record ContactSelected(String name) implements ContactEvent {}

    record ContactRenamed(String name) implements ContactEvent {}

    record ContactOpened(String name) {}

    record Ping() {}

    record Broken() {}

    record Held(CountDownLatch started, CountDownLatch release) {}

    record Met(CountDownLatch both) {}

    record Fatal() {}

    record Doomed() {}

    record Faulty() {}

    record Refused() {}

    record Greeting() {}

    record Farewell() {}

    /** An error thrown by a subscriber. */
    static final class DeliveryError extends Error {
        private static final long serialVersionUID = 1L;

        DeliveryError() {
            this("thrown by an @OnVirtualThread method");
        }

        DeliveryError(String message) {
            super(message);
        }
    }

    /** What the subscribers received: on the publishing thread, and on other threads. */
    @Singleton
    static final class Journal {
        private final List<String> entries = new CopyOnWriteArrayList<>();
        private final BlockingQueue<String> background = new LinkedBlockingQueue<>();

        @Inject
        Journal() {}

        List<String> entries() {
            return List.copyOf(entries);
        }

        BlockingQueue<String> background() {
            return background;
        }

        void add(String entry) {
            entries.add(entry);
        }
    }

    @Singleton
    @Eager
    static final class ContactList {
        private final Journal journal;

        @Inject
        ContactList(Journal journal) {
            this.journal = journal;
        }

        @Subscribe
        void onSelected(ContactSelected event) {
            journal.add("list selected " + event.name());
        }

        @Subscribe
        void onSelectedAgain(ContactSelected event) {
            journal.add("list selected again " + event.name());
        }

        @Subscribe
        void onEvent(ContactEvent event) {
            journal.add("list event " + event);
        }
    }

    @Singleton
    @Eager
    static final class ContactOpener {
        private final Journal journal;
        private final EventBus bus;

        @Inject
        ContactOpener(Journal journal, EventBus bus) {
            this.journal = journal;
            this.bus = bus;
        }

        @Subscribe
        void onOpened(ContactOpened event) {
            journal.add("opener " + event.name());
            bus.publish(new ContactSelected(event.name()));
            journal.add("opener published");
        }
    }

    @Singleton
    @Eager
    static final class ThreadProbe {
        private final Journal journal;

        @Inject
        ThreadProbe(Journal journal) {
            this.journal = journal;
        }

        @Subscribe
        void onPing(Ping event) {
            journal.add("ping on " + Thread.currentThread().getName());
        }
    }

    @Singleton
    @Eager
    static final class ContactIndexer {
        private final Journal journal;

        @Inject
        ContactIndexer(Journal journal) {
            this.journal = journal;
        }

        @OnVirtualThread
        void onRenamed(ContactRenamed event) {
            journal.background()
                    .add("indexed " + event.name()
                            + (Thread.currentThread().isVirtual() ? " on a virtual thread" : ""));
        }
    }

    @Singleton
    @Eager
    static final class SlowSubscriber {
        private final Journal journal;

        @Inject
        SlowSubscriber(Journal journal) {
            this.journal = journal;
        }

        @OnVirtualThread
        void onHeld(Held event) {
            event.started().countDown();
            try {
                journal.add(
                        event.release().await(TIMEOUT_SECONDS, TimeUnit.SECONDS) ? "held released" : "held expired");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                journal.add("held interrupted");
            }
        }

        @OnVirtualThread
        void onMet(Met event) {
            event.both().countDown();
            try {
                journal.background().add("met " + event.both().await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                journal.background().add("met interrupted");
            }
        }

        @OnVirtualThread
        void onFatal(Fatal event) {
            journal.add("fatal thrown");
            throw new DeliveryError();
        }

        @OnVirtualThread
        void onFaulty(Faulty event) {
            journal.add("faulty thrown");
            throw new IllegalStateException("thrown by an @OnVirtualThread method");
        }
    }

    @Singleton
    @Eager
    static final class BrokenSubscriber {
        private final Journal journal;

        @Inject
        BrokenSubscriber(Journal journal) {
            this.journal = journal;
        }

        @Subscribe
        void onBroken(Broken event) {
            journal.add("broken thrown");
            throw new IllegalStateException("broken");
        }

        @Subscribe
        void onBrokenToo(Broken event) {
            journal.add("broken survived");
        }

        @Subscribe
        void onDoomed(Doomed event) {
            journal.add("doomed thrown");
            throw new DeliveryError("thrown by a @Subscribe method");
        }
    }

    /** A subscribing annotation whose deliveries the test runs itself. */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @SubscribingAnnotation(executor = UnusedExecutor.class)
    @interface Queued {}

    /** The executor {@link Queued} declares, which the application's bus replaces. */
    public static final class UnusedExecutor implements Executor {
        @Override
        public void execute(Runnable task) {
            throw new AssertionError("The application's bus runs the deliveries with the contributed executor");
        }
    }

    /** A shutdown step that publishes an event, after the shutdown has cancelled and awaited the running tasks. */
    @Singleton
    @IntoSet(ShutdownStep.class)
    static final class FarewellStep implements ShutdownStep {
        private final EventBus bus;

        @Inject
        FarewellStep(EventBus bus) {
            this.bus = bus;
        }

        @Override
        public int order() {
            return 0;
        }

        @Override
        public void run() {
            bus.publish(new Farewell());
        }
    }

    @Singleton
    @Eager
    static final class FarewellListener {
        private final Journal journal;

        @Inject
        FarewellListener(Journal journal) {
            this.journal = journal;
        }

        @OnVirtualThread
        void onFarewell(Farewell event) {
            journal.background().add("farewell");
        }
    }

    @Singleton
    @IntoSet(EventExecutor.class)
    static final class QueuedExecutor implements EventExecutor {
        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Inject
        QueuedExecutor() {}

        @Override
        public Class<? extends Annotation> annotation() {
            return Queued.class;
        }

        @Override
        public Executor executor() {
            return tasks::add;
        }

        void runAll() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    @Singleton
    @Eager
    static final class Archiver {
        private final Journal journal;

        @Inject
        Archiver(Journal journal) {
            this.journal = journal;
        }

        @Queued
        void onRenamed(ContactRenamed event) {
            journal.add("archived " + event.name());
        }
    }

    /** A subscribing annotation whose contributed executor rejects every delivery. */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @SubscribingAnnotation(executor = UnusedExecutor.class)
    @interface Rejected {}

    @Singleton
    @IntoSet(EventExecutor.class)
    static final class RejectingExecutor implements EventExecutor {
        @Inject
        RejectingExecutor() {}

        @Override
        public Class<? extends Annotation> annotation() {
            return Rejected.class;
        }

        @Override
        public Executor executor() {
            return task -> {
                throw new RejectedExecutionException("rejected");
            };
        }
    }

    @Singleton
    @Eager
    static final class RejectedSubscriber {
        private final Journal journal;

        @Inject
        RejectedSubscriber(Journal journal) {
            this.journal = journal;
        }

        @Rejected
        void onRejected(Refused event) {
            journal.add("refused delivered");
        }

        @Subscribe
        void onRefused(Refused event) {
            journal.add("refused survived");
        }
    }

    /** A subscriber class whose subclass, which declares no subscriber, gaia indexes none of. */
    static class Greeter {
        private final Journal journal;

        Greeter(Journal journal) {
            this.journal = journal;
        }

        @Subscribe
        void onGreeting(Greeting event) {
            journal.add("greeted by " + getClass().getSimpleName());
        }
    }

    @Singleton
    @Eager
    static final class PoliteGreeter extends Greeter {
        @Inject
        PoliteGreeter(Journal journal) {
            super(journal);
        }
    }
}
