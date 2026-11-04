package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.Task;
import be.imgn.swing.TaskWork;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Concurrency;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.ViewScoped;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;

/**
 * Long tasks that show each kind of input blocking. Guts' dialog blocker, a modal dialog over the application, is the
 * {@link Block#APPLICATION} glass pane here, with its progress indicator and Cancel button. Guts' task groups are
 * several tasks, each blocking the application, and its serial groups are chains, which block it once for both tasks;
 * its serial executor is a {@link Concurrency#SERIAL} command, whose activations run one after the other.
 */
@ViewScoped
public class TaskTestActions {

    private static final System.Logger LOGGER = System.getLogger(TaskTestActions.class.getName());
    private static final Duration CHUNK = Duration.ofMillis(500);
    private static final int MIN_CHUNKS = 10;
    private static final int MORE_CHUNKS = 10;
    private static final int GROUP_SIZE = 5;

    private final Ui ui;
    private final AddressBookMessages messages;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private Duration chunk = CHUNK;
    private volatile Hold hold = name -> {};

    @Inject
    TaskTestActions(Ui ui, AddressBookMessages messages) {
        this.ui = ui;
        this.messages = messages;
    }

    @Command
    Task<String> oneTaskNoBlocker() {
        return longTask("oneTaskNoBlocker");
    }

    @Command(block = Block.COMMAND)
    Task<String> oneTaskActionBlocker() {
        return longTask("oneTaskActionBlocker");
    }

    @Command(block = Block.COMPONENT)
    Task<String> oneTaskComponentBlocker() {
        return longTask("oneTaskComponentBlocker");
    }

    @Command(block = Block.WINDOW)
    Task<String> oneTaskWindowBlocker() {
        return longTask("oneTaskWindowBlocker");
    }

    @Command(block = Block.APPLICATION)
    Task<String> oneTaskDialogBlocker() {
        return longTask("oneTaskDialogBlocker");
    }

    @Command(block = Block.APPLICATION)
    Task<String> oneTaskProgressDialogBlocker() {
        return longProgressTask("oneTaskProgressDialogBlocker");
    }

    @Command(concurrency = Concurrency.SERIAL)
    Task<String> oneTaskSerialExecutor() {
        return longTask("oneTaskSerialExecutor");
    }

    @Command
    void fiveTasksDialogBlocker() {
        for (int index = 0; index < GROUP_SIZE; index++) {
            ui.run(longTask("fiveTasksDialogBlocker #" + index), Block.APPLICATION);
        }
    }

    @Command(block = Block.APPLICATION)
    Task<String> twoSerialTaskDialogBlocker() {
        return longProgressTask("twoSerialTaskDialogBlocker #1")
                .then(name -> longProgressTask("twoSerialTaskDialogBlocker #2"));
    }

    @Command(block = Block.APPLICATION)
    Task<String> twoSerialGroupsDialogBlocker() {
        return longTask("twoSerialGroupsDialogBlocker #1").then(name -> longTask("twoSerialGroupsDialogBlocker #2"));
    }

    /**
     * Sets the duration of a chunk of the tasks' sleep, half a second by default, for tests.
     *
     * @param duration the duration
     */
    void chunk(Duration duration) {
        this.chunk = duration;
    }

    /**
     * Makes each task, once started, wait for a hold, so that tests act while it runs.
     *
     * @param hold the hold
     */
    void hold(Hold hold) {
        this.hold = hold;
    }

    /**
     * Returns what the tasks logged, in order.
     *
     * @return the log
     */
    List<String> log() {
        return List.copyOf(log);
    }

    private Task<String> longTask(String name) {
        return task(name, context -> sleep(name, chunks()));
    }

    private Task<String> longProgressTask(String name) {
        return task(name, context -> {
            int chunks = chunks();
            record(name, "starting execute() for " + chunk.multipliedBy(chunks).toMillis() + " ms");
            for (int index = 0; index < chunks; index++) {
                context.message(messages.sleepChunk(index + 1));
                Thread.sleep(chunk);
                context.progress((index + 1) / (double) chunks);
            }
            context.message(messages.taskDone());
            record(name, "ending execute()");
            return name;
        });
    }

    private Task<String> task(String name, TaskWork<String> work) {
        return Task.of(context -> {
                    record(name, "just started");
                    hold.await(name);
                    return work.run(context);
                })
                .onSuccess(result -> record(name, "finished"))
                .onCancel(() -> record(name, "cancelled"));
    }

    private String sleep(String name, int chunks) throws InterruptedException {
        Duration duration = chunk.multipliedBy(chunks);
        record(name, "starting execute() for " + duration.toMillis() + " ms");
        Thread.sleep(duration);
        record(name, "ending execute()");
        return name;
    }

    private int chunks() {
        return MIN_CHUNKS + random.nextInt(MORE_CHUNKS);
    }

    private void record(String name, String event) {
        String line = "Task `" + name + "` " + event;
        log.add(line);
        LOGGER.log(System.Logger.Level.INFO, line);
    }

    /** What a task waits for once started, for tests. */
    @FunctionalInterface
    interface Hold {

        /**
         * Waits until the task may go on.
         *
         * @param name the task's name
         * @throws InterruptedException if the task is cancelled while it waits
         */
        void await(String name) throws InterruptedException;
    }
}
