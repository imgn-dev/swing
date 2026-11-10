package be.imgn.swing.examples.frames;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.Frames;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.StandardMenus;
import be.imgn.swing.ui.Window;
import java.util.Arrays;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;

class WindowMenuTest {

    private static JMenu windowMenu(AppTester<BoardApp> app) {
        return app.call(() -> app.bean(StandardMenus.class)
                .window()
                .with(app.commands(StandardCommands.class).openWindows())
                .createMenu());
    }

    private static List<String> items(AppTester<BoardApp> app, JMenu menu) {
        return app.call(() -> Arrays.stream(menu.getMenuComponents())
                .filter(JMenuItem.class::isInstance)
                .map(item -> ((JMenuItem) item).getText())
                .toList());
    }

    @Test
    void listsTheOpenFramesByTitleAndFollowsThem() {
        try (AppTester<BoardApp> app =
                AppTester.builder(BoardApp.class).arguments("2").start()) {
            JMenu menu = windowMenu(app);
            assertThat(items(app, menu)).containsExactly("1 Board", "2 Board");

            app.run(() -> app.bean(Frames.class).open(BoardView.class));
            assertThat(items(app, menu)).containsExactly("1 Board", "2 Board", "3 Board");

            app.run(() -> app.bean(Window.class).setTitle("Plans & Notes"));
            assertThat(items(app, menu)).containsExactly("1 Board", "2 Board", "3 Plans & Notes");

            app.frames().getLast().close();
            assertThat(items(app, menu)).containsExactly("1 Board", "2 Board");
        }
    }

    @Test
    void bringsTheChosenFrameToTheFront() {
        try (AppTester<BoardApp> app =
                AppTester.builder(BoardApp.class).arguments("2").start()) {
            JMenu menu = windowMenu(app);
            int before = app.frames().getFirst().toFrontCount();

            app.run(() -> ((JMenuItem) menu.getMenuComponent(0)).doClick());

            assertThat(app.frames().getFirst().toFrontCount()).isEqualTo(before + 1);
        }
    }
}
