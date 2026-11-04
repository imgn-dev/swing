package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.swing.Property;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.CommandRegistry;
import be.imgn.swing.ui.DynamicEntry;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.StandardMenus;
import java.util.Arrays;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;

/**
 * The exceptions that the handlers of commands and the actions of dynamic items throw go to the {@code FailureHandler},
 * which the tester records and fails its next call with; their errors propagate.
 */
class HandlerFailureTest {

    @Test
    void anExceptionOfAHandlerGoesToTheFailureHandlerAndTheCommandRan() {
        IllegalStateException failure = new IllegalStateException("The handler failed");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            CommandRegistry registry = app.commands(CommandRegistry.class);
            BoundCommand command = app.call(() -> registry.command(HandlerFailureTest.class, "fail")
                    .handler(context -> {
                        throw failure;
                    })
                    .bind());

            assertThat(command.execute()).isTrue();

            assertThatThrownBy(app::isRunning)
                    .isInstanceOf(AssertionError.class)
                    .cause()
                    .isSameAs(failure);
        }
    }

    @Test
    void anErrorOfAHandlerPropagatesOutOfTheCommand() {
        InternalError failure = new InternalError("The handler broke an invariant");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            CommandRegistry registry = app.commands(CommandRegistry.class);
            BoundCommand command = app.call(() -> registry.command(HandlerFailureTest.class, "breakInvariant")
                    .handler(context -> {
                        throw failure;
                    })
                    .bind());

            assertThatThrownBy(command::execute).isSameAs(failure);
            assertThat(app.isRunning()).isTrue();
        }
    }

    @Test
    void anExceptionOfATaskHandlerGoesToTheFailureHandlerAndStartsNothing() {
        IllegalStateException failure = new IllegalStateException("The task handler failed");
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            CommandRegistry registry = app.commands(CommandRegistry.class);
            BoundCommand command = app.call(() -> registry.command(HandlerFailureTest.class, "failTask")
                    .taskHandler(context -> {
                        throw failure;
                    })
                    .bind());

            assertThat(command.execute()).isTrue();

            assertThatThrownBy(app::isRunning)
                    .isInstanceOf(AssertionError.class)
                    .cause()
                    .isSameAs(failure);
            assertThat(app.call(() -> command.running().get())).isFalse();
        }
    }

    @Test
    void anExceptionOfADynamicItemsActionGoesToTheFailureHandler() {
        IllegalStateException failure = new IllegalStateException("The item failed");
        Property<List<DynamicEntry>> entries = Property.of(List.of(DynamicEntry.item("&Fail", () -> {
            throw failure;
        })));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            StandardMenus menus = app.commands(StandardMenus.class);
            JMenu menu =
                    app.call(() -> menus.file().with(MenuEntry.dynamic(entries)).createMenu());
            JMenuItem item = app.call(() -> Arrays.stream(menu.getMenuComponents())
                    .filter(JMenuItem.class::isInstance)
                    .map(JMenuItem.class::cast)
                    .filter(found -> found.getText().equals("Fail"))
                    .findFirst()
                    .orElseThrow());

            // Had the exception left the action, the call would rethrow it as it is.
            assertThatThrownBy(() -> app.run(item::doClick))
                    .isInstanceOf(AssertionError.class)
                    .cause()
                    .isSameAs(failure);
        }
    }

    @Test
    void anErrorOfADynamicItemsActionPropagates() {
        InternalError failure = new InternalError("The item broke an invariant");
        Property<List<DynamicEntry>> entries = Property.of(List.of(DynamicEntry.item("&Break", () -> {
            throw failure;
        })));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            StandardMenus menus = app.commands(StandardMenus.class);
            JMenu menu =
                    app.call(() -> menus.file().with(MenuEntry.dynamic(entries)).createMenu());
            JMenuItem item = app.call(() -> Arrays.stream(menu.getMenuComponents())
                    .filter(JMenuItem.class::isInstance)
                    .map(JMenuItem.class::cast)
                    .filter(found -> found.getText().equals("Break"))
                    .findFirst()
                    .orElseThrow());

            assertThatThrownBy(() -> app.run(item::doClick)).isSameAs(failure);
            assertThat(app.isRunning()).isTrue();
        }
    }
}
