package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.Task;
import be.imgn.swing.TaskWork;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.ViewScoped;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.random.RandomGenerator;

/**
 * Long tasks that show each kind of input blocking. Guts' dialog blocker, a modal dialog over the application, is the
 * {@link Block#APPLICATION} glass pane here, with its progress indicator and Cancel button. Guts' task groups are
 * several tasks, each blocking the application; its serial executor is a permit that the serial tasks take in turn,
 * since tasks run on virtual threads.
 */
@ViewScoped
public class TaskTestActions {

    private static final System.Logger LOGGER = System.getLogger(TaskTestActions.class.getName());
    private static final Duration CHUNK = Duration.ofMillis(500);
    private static final int MIN_CHUNKS = 10;
    private static final int MORE_CHUNKS = 10;
    private static final int GROUP_SIZE = 5;

    private final Ui ui;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final Semaphore serial = new Semaphore(1);
    private final List<String> log = new CopyOnWriteArrayList<>();
    private Duration chunk = CHUNK;

    @Inject
    TaskTestActions(Ui ui) {
        this.ui = ui;
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

    @Command
    Task<String> oneTaskSerialExecutor() {
        return task("oneTaskSerialExecutor", context -> {
            serial.acquire();
            try {
                return sleep("oneTaskSerialExecutor", chunks());
            } finally {
                serial.release();
            }
        });
    }

    @Command
    void fiveTasksDialogBlocker() {
        for (int index = 0; index < GROUP_SIZE; index++) {
            ui.run(longTask("fiveTasksDialogBlocker #" + index), Block.APPLICATION);
        }
    }

    @Command
    void twoSerialTaskDialogBlocker() {
        Task<String> second = longProgressTask("twoSerialTaskDialogBlocker #2");
        ui.run(
                longProgressTask("twoSerialTaskDialogBlocker #1").onSuccess(name -> ui.run(second, Block.APPLICATION)),
                Block.APPLICATION);
    }

    @Command
    void twoSerialGroupsDialogBlocker() {
        Task<String> second = longTask("twoSerialGroupsDialogBlocker #2");
        ui.run(
                longTask("twoSerialGroupsDialogBlocker #1").onSuccess(name -> ui.run(second, Block.APPLICATION)),
                Block.APPLICATION);
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
                context.message("Sleep chunk #" + (index + 1) + "...");
                Thread.sleep(chunk);
                context.progress((index + 1) / (double) chunks);
            }
            context.message("Task done!");
            record(name, "ending execute()");
            return name;
        });
    }

    private Task<String> task(String name, TaskWork<String> work) {
        return Task.of(context -> {
                    record(name, "just started");
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
}
