package be.imgn.swing.testing;

import be.imgn.gaia.core.testing.FakePlatform;
import be.imgn.gaia.core.testing.FakeTicker;
import be.imgn.gaia.core.testing.OperatingSystem;
import be.imgn.gaia.core.time.Ticker;
import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.inject.Injector;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Exit;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.internal.DefaultLifecycle;
import be.imgn.swing.internal.TaskRunner;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.Windows;
import be.imgn.swing.ui.internal.Edt;
import be.imgn.swing.ui.internal.Launch;
import be.imgn.swing.ui.internal.LaunchOptions;
import be.imgn.swing.ui.internal.UiMode;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JRootPane;
import javax.swing.RepaintManager;
import org.jspecify.annotations.Nullable;

/**
 * Runs an application in a test: the real lifecycle, headless, with fakes for the platform, the directories, the clock
 * and the dialogs.
 *
 * <pre>{@code
 * try (AppTester<CounterApp> app = AppTester.start(CounterApp.class, Locale.FRENCH)) {
 *     app.commands(CounterAppCommands.class).increment().execute();
 *
 *     assertThat(app.component(JLabel.class).getText()).isEqualTo("Le compte est : 1.");
 * }
 * }</pre>
 *
 * <p>Frames and dialogs are never realized: each window is a bare root pane, where the content, menu bar and toolbar
 * are built and laid out. Every call on the tester, and on the commands it returns, runs on the EDT and waits; before
 * returning, the tester runs the updates posted by observables and waits for the tasks commands start, so assertions
 * see the components as the user will. A component repainted or invalidated off the EDT, a failure no handler handles,
 * and duplicate mnemonics or shortcuts, in a dialog or in a menu the framework rebuilt after a dynamic section changed,
 * fail the test at the tester's next call.
 *
 * <p>The platform is gaia's {@link FakePlatform} for Linux, with its directories on a {@code gaia-memfs} file system,
 * so window state is saved in memory. Time is a {@link FakeTicker} and an {@link InstantSource} fixed at the epoch.
 *
 * @param <T> the application class
 */
public final class AppTester<T> implements AutoCloseable {

    private final Class<T> type;
    private final Failures failures = new Failures();
    private final TestExecutor executor = new TestExecutor();
    private final FakeDialogs dialogs = new FakeDialogs();
    private final FakeTicker ticker = new FakeTicker();
    private final FileSystem fileSystem = MemoryFileSystems.newFileSystem();
    private final HeadlessWindows windows = new HeadlessWindows(this::showDialog);
    private final AtomicReference<@Nullable Injector> root = new AtomicReference<>();
    private volatile boolean running = true;
    private volatile int exitStatus = -1;
    private @Nullable RepaintManager previousRepaintManager;
    private Thread.@Nullable UncaughtExceptionHandler previousHandler;

    private AppTester(Class<T> type) {
        this.type = type;
    }

    /**
     * Starts an application with {@code Locale.ROOT}, which reads the base files of resources.
     *
     * @param type the application class
     * @param <T> the application class
     * @return the tester, to close
     * @throws AssertionError if the startup fails
     */
    public static <T> AppTester<T> start(Class<T> type) {
        return start(type, Locale.ROOT);
    }

    /**
     * Starts an application in a locale, bound as its {@code DisplayLocale}.
     *
     * @param type the application class
     * @param locale the display locale
     * @param <T> the application class
     * @return the tester, to close
     * @throws AssertionError if the startup fails
     */
    public static <T> AppTester<T> start(Class<T> type, Locale locale) {
        AppTester<T> tester = new AppTester<>(Objects.requireNonNull(type, "type"));
        tester.launch(Objects.requireNonNull(locale, "locale"));
        return tester;
    }

    private void launch(Locale locale) {
        Edt.invokeAndWait(() -> {
            previousRepaintManager = RepaintManager.currentManager(null);
            RepaintManager.setCurrentManager(new EdtChecker(failures));
        });
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> failures.add(failure));
        FakePlatform platform = FakePlatform.newBuilder(OperatingSystem.LINUX, fileSystem.getPath("/"))
                .build();
        AtomicReference<@Nullable Throwable> startupFailure = new AtomicReference<>();
        LaunchOptions options = new LaunchOptions(
                platform,
                locale,
                builder -> builder.bind(UiExecutor.class, executor)
                        .bind(FailureHandler.class, failures::add)
                        .bind(Exit.class, (Exit) this::exited)
                        .bind(Windows.class, windows)
                        .bind(Dialogs.class, dialogs)
                        .bind(UiMode.class, new UiMode(true, failures::add))
                        .bind(Ticker.class, ticker)
                        .bind(InstantSource.class, InstantSource.fixed(Instant.EPOCH)),
                injector -> {
                    root.set(injector);
                    executor.attach(injector.getInstance(TaskRunner.class));
                },
                false,
                (failure, injector, lookAndFeel) -> startupFailure.set(failure));
        Optional<Injector> started = Launch.start(type, List.of(), options);
        Throwable failure = startupFailure.get();
        if (started.isEmpty() || failure != null) {
            restore();
            throw new AssertionError("The application " + type.getName() + " failed to start", failure);
        }
        executor.settle();
        failures.throwIfAny();
    }

    private void exited(int status) {
        exitStatus = status;
        running = false;
    }

    private Injector root() {
        Injector injector = root.get();
        if (injector == null) {
            throw new IllegalStateException("The application is not started");
        }
        return injector;
    }

    private ViewHost mainView() {
        WindowHost main = root().getInstance(WindowRegistry.class)
                .main()
                .orElseThrow(() -> new IllegalStateException("The main window is not open"));
        return main.views().getFirst();
    }

    private <R> R call(Supplier<R> code) {
        failures.throwIfAny();
        R result = Edt.call(executor, code);
        executor.settle();
        failures.throwIfAny();
        return result;
    }

    /**
     * Returns a bean of the main view's scope, such as its generated {@code <Class>Commands}.
     *
     * @param type the bean's class
     * @param <C> the bean's class
     * @return the bean
     */
    public <C> C commands(Class<C> type) {
        return call(() -> mainView().injector().getInstance(type));
    }

    /**
     * Returns the application class's instance, the main window's view.
     *
     * @return the instance
     */
    public T application() {
        return call(() -> type.cast(mainView().view().orElseThrow()));
    }

    /**
     * Returns the one component of a type in the main window.
     *
     * @param type the type
     * @param <C> the type
     * @return the component
     * @throws IllegalStateException if there is none or more than one
     */
    public <C> C component(Class<C> type) {
        return call(() -> Components.single(mainRootPane(), type, "the main window"));
    }

    /**
     * Returns the one component of a type in the main window whose accessible name is {@code name}: the text of the
     * label whose {@code labelFor} it is, or the {@code accessible-name} of its resources.
     *
     * @param type the type
     * @param name the accessible name
     * @param <C> the type
     * @return the component
     * @throws IllegalStateException if there is none or more than one
     */
    public <C> C component(Class<C> type, String name) {
        return call(() -> Components.named(mainRootPane(), type, name, "the main window"));
    }

    /**
     * Returns the components of a type in the main window, in depth-first order.
     *
     * @param type the type
     * @param <C> the type
     * @return the components
     */
    public <C> List<C> components(Class<C> type) {
        return call(() -> Components.find(mainRootPane(), type));
    }

    /**
     * Returns the title of the main window.
     *
     * @return the title
     */
    public String title() {
        return call(() -> ((HeadlessWindows.HeadlessWindow) mainView().window().window()).title());
    }

    private JRootPane mainRootPane() {
        return mainView().window().window().rootPane();
    }

    /**
     * Returns the dialogs, which record what was shown and answer it.
     *
     * @return the dialogs
     */
    public FakeDialogs dialogs() {
        return dialogs;
    }

    /**
     * Returns the ticker the application reads time from.
     *
     * @return the ticker, which tests advance
     */
    public FakeTicker ticker() {
        return ticker;
    }

    /**
     * Returns the application's information: its platform and directories are the fakes.
     *
     * @return the information
     */
    public ApplicationInfo info() {
        return call(() -> root().getInstance(ApplicationInfo.class));
    }

    /**
     * Returns whether the application runs: {@code false} once it exited.
     *
     * @return {@code true} until it exits
     */
    public boolean isRunning() {
        return call(() -> running);
    }

    /**
     * Returns the status the application exited with.
     *
     * @return the status
     * @throws IllegalStateException if the application runs
     */
    public int exitStatus() {
        return call(() -> {
            if (running) {
                throw new IllegalStateException("The application is running");
            }
            return exitStatus;
        });
    }

    private void showDialog(HeadlessWindows.HeadlessWindow window) {
        Injector injector = root.get();
        if (injector == null) {
            return;
        }
        injector.getInstance(WindowRegistry.class).windowOf(window.rootPane()).ifPresent(host -> {
            ViewHost view = host.views().getFirst();
            Object instance = view.view().orElseThrow();
            try {
                MnemonicChecker.check(
                        window.rootPane(), "the dialog " + instance.getClass().getName());
            } catch (AssertionError e) {
                failures.add(e);
            }
            dialogs.answer(new OpenDialog<>(instance, view.injector(), window.rootPane()));
        });
    }

    /**
     * Closes the application if it runs, without asking its exit vetoes nor running its shutdown steps: its windows,
     * then, through the lifecycle, its root injector and its application scope, which cancels the tasks and releases
     * the subscriptions registered there. Then closes the fakes.
     */
    @Override
    public void close() {
        try {
            Injector injector = root.get();
            if (running && injector != null) {
                executor.settle();
                Edt.invokeAndWait(() -> {
                    for (WindowHost window :
                            injector.getInstance(WindowRegistry.class).windows().reversed()) {
                        windows.dispose(window.window());
                        window.injector().close();
                        window.scope().close();
                    }
                    injector.getInstance(DefaultLifecycle.class).close();
                });
                running = false;
            }
        } finally {
            restore();
        }
        failures.throwIfAny();
    }

    private void restore() {
        RepaintManager previous = previousRepaintManager;
        Edt.invokeAndWait(() -> RepaintManager.setCurrentManager(previous));
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        try {
            fileSystem.close();
        } catch (IOException | UnsupportedOperationException e) {
            failures.add(e);
        }
    }
}
