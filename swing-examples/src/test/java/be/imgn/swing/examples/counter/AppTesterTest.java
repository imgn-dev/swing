package be.imgn.swing.examples.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Arguments;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.Task;
import be.imgn.swing.TaskContext;
import be.imgn.swing.TaskService;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.DynamicEntry;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.Ui;
import java.awt.EventQueue;
import java.nio.file.FileSystems;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/** What the tester checks of its own, on the counter. */
class AppTesterTest {

    @Test
    void givesTheArgumentsToTheApplication() {
        try (AppTester<CounterApp> app = AppTester.builder(CounterApp.class)
                .arguments("--file", "notes.txt")
                .start()) {
            assertThat(app.commands(Arguments.class).values()).containsExactly("--file", "notes.txt");
        }
    }

    @Test
    void bindsItsPlatformInPlaceOfTheDefaultAndTheDirectoriesFollowIt() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Platform platform = app.commands(Platform.class);
            ApplicationDirectories directories = app.commands(ApplicationDirectories.class);
            ApplicationInfo info = app.commands(ApplicationInfo.class);

            assertThat(platform.isMacOS()).isFalse();
            assertThat(directories.data().getFileSystem()).isNotEqualTo(FileSystems.getDefault());
            assertThat(directories.data()).hasToString("/data-home/counter");
            assertThat(info.platform()).isSameAs(platform);
            assertThat(info.directories()).isSameAs(directories);
        }
    }

    @Test
    void runsNothingForTheTaskWithNoWork() throws Exception {
        List<String> hooks = new ArrayList<>();
        Task<String> none = Task.<String>none().onSuccess(hooks::add).onCancel(() -> hooks.add("cancelled"));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Scope scope = app.commands(Scope.class);
            app.commands(TaskService.class).run(none, scope);
            app.commands(TaskService.class).run(none, scope);
            Ui ui = app.commands(Ui.class);
            EventQueue.invokeAndWait(() -> ui.run(none, Block.WINDOW));

            assertThat(none).isSameAs(Task.none());
            assertThat(hooks).isEmpty();
            assertThat(app.isRunning()).isTrue();
        }
    }

    @Test
    void givesNoArgumentsByDefault() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            assertThat(app.commands(Arguments.class).values()).isEmpty();
        }
    }

    @Test
    void combinesTheArgumentsWithALocale() {
        try (AppTester<CounterApp> app = AppTester.builder(CounterApp.class)
                .locale(Locale.FRENCH)
                .arguments("one")
                .start()) {
            assertThat(app.commands(Arguments.class).values()).containsExactly("one");
            assertThat(app.component(JLabel.class).getText()).startsWith("Le compte est");
        }
    }

    @Test
    void duplicateMnemonicsInARebuiltDynamicSectionFailTheNextCall() throws Exception {
        Property<List<DynamicEntry>> entries = Property.of(List.of(DynamicEntry.item("&Alpha", () -> {})));
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Ui ui = app.commands(Ui.class);
            JLabel label = app.component(JLabel.class);
            EventQueue.invokeAndWait(() -> ui.popupMenu(label, MenuEntry.dynamic(entries)));
            assertThat(app.isRunning()).isTrue();

            entries.set(List.of(DynamicEntry.item("&Alpha", () -> {}), DynamicEntry.item("&Apple", () -> {})));

            assertThatThrownBy(app::isRunning)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Duplicate mnemonic")
                    .hasMessageContaining("Alpha and Apple");
        }
    }

    @Test
    void closingCancelsTheTasksOfTheApplicationScope() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            Scope application = app.commands(Scope.class);
            while (application.parent().isPresent()) {
                application = application.parent().orElseThrow();
            }
            // Work that returns once cancelled succeeds; ending by CancellationException runs onCancel.
            Task<?> task = Task.of((TaskContext context) -> {
                        while (!context.isCancelled()) {
                            Thread.sleep(Duration.ofMillis(10));
                        }
                        throw new CancellationException();
                    })
                    .onCancel(cancelled::countDown);

            app.commands(TaskService.class).run(task, application);

            assertThat(application.isClosed()).isFalse();
        }

        assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
