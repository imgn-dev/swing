package be.imgn.swing.internal;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The calls of the listeners of observables, on each thread, so that what a change causes can wait until every listener
 * of the change has been called.
 *
 * <p>A listener that queues work for another thread, such as an update for the EDT, would otherwise let that thread run
 * it before the next listeners of the same change have queued theirs, and in the wrong order.
 *
 * <p>The wait lasts as long as the outermost call of listeners on the thread. A listener that opens a nested event
 * loop, such as a modal dialog shown on the EDT, so holds back the work given during that change until it returns: the
 * work the listeners before it gave, and that of every change made on the EDT while the nested loop runs, such as in
 * the dialog, since those changes are made inside that call.
 */
public final class Notifications {

    // Inside the outermost call of listeners, until the first action is given: a list is made only for a change that
    // gives some.
    private static final List<Runnable> NONE = List.of();

    // The actions to run once the outermost call of listeners on the current thread ends, NONE until the first one is
    // given, or null outside of a call.
    private static final ThreadLocal<@Nullable List<Runnable>> AFTER = new ThreadLocal<>();

    private Notifications() {}

    /**
     * Runs an action at once, or, if the current thread is calling the listeners of an observable, once the outermost
     * such call ends, even if a listener threw, after the actions given before it, and even if one of them threw.
     *
     * @param action the action
     */
    public static void whenDone(Runnable action) {
        List<Runnable> after = AFTER.get();
        if (after == null) {
            action.run();
        } else if (after.isEmpty()) {
            List<Runnable> first = new ArrayList<>();
            first.add(action);
            AFTER.set(first);
        } else {
            after.add(action);
        }
    }

    // Calls the listeners, then, if this is the outermost call on the thread, the actions given meanwhile. The first
    // failure, the listeners' or an action's, is thrown once every action has run, with the later ones suppressed.
    static void notifying(Runnable calls) {
        if (AFTER.get() != null) {
            calls.run();
            return;
        }
        AFTER.set(NONE);
        @Nullable Throwable failure = Guarded.run(calls);
        // Removed before the actions run, so that an action given by one of them runs at once.
        List<Runnable> after = Objects.requireNonNull(AFTER.get());
        AFTER.remove();
        // Every action runs, even when the listeners or other actions threw: an action left out would leave behind
        // what it was to finish, such as the EDT updates a change queued.
        for (Runnable action : after) {
            @Nullable Throwable actionFailure = Guarded.run(action);
            if (actionFailure == null) {
                continue;
            }
            if (failure == null) {
                failure = actionFailure;
            } else {
                failure.addSuppressed(actionFailure);
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure != null) {
            throw new UndeclaredThrowableException(failure);
        }
    }
}
