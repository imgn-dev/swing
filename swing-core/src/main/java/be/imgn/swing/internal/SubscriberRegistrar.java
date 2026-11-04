package be.imgn.swing.internal;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.gaia.core.event.Subscription;
import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.Scope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Registers on the application's bus every instance the construction hook reports, and closes its subscriptions with
 * the scope the instance is cached in: the application scope for the root injector, the host's scope otherwise.
 *
 * <p>The listener doesn't depend on the bus: the hook runs inside the reported instance's resolution, so resolving the
 * bus from it while the bus or one of its dependencies is built would be circular. The bus's provider hands it the bus
 * instead, once built; until then, the reported instances wait in a queue, in the order they were reported. The
 * instances reported on the thread of an active hold, during a view's build, wait for the hold's release. An instance
 * with subscribers built by an injector that hosts no window or view fails as soon as it is reported, once the bus is
 * given.
 *
 * <p>It is contributed as a listener by {@link EventBusBeans}, since a contribution can't also be injected by its
 * class.
 */
@Singleton
public final class SubscriberRegistrar implements ConstructionListener {

    private final Injector root;
    private final Scope application;
    private final Hosts hosts;
    private final List<Registration> queued = new ArrayList<>();
    private final Deque<Hold> holds = new ArrayDeque<>();
    private @Nullable EventBus bus;

    @Inject
    SubscriberRegistrar(Injector root, Scope application, Hosts hosts) {
        this.root = root;
        this.application = application;
        this.hosts = hosts;
    }

    @Override
    public synchronized void constructed(Object instance, Injector injector) {
        @Nullable
        Scope scope =
                injector.equals(root) ? application : hosts.scopeOf(injector).orElse(null);
        Registration registration = new Registration(instance, scope);
        @Nullable Hold hold = holdOf(Thread.currentThread());
        // An instance without a scope isn't held, so that its failure is thrown from its own resolution, not from the
        // end of a view's build, where it would hide the build's own exception.
        if (hold != null && scope != null) {
            hold.registrations.add(registration);
        } else {
            submit(registration);
        }
    }

    /**
     * Gives the listener the application's bus: registers the instances reported until now, in the order they were
     * reported, and from then on each instance at once.
     *
     * @param built the bus
     */
    public synchronized void attach(EventBus built) {
        bus = built;
        List<Registration> pending = List.copyOf(queued);
        queued.clear();
        pending.forEach(registration -> register(built, registration));
    }

    /**
     * Holds the registration of the instances reported from now on on the calling thread, until the returned release
     * runs, which registers them in the order they were reported. The framework holds them while it builds a view, on
     * the thread that builds it, so that no event reaches a view still being built; an instance built meanwhile on
     * another thread doesn't belong to that build, and is registered at once.
     *
     * @return the release, to run once
     */
    public synchronized Runnable hold() {
        Hold hold = new Hold(Thread.currentThread());
        holds.addLast(hold);
        return () -> release(hold);
    }

    // The innermost hold of a thread, the build that runs on it.
    private @Nullable Hold holdOf(Thread thread) {
        Iterator<Hold> latestFirst = holds.descendingIterator();
        while (latestFirst.hasNext()) {
            Hold hold = latestFirst.next();
            if (hold.owner == thread) {
                return hold;
            }
        }
        return null;
    }

    private synchronized void release(Hold hold) {
        holds.remove(hold);
        hold.registrations.forEach(this::submit);
    }

    private void submit(Registration registration) {
        EventBus current = bus;
        if (current == null) {
            queued.add(registration);
        } else {
            register(current, registration);
        }
    }

    private static void register(EventBus bus, Registration registration) {
        Subscription subscription;
        try {
            subscription = bus.register(registration.instance());
        } catch (IllegalArgumentException e) {
            // gaia's bus rejects an instance whose class, and the classes it extends, have no subscribers.
            return;
        }
        Scope scope = registration.scope();
        if (scope == null) {
            subscription.close();
            // A subscriber in no scope would never be unsubscribed: fail rather than leak it.
            throw new IllegalStateException("The subscriber " + registration.instance() + " of class "
                    + registration.instance().getClass().getName()
                    + " was built by an injector that hosts no window or view");
        }
        scope.onClose(subscription);
    }

    private record Registration(Object instance, @Nullable Scope scope) {}

    // Compared by identity, so that releasing a hold removes it and no other.
    private static final class Hold {
        private final Thread owner;
        private final List<Registration> registrations = new ArrayList<>();

        Hold(Thread owner) {
            this.owner = owner;
        }
    }
}
