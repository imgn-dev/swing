package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.inject.Provider;
import be.imgn.swing.Observable;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import be.imgn.swing.Task;
import be.imgn.swing.internal.NoTask;
import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.ui.internal.EdtDelivery;
import be.imgn.swing.ui.internal.PeriodicActions;
import be.imgn.swing.ui.internal.RefreshPhase;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

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
    private final PeriodicActions periodic;

    // StandardMenus depends on Ui, hence the Provider.
    @Inject
    Ui(
            Window window,
            Scope scope,
            UiServices services,
            DialogFlow flow,
            Dialogs dialogs,
            Provider<StandardMenus> menus,
            PeriodicActions periodic) {
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
        this.periodic = periodic;
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
     * Shows an observable list in a list: sets a new model on the list, which follows the observable list change by
     * change on the EDT, and a {@link javax.swing.DefaultListCellRenderer} showing the text of each element. The
     * subscription belongs to the view's scope.
     *
     * @param list the list
     * @param items the elements, made by {@link ObservableList#of(Object[])} or {@link ObservableList#copyOf}
     * @param text the text of an element
     * @param <E> the type of the elements
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if the list is already bound
     */
    public <E> void bind(JList<E> list, ObservableList<E> items, Function<? super E, String> text) {
        ListBindings.bind(view.scope(), checked(list, items, text), items, text, null, null);
    }

    /**
     * Shows an observable list in a list, as {@link #bind(JList, ObservableList, Function)} does, and keeps its single
     * selection and a property in step: a user's selection sets the property, and a change of the property selects its
     * value, or clears the selection for an empty value or one that isn't in the list, then setting the property to
     * empty. Changes of the list keep the selection on the selected element, by tracking its index.
     *
     * @param list the list
     * @param items the elements, made by {@link ObservableList#of(Object[])} or {@link ObservableList#copyOf}
     * @param text the text of an element
     * @param selection the selected element, empty when nothing is selected
     * @param <E> the type of the elements
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if the list is already bound
     */
    public <E> void bind(
            JList<E> list, ObservableList<E> items, Function<? super E, String> text, Property<Optional<E>> selection) {
        Objects.requireNonNull(selection, "selection");
        ListBindings.bind(view.scope(), checked(list, items, text), items, text, selection, null);
    }

    /**
     * Shows an observable list in a list and keeps its single selection and a property in step, as {@link #bind(JList,
     * ObservableList, Function, Property)} does, matching the selected element by key rather than by equality: after a
     * change of the list, the element with the selected element's key keeps the selection, wherever it moved.
     *
     * @param list the list
     * @param items the elements, made by {@link ObservableList#of(Object[])} or {@link ObservableList#copyOf}
     * @param text the text of an element
     * @param selection the selected element, empty when nothing is selected
     * @param key the key of an element, compared with {@link Objects#equals(Object, Object)}
     * @param <E> the type of the elements
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalStateException if the list is already bound
     */
    public <E> void bind(
            JList<E> list,
            ObservableList<E> items,
            Function<? super E, String> text,
            Property<Optional<E>> selection,
            Function<? super E, ?> key) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(key, "key");
        ListBindings.bind(view.scope(), checked(list, items, text), items, text, selection, key);
    }

    /**
     * Shows an observable list in a table, a row per element and a column per {@link Column}, in order: sets a new
     * model on the table, which follows the observable list change by change on the EDT, and whose model index of a
     * column is its position in {@code columns}. The table has no row sorter, so a view index of a row is its index in
     * the list. Its columns exist when this method returns, so that a table bound in {@code content()} has its order
     * and widths restored with the window state. The subscription belongs to the view's scope.
     *
     * @param table the table
     * @param rows the rows, made by {@link ObservableList#of(Object[])} or {@link ObservableList#copyOf}
     * @param columns the columns, in order
     * @param <E> the type of the rows
     * @throws NullPointerException if an argument or a column is {@code null}
     * @throws IllegalStateException if the table is already bound
     */
    public <E> void bind(JTable table, ObservableList<E> rows, List<Column<E, ?>> columns) {
        ListBindings.bind(view.scope(), checked(table, rows), rows, List.copyOf(columns), null, null);
        followHeaders(table);
    }

    /**
     * Shows an observable list in a table, as {@link #bind(JTable, ObservableList, List)} does, and keeps its single
     * selection and a property in step: a user's selection sets the property, and a change of the property selects its
     * row, or clears the selection for an empty value or one that isn't in the list, then setting the property to
     * empty. Changes of the list keep the selection on the selected row, by tracking its index.
     *
     * @param table the table
     * @param rows the rows, made by {@link ObservableList#of(Object[])} or {@link ObservableList#copyOf}
     * @param columns the columns, in order
     * @param selection the selected row, empty when nothing is selected
     * @param <E> the type of the rows
     * @throws NullPointerException if an argument or a column is {@code null}
     * @throws IllegalStateException if the table is already bound
     */
    public <E> void bind(
            JTable table, ObservableList<E> rows, List<Column<E, ?>> columns, Property<Optional<E>> selection) {
        Objects.requireNonNull(selection, "selection");
        ListBindings.bind(view.scope(), checked(table, rows), rows, List.copyOf(columns), selection, null);
        followHeaders(table);
    }

    /**
     * Shows an observable list in a table and keeps its single selection and a property in step, as
     * {@link #bind(JTable, ObservableList, List, Property)} does, matching the selected row by key rather than by
     * equality: after a change of the list, the row with the selected row's key keeps the selection, wherever it moved,
     * so that a row modified, moved or reloaded stays selected.
     *
     * @param table the table
     * @param rows the rows, made by {@link ObservableList#of(Object[])} or {@link ObservableList#copyOf}
     * @param columns the columns, in order
     * @param selection the selected row, empty when nothing is selected
     * @param key the key of a row, compared with {@link Objects#equals(Object, Object)}
     * @param <E> the type of the rows
     * @throws NullPointerException if an argument or a column is {@code null}
     * @throws IllegalStateException if the table is already bound
     */
    public <E> void bind(
            JTable table,
            ObservableList<E> rows,
            List<Column<E, ?>> columns,
            Property<Optional<E>> selection,
            Function<? super E, ?> key) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(key, "key");
        ListBindings.bind(view.scope(), checked(table, rows), rows, List.copyOf(columns), selection, key);
        followHeaders(table);
    }

    /**
     * Runs an update that computes texts from messages or resources: at once, then after every change of one of the
     * sources, on the EDT, as the targets of {@link #bind(Consumer, Observable)} are, and again at every refresh of the
     * open windows, after a change of the display locale, of the appearance, or a reload of the resources. Its
     * subscriptions belong to the view's scope. Several changes before the update runs cause one run.
     *
     * <pre>{@code ui.render(() -> status.setText(messages.words(words.get())), words);}</pre>
     *
     * @param render the update, run on the EDT
     * @param sources the observables whose changes run it again
     * @throws NullPointerException if an argument or a source is {@code null}
     */
    public void render(Runnable render, Observable<?>... sources) {
        Objects.requireNonNull(render, "render");
        List<Observable<?>> observed = List.of(sources);
        render.run();
        Runnable update = EdtDelivery.updater(view.scope(), render, EdtDelivery.Order.VIEW);
        observed.forEach(source -> source.subscribe(view.scope(), ignored -> update.run()));
        view.onRefresh(RefreshPhase.CODE, view.scope(), render);
    }

    private static <E> JList<E> checked(JList<E> list, ObservableList<E> items, Function<? super E, String> text) {
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(text, "text");
        return list;
    }

    private static JTable checked(JTable table, ObservableList<?> rows) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(rows, "rows");
        return table;
    }

    // The headers of the columns, given as suppliers, are read again at every refresh, whatever order the user gave the
    // columns.
    private void followHeaders(JTable table) {
        view.onRefresh(RefreshPhase.CODE, view.scope(), () -> {
            TableModel model = table.getModel();
            for (TableColumn column : Collections.list(table.getColumnModel().getColumns())) {
                column.setHeaderValue(model.getColumnName(column.getModelIndex()));
            }
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                header.repaint();
            }
        });
    }

    /**
     * Adds a component as the last tab of a tabbed pane, and applies its {@code tab-title} at once, from the resources
     * of the view's {@code @Exposed} field that holds it, nested ones included: a tab needs no empty title, and gets
     * its title whenever it is added, during the build or later. A refresh applies it again.
     *
     * @param tabs the tabbed pane
     * @param component the component, held by an {@code @Exposed} component field of the view
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if no {@code @Exposed} component field of the view holds the component
     * @throws IllegalStateException if the view is not built yet, as in its constructor
     */
    public void addTab(JTabbedPane tabs, Component component) {
        Objects.requireNonNull(tabs, "tabs");
        Objects.requireNonNull(component, "component");
        if (view.view().isEmpty()) {
            throw new IllegalStateException(
                    "The view " + view.type().getName() + " is not built yet: add its tabs in content() or later");
        }
        ViewBuilder builder = view.injector().getInstance(ViewBuilder.class);
        String path = builder.fieldOf(view, component)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No @Exposed component field of " + view.type().getName() + " holds the "
                                + component.getClass().getName()));
        tabs.addTab(builder.tabTitle(view, path).orElse(""), component);
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
        running.whenEnded(unblock);
    }

    /**
     * Runs an action on the EDT every period, the first time one period after the call, until the returned subscription
     * or this view's scope is closed. The period is measured with the {@code Ticker} bean; a late run doesn't make up
     * for the runs it missed: the next one comes one period after it. An exception thrown by the action goes to the
     * {@code FailureHandler}, and the action keeps its schedule; an {@link Error} is not caught.
     *
     * @param period the period
     * @param action the action
     * @return the subscription, which stops the action when closed
     * @throws NullPointerException if {@code period} or {@code action} is {@code null}
     * @throws IllegalArgumentException if the period is not positive
     * @throws IllegalStateException if this view's scope is closed
     */
    public Subscription every(Duration period, Runnable action) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(action, "action");
        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("The period must be positive: " + period);
        }
        Subscription added = periodic.add(period, action);
        Runnable withdraw;
        try {
            withdraw = view.scope().register(added);
        } catch (IllegalStateException e) {
            // A closed scope would never stop the action.
            added.close();
            throw e;
        }
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                withdraw.run();
                added.close();
            }
        };
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

    /**
     * Returns a new row of buttons: a panel with a centered {@link FlowLayout}, holding one button per command, in
     * order, made with {@link BoundCommand#createToggleButton()} for a command with a selected state and with
     * {@link BoundCommand#createButton()} for any other. Unlike the buttons of a toolbar, they show their text.
     *
     * @param commands the commands
     * @return the panel
     */
    public JPanel buttonBar(BoundCommand... commands) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        for (BoundCommand command : commands) {
            Objects.requireNonNull(command, "command");
            bar.add(command.hasSelectedState() ? command.createToggleButton() : command.createButton());
        }
        return bar;
    }

    BoundMenu menu(Class<?> owner, String id) {
        return builder.menu(owner, id);
    }
}
