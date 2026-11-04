package be.imgn.swing.testing;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.gaia.core.testing.FakePlatform;
import be.imgn.gaia.core.testing.FakeTicker;
import be.imgn.gaia.core.testing.OperatingSystem;
import be.imgn.gaia.core.time.Ticker;
import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.inject.Injector;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Exit;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.Scope;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.internal.DefaultLifecycle;
import be.imgn.swing.internal.DefaultSettingsStore;
import be.imgn.swing.internal.TaskRunner;
import be.imgn.swing.spi.SettingsDescriptor;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.Windows;
import be.imgn.swing.ui.internal.Edt;
import be.imgn.swing.ui.internal.Launch;
import be.imgn.swing.ui.internal.LaunchOptions;
import be.imgn.swing.ui.internal.PeriodicActions;
import be.imgn.swing.ui.internal.UiMode;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import java.awt.Component;
import java.awt.Image;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
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
 * returning, the tester runs the updates posted by observables and the periodic actions whose period has elapsed on the
 * {@link FakeTicker}, once each, and waits for the tasks commands start and for the {@code @OnVirtualThread} deliveries
 * of the application's bus, but not for the other tasks an application starts, so assertions see the components as the
 * user will. A component repainted or invalidated off the EDT, a failure no handler handles, and duplicate mnemonics or
 * shortcuts, in a dialog or in a menu the framework rebuilt after a dynamic section changed, fail the test at the
 * tester's next call.
 *
 * <p>The platform is gaia's {@link FakePlatform} for Linux, with its directories on a {@code gaia-memfs} file system,
 * so window state and settings are saved in memory. Every call also waits for the pending settings saves, and
 * {@link #close()} waits up to 2 seconds for them. Time is a {@link FakeTicker} and an {@link InstantSource} fixed at
 * the epoch.
 *
 * <p>The tester keeps the {@code ValidationFeedback} of the modules on its path, so tests check a form's errors as the
 * user sees them: through the tool tip text and accessible description of the component.
 *
 * @param <T> the application class
 */
public final class AppTester<T> implements AutoCloseable {

    private static final Duration SAVE_TIMEOUT = Duration.ofSeconds(2);

    private final Class<T> type;
    private final Failures failures = new Failures();
    private final TestExecutor executor = new TestExecutor();
    private final FakeDialogs dialogs = new FakeDialogs();
    private final FakeTicker ticker = new FakeTicker();
    private final FileSystem fileSystem;
    private final boolean ownsFileSystem;
    private final HeadlessWindows windows = new HeadlessWindows(this::showDialog);
    private volatile @Nullable DefaultSettingsStore settings;
    private final AtomicReference<@Nullable Injector> root = new AtomicReference<>();
    private final List<Object> published = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;
    private volatile int exitStatus = -1;
    private @Nullable RepaintManager previousRepaintManager;
    private Thread.@Nullable UncaughtExceptionHandler previousHandler;

    private AppTester(Class<T> type, @Nullable FileSystem fileSystem) {
        this.type = type;
        this.fileSystem = fileSystem == null ? MemoryFileSystems.newFileSystem() : fileSystem;
        this.ownsFileSystem = fileSystem == null;
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
        return builder(type).start();
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
        return builder(type).locale(locale).start();
    }

    /**
     * Starts the configuration of an application, which {@link Builder#start()} starts: in {@code Locale.ROOT} and with
     * no arguments unless the builder says otherwise.
     *
     * @param type the application class
     * @param <T> the application class
     * @return the builder
     */
    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<>(Objects.requireNonNull(type, "type"));
    }

    private void launch(Locale locale, List<String> arguments, Consumer<ApplicationDirectories> files) {
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
                locale,
                builder -> builder.bind(Platform.class, platform)
                        .bind(UiExecutor.class, executor)
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
                    DefaultSettingsStore store = injector.getInstance(DefaultSettingsStore.class);
                    settings = store;
                    executor.attachSettings(store);
                    executor.poll(injector.getInstance(PeriodicActions.class));
                    injector.getInstance(Scope.class)
                            .onClose(injector.getInstance(EventBus.class).subscribe(Object.class, published::add));
                    files.accept(injector.getInstance(ApplicationDirectories.class));
                },
                false,
                (failure, injector, lookAndFeel) -> startupFailure.set(failure));
        Optional<Injector> started = Launch.start(type, arguments, options);
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

    /**
     * Runs an action on the EDT and waits, then settles as every other call does. Tests change components this way,
     * since the EDT checker fails a test that changes them on its own thread.
     *
     * @param action the action
     * @throws NullPointerException if {@code action} is {@code null}
     */
    public void run(Runnable action) {
        Objects.requireNonNull(action, "action");
        call(() -> {
            action.run();
            return true;
        });
    }

    /**
     * Runs an action on the EDT and waits, then settles as every other call does, and returns the action's result.
     *
     * @param action the action, which returns a non-null result
     * @param <R> the type of the result
     * @return the result
     * @throws NullPointerException if {@code action} is {@code null}, or returns {@code null}
     */
    public <R> R call(Supplier<R> action) {
        Objects.requireNonNull(action, "action");
        failures.throwIfAny();
        R result = Edt.call(executor, action);
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
     * Resolves a bean through the main view's injector, so that beans of every scope of the main window are reachable,
     * such as the {@code SettingsStore}, or the {@code ApplicationDirectories} whose {@code config()} is the fake
     * config directory, on the memory file system.
     *
     * @param type the bean's class
     * @param <B> the bean's class
     * @return the bean
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <B> B bean(Class<B> type) {
        Objects.requireNonNull(type, "type");
        return call(() -> mainView().injector().getInstance(type));
    }

    /**
     * Activates a command on the EDT, as a click would, and runs the posted updates, but doesn't wait for the task it
     * starts, so that a test can act while the task runs, such as activating it again or checking a stop command. Tests
     * hold such tasks with latches: the tester's other calls wait for them. It waits for the {@code @OnVirtualThread}
     * deliveries and the pending settings saves, as every other call does.
     *
     * @param command the command
     */
    public void activate(BoundCommand command) {
        Objects.requireNonNull(command, "command");
        failures.throwIfAny();
        Edt.invokeAndWait(command::execute);
        executor.settleExceptCommands();
        failures.throwIfAny();
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

    /**
     * Returns the icon image set on the main window.
     *
     * @return the image, empty if none was set
     */
    public Optional<Image> iconImage() {
        return call(() -> mainWindow().iconImage());
    }

    /**
     * Returns the last component focused through the main window's {@code Window.focus}.
     *
     * @return the component, empty if none was
     */
    public Optional<Component> focused() {
        return call(() -> mainWindow().focused());
    }

    private HeadlessWindows.HeadlessWindow mainWindow() {
        return (HeadlessWindows.HeadlessWindow) mainView().window().window();
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
     * Publishes an event on the application's bus, on the EDT, then settles: its {@code @Subscribe}, {@code @OnEdt} and
     * {@code @OnVirtualThread} methods have run when it returns.
     *
     * @param event the event
     * @throws NullPointerException if {@code event} is {@code null}
     */
    public void publish(Object event) {
        Objects.requireNonNull(event, "event");
        call(() -> {
            root().getInstance(EventBus.class).publish(event);
            return event;
        });
    }

    /**
     * Returns the events published on the application's bus since startup that are instances of a type, in the order
     * they were delivered, whoever published them.
     *
     * @param type the type
     * @param <E> the type
     * @return the events
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <E> List<E> published(Class<E> type) {
        Objects.requireNonNull(type, "type");
        return call(() ->
                published.stream().filter(type::isInstance).map(type::cast).toList());
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
            dialogs.answer(new OpenDialog<>(instance, view.injector(), window.rootPane(), window, executor));
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
                awaitSaves();
            }
        } finally {
            restore();
        }
        failures.throwIfAny();
    }

    // The saves the @PreDestroy methods started, before the file system they write to closes.
    private void awaitSaves() {
        DefaultSettingsStore store = settings;
        if (store == null) {
            return;
        }
        try {
            store.awaitSaves(SAVE_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void restore() {
        RepaintManager previous = previousRepaintManager;
        Edt.invokeAndWait(() -> RepaintManager.setCurrentManager(previous));
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        if (!ownsFileSystem) {
            return;
        }
        try {
            fileSystem.close();
        } catch (IOException | UnsupportedOperationException e) {
            failures.add(e);
        }
    }

    /**
     * The configuration of an application to start.
     *
     * @param <T> the application class
     */
    public static final class Builder<T> {

        private final Class<T> type;
        private Locale locale = Locale.ROOT;
        private List<String> arguments = List.of();
        private final Map<Class<?>, SettingsFile> settings = new LinkedHashMap<>();
        private final List<Consumer<ApplicationDirectories>> files = new ArrayList<>();
        private @Nullable FileSystem fileSystem;

        private Builder(Class<T> type) {
            this.type = type;
        }

        /**
         * Writes a settings record to the fake config directory before startup, with the settings store's own writer,
         * so that the application starts on it. A later value of the same record replaces an earlier one.
         *
         * @param value the record, annotated {@code @Settings}
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         * @throws IllegalArgumentException if the record's class has no settings descriptor
         */
        public Builder<T> settings(Record value) {
            Objects.requireNonNull(value, "value");
            String id = DefaultSettingsStore.descriptors().stream()
                    .filter(descriptor -> descriptor.type() == value.getClass())
                    .map(SettingsDescriptor::id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No settings descriptor for "
                            + value.getClass().getName() + ": annotate it with @Settings"));
            settings.put(value.getClass(), new SettingsFile(id, value));
            return this;
        }

        /**
         * Writes a file before startup, at a path in the application's fake directories, creating its directories, so
         * that the application starts on a settings file, a data file or a window state of the test's choice.
         *
         * <pre>{@code
         * AppTester.builder(NotesApp.class)
         *         .file(directories -> directories.data().resolve("notes.txt"), "Hello")
         *         .start()
         * }</pre>
         *
         * @param path gives the file's path from the application's directories
         * @param content the file's content, written in UTF-8
         * @return this builder
         * @throws NullPointerException if {@code path} or {@code content} is {@code null}
         */
        public Builder<T> file(Function<ApplicationDirectories, Path> path, String content) {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(content, "content");
            files.add(directories -> {
                Path file = path.apply(directories);
                try {
                    Path parent = file.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(file, content);
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not write " + file, e);
                }
            });
            return this;
        }

        /**
         * Uses a memory file system rather than a new one, and doesn't close it, so that a test starts the application
         * a second time on what the first run saved.
         *
         * @param fileSystem the file system, which the test closes
         * @return this builder
         * @throws NullPointerException if {@code fileSystem} is {@code null}
         */
        public Builder<T> fileSystem(FileSystem fileSystem) {
            this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
            return this;
        }

        private void writeFiles(ApplicationDirectories directories) {
            for (SettingsFile file : settings.values()) {
                try {
                    DefaultSettingsStore.write(directories.config(), file.id(), file.value());
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not write the settings " + file.value(), e);
                }
            }
            files.forEach(file -> file.accept(directories));
        }

        /**
         * Sets the locale, bound as the application's {@code DisplayLocale}.
         *
         * @param locale the display locale; {@code Locale.ROOT}, which reads the base files of resources, by default
         * @return this builder
         */
        public Builder<T> locale(Locale locale) {
            this.locale = Objects.requireNonNull(locale, "locale");
            return this;
        }

        /**
         * Sets the arguments given to the launch, bound as the application's {@code Arguments}, as
         * {@code Swing.launch(type, args)} binds them.
         *
         * @param arguments the arguments, in order; none by default
         * @return this builder
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder<T> arguments(String... arguments) {
            this.arguments = List.of(arguments);
            return this;
        }

        /**
         * Starts the application.
         *
         * @return the tester, to close
         * @throws AssertionError if the startup fails
         */
        public AppTester<T> start() {
            AppTester<T> tester = new AppTester<>(type, fileSystem);
            tester.launch(locale, arguments, this::writeFiles);
            return tester;
        }

        private record SettingsFile(String id, Record value) {}
    }
}
