package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Property;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.DynamicEntry;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.MenuSpec;
import be.imgn.swing.ui.StandardMenus;
import be.imgn.swing.ui.ToolBarEntry;
import be.imgn.swing.ui.Ui;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import org.junit.jupiter.api.Test;

/** How menus, popups and toolbars are built from descriptions, on the counter's view. */
class MenuBuilderTest {

    @Test
    void hidesLeadingTrailingAndDoubledSeparators() throws Exception {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JMenu menu = onEdt(() -> menus(app)
                    .file()
                    .with(
                            MenuEntry.SEPARATOR,
                            commands(app).increment(),
                            MenuEntry.SEPARATOR,
                            MenuEntry.SEPARATOR,
                            commands(app).exit(),
                            MenuEntry.SEPARATOR)
                    .createMenu());

            List<Component> shown = visible(menu.getPopupMenu());
            assertThat(shown).hasSize(3);
            assertThat(shown.get(0)).isInstanceOf(JMenuItem.class);
            assertThat(shown.get(1)).isInstanceOf(JPopupMenu.Separator.class);
            assertThat(shown.get(2)).isInstanceOf(JMenuItem.class);
            assertThat(actionCommands(shown)).containsExactly("increment", "exit");
        }
    }

    @Test
    void takesTheTextAndMnemonicOfAStandardMenuFromTheFrameworksResources() throws Exception {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JMenuBar bar = onEdt(() -> ui(app).menuBar(
                            menus(app).file().with(commands(app).exit()),
                            menus(app).edit().with(commands(app).copy())));

            assertThat(bar.getMenu(0).getText()).isEqualTo("File");
            assertThat(bar.getMenu(0).getMnemonic()).isEqualTo(KeyEvent.VK_F);
            assertThat(bar.getMenu(1).getText()).isEqualTo("Edit");
            assertThat(bar.getMenu(1).getMnemonic()).isEqualTo(KeyEvent.VK_E);
        }
    }

    @Test
    void rejectsDuplicateMnemonicsInAMenu() throws Exception {
        Property<List<DynamicEntry>> entries =
                Property.of(List.of(DynamicEntry.item("&Alpha", () -> {}), DynamicEntry.item("&Apple", () -> {})));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            assertThatThrownBy(() -> onEdt(() ->
                            menus(app).file().with(MenuEntry.dynamic(entries)).createMenu()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Alpha and Apple");

            // The tester recorded the same failure; take it, so that closing the tester passes.
            assertThatThrownBy(app::isRunning).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void rejectsDuplicateMnemonicsAmongTheMenusOfABar() throws Exception {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            assertThatThrownBy(() -> onEdt(() -> ui(app).menuBar(
                                    menus(app).file().with(commands(app).exit()),
                                    menus(app).file().with(commands(app).increment()))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("File and File");

            assertThatThrownBy(app::isRunning).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void rebuildsADynamicSectionWhenItsObservableChanges() throws Exception {
        Property<List<DynamicEntry>> recent = Property.of(List.of());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JMenu menu = onEdt(() -> menus(app)
                    .file()
                    .with(
                            commands(app).increment(),
                            MenuEntry.SEPARATOR,
                            MenuEntry.dynamic(recent),
                            MenuEntry.SEPARATOR,
                            commands(app).exit())
                    .createMenu());
            assertThat(actionCommands(visible(menu.getPopupMenu()))).containsExactly("increment", "exit");
            // Without entries, the two separators around the section are doubled: one is hidden.
            assertThat(visible(menu.getPopupMenu())).hasSize(3);

            recent.set(List.copyOf(DynamicEntry.numbered(
                    List.of(DynamicEntry.item("notes.txt", () -> {}), DynamicEntry.item("todo.txt", () -> {})))));
            assertThat(app.isRunning()).isTrue();

            List<Component> shown = visible(menu.getPopupMenu());
            assertThat(shown).hasSize(6);
            assertThat(texts(shown)).containsExactly("Increment", "1 notes.txt", "2 todo.txt", "Exit");
            assertThat(((JMenuItem) shown.get(2)).getMnemonic()).isEqualTo(KeyEvent.VK_1);
        }
    }

    @Test
    void closesTheSubscriptionsOfAReplacedSubmenu() throws Exception {
        Property<List<DynamicEntry>> inner = Property.of(List.of(DynamicEntry.item("One", () -> {})));
        Property<List<DynamicEntry>> outer = Property.of(List.of());
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            MenuSpec submenu = menus(app).edit().with(MenuEntry.dynamic(inner));
            JMenu menu =
                    onEdt(() -> menus(app).file().with(MenuEntry.dynamic(outer)).createMenu());

            outer.set(List.of(submenu));
            assertThat(app.isRunning()).isTrue();
            JMenu first = (JMenu) visible(menu.getPopupMenu()).get(0);
            assertThat(texts(visible(first.getPopupMenu()))).containsExactly("One");

            // The submenu is replaced: its section's subscription to `inner` goes with it.
            outer.set(List.of());
            assertThat(app.isRunning()).isTrue();
            inner.set(List.of(DynamicEntry.item("One", () -> {}), DynamicEntry.item("Two", () -> {})));
            assertThat(app.isRunning()).isTrue();

            assertThat(visible(menu.getPopupMenu())).isEmpty();
            assertThat(texts(visible(first.getPopupMenu()))).containsExactly("One");
        }
    }

    @Test
    void setsAPopupMenuOnItsInvoker() throws Exception {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JLabel label = app.component(JLabel.class);

            JPopupMenu popup =
                    onEdt(() -> ui(app).popupMenu(label, commands(app).increment()));

            assertThat(label.getComponentPopupMenu()).isSameAs(popup);
            assertThat(actionCommands(visible(popup))).containsExactly("increment");
        }
    }

    @Test
    void buildsAToolBarWhoseButtonsShareTheCommandsAction() throws Exception {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            JToolBar bar = onEdt(() -> ui(app).toolBar(
                            commands(app).increment(),
                            ToolBarEntry.SEPARATOR,
                            commands(app).exit()));

            List<Component> shown = visible(bar);
            assertThat(shown).hasSize(3);
            JButton increment = (JButton) shown.get(0);
            assertThat(increment.getAction()).isSameAs(commands(app).increment().asAction());
            // Without an icon, a button keeps its text.
            assertThat(increment.getHideActionText()).isFalse();
            assertThat(increment.getText()).isEqualTo("Increment");
        }
    }

    private static Ui ui(AppTester<CounterApp> app) {
        return app.commands(Ui.class);
    }

    private static StandardMenus menus(AppTester<CounterApp> app) {
        return app.commands(StandardMenus.class);
    }

    private static CounterAppCommands commands(AppTester<CounterApp> app) {
        return app.commands(CounterAppCommands.class);
    }

    // Runs on the EDT, as every call of Ui must, and rethrows what it throws.
    private static <T> T onEdt(Supplier<T> call) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        try {
            EventQueue.invokeAndWait(() -> result.set(call.get()));
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
        return Objects.requireNonNull(result.get());
    }

    private static List<Component> visible(Container container) {
        List<Component> visible = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component.isVisible()) {
                visible.add(component);
            }
        }
        return visible;
    }

    private static List<String> actionCommands(List<Component> components) {
        return components.stream()
                .filter(AbstractButton.class::isInstance)
                .map(component -> ((AbstractButton) component).getActionCommand())
                .toList();
    }

    private static List<String> texts(List<Component> components) {
        return components.stream()
                .filter(JMenuItem.class::isInstance)
                .map(component -> ((JMenuItem) component).getText())
                .toList();
    }
}
