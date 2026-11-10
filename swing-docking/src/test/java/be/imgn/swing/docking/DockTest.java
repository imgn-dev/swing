package be.imgn.swing.docking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.memfs.MemoryFileSystems;
import be.imgn.swing.docking.DockLayout.Orientation;
import be.imgn.swing.docking.DockLayout.Split;
import be.imgn.swing.docking.DockLayout.Tabs;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/** The dock's API against the headless surface: placements, layout, active view, closing, titles, saved layout. */
class DockTest {

    private static final String STATE = "window-state.toml";

    private static <V extends DockView> V view(AppTester<?> app, Class<V> type) {
        Dock dock = app.bean(Dock.class);
        return app.call(() -> dock.views().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " in " + dock.views())));
    }

    private static Tabs tabs(DockView... views) {
        return new Tabs(List.of(views), views[views.length - 1]);
    }

    // A file system whose window state holds a dock's saved layout, which a first run creates where it belongs.
    private static FileSystem savedState(String saved) throws IOException {
        FileSystem fileSystem = MemoryFileSystems.newFileSystem();
        try (AppTester<DockApp> app =
                AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
            app.commands(StandardCommands.class).exit().execute();
        }
        Files.writeString(stateFile(fileSystem), saved);
        return fileSystem;
    }

    // The window state the application saved, wherever its fake platform puts its state directory.
    private static Path stateFile(FileSystem fileSystem) throws IOException {
        try (Stream<Path> paths = Files.walk(fileSystem.getPath("/"))) {
            return paths.filter(path -> path.endsWith(STATE)).findFirst().orElseThrow();
        }
    }

    private static Optional<DockLayout> layout(AppTester<?> app) {
        Dock dock = app.bean(Dock.class);
        return app.call(dock::layout);
    }

    @Test
    void opensTheFirstLayoutAsItsPlacementsSay() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            AlphaView alpha = view(app, AlphaView.class);
            BetaView beta = view(app, BetaView.class);
            GammaView gamma = view(app, GammaView.class);

            assertThat(layout(app))
                    .contains(new Split(
                            Orientation.HORIZONTAL,
                            DockApp.BETA,
                            new Split(Orientation.VERTICAL, 1 - DockApp.GAMMA, tabs(beta), tabs(gamma)),
                            tabs(alpha)));
            assertThat(app.call(() -> app.bean(Dock.class).views())).containsExactly(alpha, beta, gamma);
            assertThat(app.call(() -> app.bean(Dock.class).active().get())).contains(gamma);
        }
    }

    @Test
    void drawsTheLayoutIntoTheFramesRootPane() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            assertThat(app.components(JTabbedPane.class)).hasSize(3);
            assertThat(app.component(JTextField.class, "Beta")).isSameAs(view(app, BetaView.class).field);
            assertThat(app.inspect(view(app, BetaView.class).field).view()).isEqualTo(BetaView.class);
        }
    }

    @Test
    void splitsOnEverySideAndFillsAnEmptyDock() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            Dock dock = app.bean(Dock.class);
            assertThat(layout(app)).isEmpty();

            AlphaView alpha = app.call(() -> dock.open(AlphaView.class, Placement.of(DockRegion.EAST, 0.25)));
            BetaView beta = app.call(() -> dock.open(BetaView.class, Placement.of(DockRegion.EAST, 0.25)));
            GammaView gamma = app.call(
                    () -> dock.open(GammaView.class, Placement.beside(AlphaView.class, DockRegion.NORTH, 0.4)));

            assertThat(layout(app))
                    .contains(new Split(
                            Orientation.HORIZONTAL,
                            0.75,
                            new Split(Orientation.VERTICAL, 0.4, tabs(gamma), tabs(alpha)),
                            tabs(beta)));
        }
    }

    @Test
    void addsATabToTheTargetsGroupOrTheActiveViewsAndSelectsIt() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            Dock dock = app.bean(Dock.class);
            AlphaView alpha = app.call(() -> dock.open(AlphaView.class, Placement.tab()));
            BetaView beta = app.call(() -> dock.open(BetaView.class, Placement.of(DockRegion.WEST, 0.5)));
            GammaView gamma = app.call(() -> dock.open(GammaView.class, Placement.tabWith(AlphaView.class)));
            VetoView veto = app.call(() -> dock.open(VetoView.class, Placement.tab()));

            assertThat(layout(app))
                    .contains(new Split(
                            Orientation.HORIZONTAL, 0.5, tabs(beta), new Tabs(List.of(alpha, gamma, veto), veto)));
        }
    }

    @Test
    void bringsAnOpenViewOfAClassToTheFrontRatherThanOpeningAnother() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            Dock dock = app.bean(Dock.class);
            AlphaView alpha = view(app, AlphaView.class);

            AlphaView again = app.call(() -> dock.open(AlphaView.class, Placement.of(DockRegion.EAST, 0.5)));

            assertThat(again).isSameAs(alpha);
            assertThat(app.call(() -> dock.views())).hasSize(3);
            assertThat(app.call(() -> dock.active().get())).contains(alpha);
            assertThat(app.focused()).containsSame(alpha.field);
        }
    }

    @Test
    void selectsAViewWithoutMakingItActive() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            Dock dock = app.bean(Dock.class);
            AlphaView alpha = app.call(() -> dock.open(AlphaView.class, Placement.tab()));
            BetaView beta = app.call(() -> dock.open(BetaView.class, Placement.tab()));

            app.run(() -> dock.select(alpha));

            assertThat(layout(app)).contains(new Tabs(List.of(alpha, beta), alpha));
            assertThat(app.call(() -> dock.active().get())).contains(beta);
        }
    }

    @Test
    void makesTheViewActiveBeforeTheClosedOneActiveAgain() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            Dock dock = app.bean(Dock.class);
            AlphaView alpha = view(app, AlphaView.class);
            BetaView beta = view(app, BetaView.class);
            GammaView gamma = view(app, GammaView.class);
            app.run(() -> dock.toFront(alpha));
            app.run(() -> dock.toFront(beta));

            assertThat(app.call(() -> dock.close(beta))).isTrue();

            assertThat(app.call(() -> dock.active().get())).contains(alpha);
            assertThat(layout(app)).contains(new Split(Orientation.HORIZONTAL, DockApp.BETA, tabs(gamma), tabs(alpha)));
        }
    }

    @Test
    void keepsAViewOpenWhenItVetoesItsClose() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            Dock dock = app.bean(Dock.class);
            DockLog log = app.bean(DockLog.class);
            VetoView veto = app.call(() -> dock.open(VetoView.class, Placement.tab()));
            app.run(() -> log.closable(false));

            assertThat(app.call(() -> dock.close(veto))).isFalse();
            assertThat(app.call(() -> dock.views())).containsExactly(veto);

            app.run(() -> log.closable(true));
            assertThat(app.call(() -> dock.close(veto))).isTrue();
            assertThat(app.call(() -> dock.views())).isEmpty();
            assertThat(layout(app)).isEmpty();
            assertThat(app.call(() -> dock.active().get())).isEmpty();
        }
    }

    @Test
    void rejectsAViewThatIsNotOpen() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            Dock dock = app.bean(Dock.class);
            AlphaView alpha = view(app, AlphaView.class);
            app.run(() -> dock.close(alpha));

            assertThatIllegalArgumentException().isThrownBy(() -> app.run(() -> dock.select(alpha)));
            assertThatIllegalArgumentException().isThrownBy(() -> app.call(() -> dock.title(alpha)));
        }
    }

    @Test
    void titlesAViewWithItsResourceElseItsSimpleNameAndShowsItsIcon() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            Dock dock = app.bean(Dock.class);

            assertThat(app.call(() -> dock.title(view(app, AlphaView.class)))).isEqualTo("Alpha");
            assertThat(app.call(() -> dock.title(view(app, GammaView.class)))).isEqualTo("GammaView");
            assertThat(app.call(() -> app.components(JTabbedPane.class).stream()
                            .map(tabs -> tabs.getTitleAt(0))
                            .toList()))
                    .containsExactlyInAnyOrder("Alpha", "Beta", "GammaView");
            assertThat(app.call(() -> app.components(JTabbedPane.class).stream()
                            .filter(tabs -> tabs.getTitleAt(0).equals("Alpha"))
                            .map(tabs -> tabs.getIconAt(0) != null)
                            .toList()))
                    .containsExactly(true);
        }
    }

    @Test
    void followsTheLocaleWithAResourceTitleButNotWithATitleTheViewSet() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class, Locale.ENGLISH)) {
            Dock dock = app.bean(Dock.class);
            BetaView beta = app.call(() -> dock.open(BetaView.class, Placement.tab()));
            DockView note = app.call(() -> dock.open(
                    NoteViewFactory.class, factory -> factory.create("Groceries"), "groceries", Placement.tab()));

            app.locale(Locale.FRENCH);

            assertThat(app.call(() -> dock.title(beta))).isEqualTo("Bêta");
            assertThat(app.call(() -> dock.title(note))).isEqualTo("Groceries");
        }
    }

    @Test
    void opensOneViewPerFactoryAndKey() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            Dock dock = app.bean(Dock.class);
            DockView first = app.call(
                    () -> dock.open(NoteViewFactory.class, factory -> factory.create("One"), "one", Placement.tab()));
            DockView second = app.call(
                    () -> dock.open(NoteViewFactory.class, factory -> factory.create("Two"), "two", Placement.tab()));
            DockView again = app.call(
                    () -> dock.open(NoteViewFactory.class, factory -> factory.create("Other"), "one", Placement.tab()));

            assertThat(again).isSameAs(first);
            assertThat(app.call(() -> dock.views())).containsExactly(first, second);
            assertThat(app.call(() -> dock.title(first))).isEqualTo("One");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> app.call(() -> dock.open(
                            NoteViewFactory.class, factory -> factory.create("Blank"), " ", Placement.tab())));
        }
    }

    @Test
    void refusesToOpenAViewDuringAnotherViewsBuild() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            Dock dock = app.bean(Dock.class);

            assertThatThrownBy(() -> app.call(() -> dock.open(NestingView.class, Placement.tab())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("being built");
            assertThat(app.call(() -> dock.views())).isEmpty();
        }
    }

    @Test
    void refusesDockedInAViewThatNoDockOpened() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            assertThatThrownBy(() -> app.run(() -> app.bean(Ui.class).showDialog(DockedDialog.class)))
                    .hasMessageContaining("Docked is resolved in a view that a Dock opens");
        }
    }

    @Test
    void savesTheLayoutWithoutTheViewsOpenedThroughAFactoryAndRestoresIt() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<DockApp> app =
                    AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
                Dock dock = app.bean(Dock.class);
                app.run(() -> dock.close(view(app, GammaView.class)));
                app.run(() ->
                        dock.open(NoteViewFactory.class, factory -> factory.create("Note"), "note", Placement.tab()));
                app.run(() -> dock.select(view(app, AlphaView.class)));

                app.commands(StandardCommands.class).exit().execute();
            }
            assertThat(Files.readString(stateFile(fileSystem)))
                    .contains("[windows.main.dock]")
                    .doesNotContain("NoteView");

            try (AppTester<DockApp> app =
                    AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
                AlphaView alpha = view(app, AlphaView.class);
                BetaView beta = view(app, BetaView.class);

                assertThat(layout(app))
                        .contains(new Split(Orientation.HORIZONTAL, DockApp.BETA, tabs(beta), tabs(alpha)));
                assertThat(app.call(() -> app.bean(Dock.class).views())).hasSize(2);
            }
        }
    }

    @Test
    void restoresATreeThatNoPlacementCanExpress() throws IOException {
        String saved = """
                [windows.main.dock]
                split = "horizontal"
                proportion = 0.2

                [windows.main.dock.first]
                tabs = ["be.imgn.swing.docking.AlphaView"]
                selected = 0

                [windows.main.dock.second]
                split = "vertical"
                proportion = 0.6

                [windows.main.dock.second.first]
                split = "horizontal"
                proportion = 0.5

                [windows.main.dock.second.first.first]
                tabs = ["be.imgn.swing.docking.BetaView"]
                selected = 0

                [windows.main.dock.second.first.second]
                tabs = ["be.imgn.swing.docking.GammaView", "be.imgn.swing.docking.VetoView"]
                selected = 1

                [windows.main.dock.second.second]
                tabs = ["be.imgn.swing.docking.NoSuchView"]
                selected = 0
                """;
        try (FileSystem fileSystem = savedState(saved);
                AppTester<DockApp> app =
                        AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
            AlphaView alpha = view(app, AlphaView.class);
            BetaView beta = view(app, BetaView.class);
            GammaView gamma = view(app, GammaView.class);
            VetoView veto = view(app, VetoView.class);

            assertThat(layout(app))
                    .contains(new Split(
                            Orientation.HORIZONTAL,
                            0.2,
                            tabs(alpha),
                            new Split(Orientation.HORIZONTAL, 0.5, tabs(beta), new Tabs(List.of(gamma, veto), veto))));
        }
    }

    @Test
    void laysOutAnewWhenTheSavedLayoutIsInvalid() throws IOException {
        String saved = """
                [windows.main.dock]
                tabs = ["be.imgn.swing.docking.AlphaView", "be.imgn.swing.docking.AlphaView"]
                selected = 0
                """;
        try (FileSystem fileSystem = savedState(saved);
                AppTester<DockApp> app =
                        AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
            assertThat(app.call(() -> app.bean(Dock.class).views())).hasSize(3);
        }
    }

    @Test
    void laysOutAnewWhenNoViewOfTheSavedLayoutIsLeft() throws IOException {
        String saved = """
                [windows.main.dock]
                tabs = ["be.imgn.swing.docking.NoSuchView"]
                selected = 0
                """;
        try (FileSystem fileSystem = savedState(saved);
                AppTester<DockApp> app =
                        AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
            assertThat(app.call(() -> app.bean(Dock.class).views())).hasSize(3);
        }
    }

    @Test
    void leavesOutAViewThatOnlyAFactoryCreates() throws IOException {
        String saved = """
                [windows.main.dock]
                tabs = ["be.imgn.swing.docking.AlphaView", "be.imgn.swing.docking.NoteView"]
                selected = 0
                """;
        try (FileSystem fileSystem = savedState(saved);
                AppTester<DockApp> app =
                        AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
            AlphaView alpha = view(app, AlphaView.class);

            assertThat(layout(app)).contains(tabs(alpha));
        }
    }

    @Test
    void leavesTheDockAsItWasWhenTheBuildOfADockedViewFails() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            Dock dock = app.bean(Dock.class);
            AlphaView alpha = app.call(() -> dock.open(AlphaView.class, Placement.tab()));

            assertThatThrownBy(() -> app.run(() -> dock.open(UnnamedView.class, Placement.of(DockRegion.EAST, 0.5))))
                    .hasMessageContaining("has no accessible name");
            assertThatThrownBy(app::isRunning).isInstanceOf(AssertionError.class);

            assertThat(app.call(() -> dock.views())).containsExactly(alpha);
            assertThat(layout(app)).contains(tabs(alpha));
            assertThat(app.components(JTabbedPane.class)).hasSize(1);
        }
    }

    @Test
    void refusesToBringAnOpenViewToTheFrontDuringAnotherViewsBuild() {
        try (AppTester<DockApp> app = AppTester.start(DockApp.class)) {
            Dock dock = app.bean(Dock.class);

            assertThatThrownBy(() -> app.call(() -> dock.open(ReopeningView.class, Placement.tab())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("being built");
        }
    }

    @Test
    void opensTheViewsOfARestoredLayoutDepthFirst() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            try (AppTester<DockApp> app =
                    AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
                app.commands(StandardCommands.class).exit().execute();
            }
            try (AppTester<DockApp> app =
                    AppTester.builder(DockApp.class).fileSystem(fileSystem).start()) {
                assertThat(app.call(() -> app.bean(Dock.class).views()))
                        .containsExactly(
                                view(app, BetaView.class), view(app, GammaView.class), view(app, AlphaView.class));
            }
        }
    }

    @Test
    void showsATitleTheViewSetsAfterItsBuild() {
        try (AppTester<EmptyDockApp> app = AppTester.start(EmptyDockApp.class)) {
            Dock dock = app.bean(Dock.class);
            AlphaView alpha = app.call(() -> dock.open(AlphaView.class, Placement.tab()));

            app.run(() -> app.bean(alpha, Docked.class).setTitle("Renamed"));

            assertThat(app.call(() -> dock.title(alpha))).isEqualTo("Renamed");
            assertThat(app.call(() -> app.component(JTabbedPane.class).getTitleAt(0)))
                    .isEqualTo("Renamed");
        }
    }
}
