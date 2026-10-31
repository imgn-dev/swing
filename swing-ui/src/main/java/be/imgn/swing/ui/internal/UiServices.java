package be.imgn.swing.ui.internal;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.internal.DefaultLifecycle;
import be.imgn.swing.internal.TaskRunner;
import be.imgn.swing.ui.Windows;
import java.util.Optional;

/** The application's services that the beans of the windows and views share. */
@Singleton
public final class UiServices {

    private final Resources resources;
    private final ApplicationInfo application;
    private final Platform platform;
    private final UiExecutor executor;
    private final TaskRunner tasks;
    private final DefaultLifecycle lifecycle;
    private final WindowRegistry registry;
    private final IconCache icons;
    private final UiMode mode;
    private final InputBlocker blocker;
    private final FocusTracker focus;
    private final Windows windows;
    private final ViewSupports supports;
    private final UiTexts texts;

    @Inject
    @SuppressWarnings("checkstyle:ParameterNumber")
    UiServices(
            Resources resources,
            ApplicationInfo application,
            Platform platform,
            UiExecutor executor,
            TaskRunner tasks,
            DefaultLifecycle lifecycle,
            WindowRegistry registry,
            IconCache icons,
            UiMode mode,
            InputBlocker blocker,
            FocusTracker focus,
            Windows windows,
            ViewSupports supports,
            UiTexts texts) {
        this.resources = resources;
        this.application = application;
        this.platform = platform;
        this.executor = executor;
        this.tasks = tasks;
        this.lifecycle = lifecycle;
        this.registry = registry;
        this.icons = icons;
        this.mode = mode;
        this.blocker = blocker;
        this.focus = focus;
        this.windows = windows;
        this.supports = supports;
        this.texts = texts;
    }

    /**
     * Returns the resources.
     *
     * @return the resources
     */
    public Resources resources() {
        return resources;
    }

    /**
     * Returns the application's information.
     *
     * @return the information
     */
    public ApplicationInfo application() {
        return application;
    }

    /**
     * Returns the application class: the class of the main window's view, in milestone 1.
     *
     * @return the class, empty before the main view is built
     */
    public Optional<Class<?>> applicationType() {
        return registry.main()
                .flatMap(main -> main.views().stream().findFirst())
                .map(ViewHost::type);
    }

    /**
     * Returns whether the application runs on macOS.
     *
     * @return {@code true} on macOS
     */
    public boolean isMacOS() {
        return platform.isMacOS();
    }

    /**
     * Returns the UI executor.
     *
     * @return the executor
     */
    public UiExecutor executor() {
        return executor;
    }

    /**
     * Returns the task runner.
     *
     * @return the runner
     */
    public TaskRunner tasks() {
        return tasks;
    }

    /**
     * Returns the lifecycle.
     *
     * @return the lifecycle
     */
    public DefaultLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * Returns the registry of windows and views.
     *
     * @return the registry
     */
    public WindowRegistry registry() {
        return registry;
    }

    /**
     * Returns the icon cache.
     *
     * @return the cache
     */
    public IconCache icons() {
        return icons;
    }

    /**
     * Returns how strictly mistakes are reported.
     *
     * @return the mode
     */
    public UiMode mode() {
        return mode;
    }

    /**
     * Returns the input blocker.
     *
     * @return the blocker
     */
    public InputBlocker blocker() {
        return blocker;
    }

    /**
     * Returns the focus tracker.
     *
     * @return the tracker
     */
    public FocusTracker focus() {
        return focus;
    }

    /**
     * Returns the windows.
     *
     * @return the windows
     */
    public Windows windows() {
        return windows;
    }

    /**
     * Returns the generated support of the view classes.
     *
     * @return the support
     */
    public ViewSupports supports() {
        return supports;
    }

    /**
     * Returns the framework's texts.
     *
     * @return the texts
     */
    public UiTexts texts() {
        return texts;
    }
}
