package be.imgn.swing.examples.added;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenFrame;
import be.imgn.swing.ui.CommandPalette;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.PaletteEntry;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.View;
import be.imgn.swing.ui.Window;
import be.imgn.swing.ui.internal.AddedViews;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import java.awt.ComponentOrientation;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

/** Views opened in an open window: how they are built, belong to it, close, and keep their state. */
class AddedViewsTest {

    private static final KeyStroke ACT = KeyStroke.getKeyStroke("ctrl K");

    private static <V extends View> V open(AppTester<AddedApp> app, Class<V> type) {
        return open(app, main(app), app.application().area, type, false);
    }

    private static <V extends View> V open(
            AppTester<?> app, WindowHost window, JPanel area, Class<V> type, boolean factory) {
        AddedViews added = app.bean(AddedViews.class);
        Optional<Class<V>> known = factory ? Optional.empty() : Optional.of(type);
        return app.call(() -> type.cast(added.open(window, known, injector -> injector.getInstance(type), host -> {
                    JComponent content = contentOf(host);
                    area.add(content);
                    return content;
                })
                .view()
                .orElseThrow()));
    }

    private static JComponent contentOf(ViewHost host) {
        Object view = host.view().orElseThrow();
        return ((View) view).content();
    }

    private static boolean close(AppTester<?> app, View view) {
        AddedViews added = app.bean(AddedViews.class);
        return app.call(() -> added.close(host(app, view), () -> {}));
    }

    private static WindowHost main(AppTester<?> app) {
        return app.bean(WindowRegistry.class).main().orElseThrow();
    }

    private static WindowHost windowOf(AppTester<?> app, View own) {
        return app.bean(WindowRegistry.class).windows().stream()
                .filter(window ->
                        window.views().getFirst().view().filter(own::equals).isPresent())
                .findFirst()
                .orElseThrow();
    }

    private static ViewHost host(AppTester<?> app, View view) {
        return app.bean(WindowRegistry.class).windows().stream()
                .flatMap(window -> window.views().stream())
                .filter(host -> host.view().filter(view::equals).isPresent())
                .findFirst()
                .orElseThrow();
    }

    private static List<Class<?>> types(AppTester<?> app) {
        return app.call(
                () -> main(app).views().stream().<Class<?>>map(ViewHost::type).toList());
    }

    private static void press(AppTester<?> app, KeyStroke keyStroke) {
        JRootPane rootPane = main(app).window().rootPane();
        app.run(() -> rootPane.getActionMap()
                .get(rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(keyStroke))
                .actionPerformed(new ActionEvent(rootPane, ActionEvent.ACTION_PERFORMED, "")));
    }

    @Test
    void buildsAnAddedViewInTheOpenWindowAfterItsOwnView() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            ToolView tool = open(app, ToolView.class);

            assertThat(types(app)).containsExactly(AddedApp.class, ToolView.class);
            assertThat(app.call(tool.caption::getText)).isEqualTo("Tool");
            assertThat(app.bean(tool, Window.class)).isSameAs(app.bean(Window.class));
            boolean added = app.call(() -> host(app, tool).isAdded());
            assertThat(added).isTrue();
            assertThat(app.inspect(tool.table).view()).isEqualTo(ToolView.class);
            assertThat(app.inspect(tool.table).field()).isEqualTo("table");
        }
    }

    @Test
    void appliesTheWindowsOrientationToTheContentOfAnAddedView() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class, Locale.of("ar"))) {
            ToolView tool = open(app, ToolView.class);

            assertThat(app.call(tool.table::getComponentOrientation)).isEqualTo(ComponentOrientation.RIGHT_TO_LEFT);
        }
    }

    @Test
    void refreshesAnAddedViewWithItsWindow() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class, Locale.ENGLISH)) {
            ToolView tool = open(app, ToolView.class);

            app.locale(Locale.FRENCH);

            assertThat(app.call(tool.caption::getText)).isEqualTo("Outil");
            assertThat(app.call(() -> tool.table.getAccessibleContext().getAccessibleName()))
                    .isEqualTo("Lignes");
        }
    }

    @Test
    void checksTheAccessibilityOfAnAddedViewsContentAndLeavesNothingWhenItFails() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            assertThatThrownBy(() -> open(app, UnnamedView.class)).hasMessageContaining("has no accessible name");

            assertThatThrownBy(app::isRunning)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(
                            "The JTextField of be.imgn.swing.examples.added.UnnamedView has no accessible" + " name");
            assertThat(types(app)).containsExactly(AddedApp.class);
        }
    }

    @Test
    void refusesToOpenAViewDuringAnotherViewsBuildOrInAClosedWindow() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            AddedViews added = app.bean(AddedViews.class);
            WindowHost window = main(app);

            assertThatIllegalStateException()
                    .isThrownBy(() -> app.call(() -> added.open(
                            window,
                            Optional.of(ToolView.class),
                            injector -> injector.getInstance(ToolView.class),
                            host -> {
                                added.open(
                                        window,
                                        Optional.of(ToolView.class),
                                        injector -> injector.getInstance(ToolView.class),
                                        inner -> new JComponent() {});
                                return new JComponent() {};
                            })))
                    .withMessageContaining("being built");
            app.run(() -> {});
            assertThat(types(app)).containsExactly(AddedApp.class);

            app.commands(StandardCommands.class).exit().execute();

            assertThatIllegalStateException()
                    .isThrownBy(() -> app.call(() -> added.open(
                            window,
                            Optional.of(ToolView.class),
                            injector -> injector.getInstance(ToolView.class),
                            host -> new JComponent() {})))
                    .withMessageContaining("closed or closing");
        }
    }

    @Test
    void asksTheVetoesOfAnAddedViewWhenItClosesAndKeepsItOpenOnAVeto() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            ToolView tool = open(app, ToolView.class);
            ToolLog log = app.bean(ToolLog.class);
            app.run(() -> log.closable(false));

            assertThat(close(app, tool)).isFalse();
            assertThat(types(app)).containsExactly(AddedApp.class, ToolView.class);

            app.run(() -> log.closable(true));
            assertThat(close(app, tool)).isTrue();
            assertThat(types(app)).containsExactly(AddedApp.class);
            assertThat(log.events()).containsExactly("veto 1", "veto 1");
        }
    }

    @Test
    void asksTheVetoesOfTheAddedViewsWhenTheApplicationExits() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            open(app, ToolView.class);
            ToolLog log = app.bean(ToolLog.class);
            app.run(() -> log.closable(false));

            app.commands(StandardCommands.class).exit().execute();

            assertThat(app.isRunning()).isTrue();
            assertThat(log.events()).containsExactly("veto 1");
        }
    }

    @Test
    void unsubscribesAClosedViewFromTheBus() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            ToolView tool = open(app, ToolView.class);
            app.publish(new Ping());

            close(app, tool);
            app.publish(new Ping());

            assertThat(app.bean(ToolLog.class).events()).containsExactly("ping 1", "veto 1");
        }
    }

    @Test
    void givesASharedShortcutToTheViewBuiltLastAndBackWhenItCloses() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            open(app, ToolView.class);
            ToolView second = open(app, ToolView.class);

            press(app, ACT);
            close(app, second);
            press(app, ACT);

            assertThat(app.bean(ToolLog.class).events()).containsExactly("act 2", "veto 2", "act 1");
        }
    }

    @Test
    void keepsTheShortcutsOfTheOtherViewsWhenAnEarlierOneCloses() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            ToolView first = open(app, ToolView.class);
            open(app, ToolView.class);

            close(app, first);
            press(app, ACT);

            assertThat(app.bean(ToolLog.class).events()).containsExactly("veto 1", "act 2");
        }
    }

    @Test
    void listsASharedCommandOnceInThePaletteForTheViewBuiltLast() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            open(app, ToolView.class);
            ToolView second = open(app, ToolView.class);

            List<PaletteEntry> acts = app.call(() -> app.bean(CommandPalette.class).entries().stream()
                    .filter(entry -> entry.command().id().equals("act"))
                    .toList());

            assertThat(acts)
                    .singleElement()
                    .satisfies(entry -> assertThat(entry.command())
                            .isSameAs(app.bean(second, ToolViewCommands.class).act()));
        }
    }

    @Test
    void restoresTheStateAClosedViewLeftWhenItsClassOpensAgain() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            ToolView tool = open(app, ToolView.class);
            app.run(() -> tool.table.getColumnModel().moveColumn(0, 1));
            close(app, tool);

            ToolView again = open(app, ToolView.class);

            assertThat(app.call(() -> again.table.getColumnModel().getColumn(0).getModelIndex()))
                    .isEqualTo(1);
        }
    }

    @Test
    void savesTheStateOfAddedViewsWithTheirWindowAndRestoresItAfterARestart() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path file;
            try (AppTester<AddedApp> app =
                    AppTester.builder(AddedApp.class).fileSystem(fileSystem).start()) {
                ToolView tool = open(app, ToolView.class);
                app.run(() -> tool.table.getColumnModel().moveColumn(0, 1));
                file = app.info().directories().state().resolve("window-state.toml");

                app.commands(StandardCommands.class).exit().execute();
            }
            assertThat(Files.readString(file))
                    .contains("windows.main.views.\"be.imgn.swing.examples.added.ToolView\".components.table");
            try (AppTester<AddedApp> app =
                    AppTester.builder(AddedApp.class).fileSystem(fileSystem).start()) {
                ToolView tool = open(app, ToolView.class);

                assertThat(app.call(
                                () -> tool.table.getColumnModel().getColumn(0).getModelIndex()))
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void savesTheStateAClosedViewLeftWithItsWindow() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path file;
            try (AppTester<AddedApp> app =
                    AppTester.builder(AddedApp.class).fileSystem(fileSystem).start()) {
                ToolView tool = open(app, ToolView.class);
                app.run(() -> tool.table.getColumnModel().moveColumn(0, 1));
                close(app, tool);
                file = app.info().directories().state().resolve("window-state.toml");

                app.commands(StandardCommands.class).exit().execute();
            }

            assertThat(Files.readString(file)).contains("be.imgn.swing.examples.added.ToolView");
        }
    }

    @Test
    void returnsAShortcutThatAClosedViewTookToItsCommand() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            KeyStroke copy = KeyStroke.getKeyStroke("ctrl C");
            GrabbingView grabbing = open(app, GrabbingView.class);
            String taken = app.call(() -> main(app).shortcuts().commandOf(copy));

            close(app, grabbing);

            assertThat(taken).isEqualTo("be.imgn.swing.examples.added.GrabbingView#grab");
            assertThat(app.call(() -> main(app).shortcuts().commandOf(copy)))
                    .isEqualTo("be.imgn.swing.ui.StandardCommands#copy");
        }
    }

    @Test
    void asksTheVetoesOfAFramesAddedViewsBeforeItsOwnViewsWhenItCloses() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            ToolFrame frame = app.call(() -> app.bean(Frames.class).open(ToolFrame.class));
            ToolView tool = open(app, windowOf(app, frame), frame.area, ToolView.class, false);
            OpenFrame<ToolFrame> open = app.frame(ToolFrame.class);
            assertThat(open.bean(tool, Window.class)).isSameAs(open.bean(Window.class));
            ToolLog log = app.bean(ToolLog.class);
            app.run(() -> log.closable(false));

            assertThat(open.close()).isFalse();

            app.run(() -> log.closable(true));
            assertThat(open.close()).isTrue();
            assertThat(log.events()).containsExactly("veto 1", "veto 1", "frame veto");
        }
    }

    @Test
    void forgetsAClosedViewInThePalette() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            ToolView tool = open(app, ToolView.class);

            close(app, tool);

            assertThat(app.call(() -> app.bean(CommandPalette.class).entries().stream()
                            .map(entry -> entry.command().id())
                            .toList()))
                    .doesNotContain("act");
        }
    }

    @Test
    void leavesNoShortcutNorSubscriberWhenABuildFails() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            assertThatThrownBy(() -> open(app, BrokenToolView.class)).hasMessageContaining("has no accessible name");
            assertThatThrownBy(app::isRunning).isInstanceOf(AssertionError.class);

            app.publish(new Ping());

            assertThat(app.call(() ->
                            Optional.ofNullable(main(app).shortcuts().commandOf(KeyStroke.getKeyStroke("ctrl J")))))
                    .isEmpty();
            assertThat(app.bean(ToolLog.class).events()).isEmpty();
        }
    }

    @Test
    void rejectsABeanOfAViewThatIsNotOpen() {
        try (AppTester<AddedApp> app = AppTester.start(AddedApp.class)) {
            ToolView tool = open(app, ToolView.class);
            close(app, tool);

            assertThatThrownBy(() -> app.bean(tool, Window.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not open");
        }
    }

    @Test
    void savesNoStateForAViewCreatedThroughAFactory() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path file;
            try (AppTester<AddedApp> app =
                    AppTester.builder(AddedApp.class).fileSystem(fileSystem).start()) {
                ToolView tool = open(app, main(app), app.application().area, ToolView.class, true);
                app.run(() -> tool.table.getColumnModel().moveColumn(0, 1));
                close(app, tool);
                open(app, main(app), app.application().area, ToolView.class, true);
                file = app.info().directories().state().resolve("window-state.toml");

                app.commands(StandardCommands.class).exit().execute();
            }

            assertThat(Files.readString(file)).doesNotContain("ToolView");
        }
    }

    @Test
    void keepsTheStateOfAViewThatStaysClosedAcrossARestart() {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<AddedApp> app =
                    AppTester.builder(AddedApp.class).fileSystem(fileSystem).start()) {
                ToolView tool = open(app, ToolView.class);
                app.run(() -> tool.table.getColumnModel().moveColumn(0, 1));
                close(app, tool);
                app.commands(StandardCommands.class).exit().execute();
            }
            try (AppTester<AddedApp> app =
                    AppTester.builder(AddedApp.class).fileSystem(fileSystem).start()) {
                app.commands(StandardCommands.class).exit().execute();
            }
            try (AppTester<AddedApp> app =
                    AppTester.builder(AddedApp.class).fileSystem(fileSystem).start()) {
                ToolView tool = open(app, ToolView.class);

                assertThat(app.call(
                                () -> tool.table.getColumnModel().getColumn(0).getModelIndex()))
                        .isEqualTo(1);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
