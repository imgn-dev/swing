package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Scope;
import be.imgn.swing.Task;
import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.ui.internal.EdtDelivery;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;

/**
 * What a view needs to build its components. Every method is called on the EDT, and every subscription it makes belongs
 * to its view's scope.
 */
@ViewScoped
public final class Ui {

    private final ViewHost view;
    private final UiServices services;
    private final MenuBuilder menus;
    private final DialogFlow dialogs;

    @Inject
    Ui(Window window, Scope scope, UiServices services, DialogFlow dialogs) {
        this.view = services.registry().viewOf(scope);
        if (!Objects.equals(view.window().window(), window)) {
            throw new IllegalStateException("The view " + view.type().getName() + " is not in this window");
        }
        this.services = services;
        this.menus = new MenuBuilder(view, services);
        this.dialogs = dialogs;
    }

    /**
     * Calls a target with the current value of an observable, then on every change, on the EDT, until the view's scope
     * closes. Several changes before an update runs cause one call, with the final value.
     *
     * @param target the target, such as {@code label::setText}
     * @param source the observable
     * @param <T> the type of the value
     */
    public <T> void bind(Consumer<? super T> target, Observable<T> source) {
        Objects.requireNonNull(target, "target");
        target.accept(source.get());
        EdtDelivery.subscribe(view.scope(), source, target);
    }

    /**
     * Returns a new menu bar built from descriptions. On macOS, it drops {@code exit()}, {@code about()} and
     * {@code preferences()}, which the application menu shows; then it drops the leading, trailing and doubled
     * separators, and checks the mnemonics of each menu and of the bar's menus.
     *
     * @param specs the menus, in order
     * @return the menu bar
     */
    public JMenuBar menuBar(MenuSpec... specs) {
        return menus.menuBar(specs);
    }

    /**
     * Returns a new popup menu built from entries, set as the invoker's component popup menu: Swing shows it on the
     * invoker's popup trigger.
     *
     * @param invoker the component the popup menu is attached to
     * @param entries the entries
     * @return the popup menu
     */
    public JPopupMenu popupMenu(JComponent invoker, MenuEntry... entries) {
        return menus.popupMenu(Objects.requireNonNull(invoker, "invoker"), entries);
    }

    /**
     * Returns a new toolbar. Buttons hide their text when they have an icon.
     *
     * @param entries the entries
     * @return the toolbar
     */
    public JToolBar toolBar(ToolBarEntry... entries) {
        return menus.toolBar(entries);
    }

    /**
     * Runs a task in this view's scope, with input blocking. Outside a command, only {@link Block#NONE},
     * {@link Block#WINDOW} and {@link Block#APPLICATION} apply.
     *
     * @param task the task
     * @param block what to block while it runs
     * @throws IllegalArgumentException for {@link Block#COMMAND} or {@link Block#COMPONENT}
     */
    public void run(Task<?> task, Block block) {
        Objects.requireNonNull(task, "task");
        if (block == Block.COMMAND || block == Block.COMPONENT) {
            throw new IllegalArgumentException(block + " applies only to a task a command starts");
        }
        RunningTask running = services.tasks().start(task, view.scope(), "task");
        Runnable unblock = TaskBlocking.block(services, view, running, block, null, null);
        running.whenEnded(() -> services.executor().execute(unblock));
    }

    /**
     * Shows a dialog view, owned by this view's window, modal to it and centered on it, and blocks until it closes.
     *
     * @param type the dialog view class
     * @param <R> the type of the result
     * @return the result of {@code ok()}, or empty for {@code cancel()}
     */
    public <R> Optional<R> showDialog(Class<? extends DialogView<R>> type) {
        Objects.requireNonNull(type, "type");
        return dialogs.show(view, Optional.of(type), host -> host.getInstance(type));
    }

    /**
     * Shows a dialog view created by an assisted factory, resolved through the dialog view's child injector, and blocks
     * until it closes.
     *
     * @param factory the assisted factory
     * @param create creates the dialog view with the factory
     * @param <F> the type of the factory
     * @param <R> the type of the result
     * @return the result of {@code ok()}, or empty for {@code cancel()}
     */
    public <F, R> Optional<R> showDialog(Class<F> factory, Function<? super F, ? extends DialogView<R>> create) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(create, "create");
        return dialogs.show(view, Optional.empty(), host -> create.apply(host.getInstance(factory)));
    }

    BoundMenu menu(Class<?> owner, String id) {
        return menus.menu(owner, id);
    }
}
