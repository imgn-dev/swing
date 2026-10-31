package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.inject.Provider;
import be.imgn.swing.Observable;
import be.imgn.swing.Scope;
import be.imgn.swing.Task;
import be.imgn.swing.internal.NoTask;
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
    private final MenuBuilder builder;
    private final DialogFlow flow;
    private final Window window;
    private final Dialogs dialogs;
    private final Provider<StandardMenus> menus;

    // StandardMenus depends on Ui, hence the Provider.
    @Inject
    Ui(
            Window window,
            Scope scope,
            UiServices services,
            DialogFlow flow,
            Dialogs dialogs,
            Provider<StandardMenus> menus) {
        this.view = services.registry().viewOf(scope);
        if (!Objects.equals(view.window().window(), window)) {
            throw new IllegalStateException("The view " + view.type().getName() + " is not in this window");
        }
        this.services = services;
        this.builder = new MenuBuilder(view, services);
        this.flow = flow;
        this.window = window;
        this.dialogs = dialogs;
        this.menus = menus;
    }

    /**
     * Returns the window of this view.
     *
     * @return the window
     */
    public Window window() {
        return window;
    }

    /**
     * Returns the dialogs of this view's window: messages, confirmations and errors.
     *
     * @return the dialogs
     */
    public Dialogs dialogs() {
        return dialogs;
    }

    /**
     * Returns the standard menus of this view, which {@link #menuBar(MenuSpec...)} takes.
     *
     * @return the standard menus
     */
    public StandardMenus menus() {
        return menus.get();
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
        EdtDelivery.subscribe(view.scope(), source, target, EdtDelivery.Order.VIEW);
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
        return builder.menuBar(specs);
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
        return builder.popupMenu(Objects.requireNonNull(invoker, "invoker"), entries);
    }

    /**
     * Returns a new toolbar. Buttons hide their text when they have an icon.
     *
     * @param entries the entries
     * @return the toolbar
     */
    public JToolBar toolBar(ToolBarEntry... entries) {
        return builder.toolBar(entries);
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
        if (task instanceof NoTask) {
            return;
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
        return flow.show(view, Optional.of(type), Optional.empty(), host -> host.getInstance(type));
    }

    /**
     * Shows a variant of a dialog view, whose resources are its base table with the variant's table merged over it, and
     * blocks until it closes, as {@link #showDialog(Class)} does.
     *
     * @param type the dialog view's class
     * @param variant the variant, a constant of the dialog's nested {@link Variants} enum
     * @param <R> the type of the result
     * @param <E> the type of the variant
     * @return the result of {@code ok()}, or empty for {@code cancel()}
     * @throws IllegalArgumentException if the variant's enum is not nested in the dialog view's class
     */
    public <R, E extends Enum<E>> Optional<R> showDialog(Class<? extends DialogView<R>> type, E variant) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(variant, "variant");
        if (variant.getDeclaringClass().getEnclosingClass() != type) {
            throw new IllegalArgumentException(variant + " is not a variant of " + type.getName()
                    + ": its variants are the constants of its nested @Variants enum");
        }
        return flow.show(view, Optional.of(type), Optional.of(variant), host -> host.getInstance(type));
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
        return flow.show(view, Optional.empty(), Optional.empty(), host -> create.apply(host.getInstance(factory)));
    }

    BoundMenu menu(Class<?> owner, String id) {
        return builder.menu(owner, id);
    }
}
