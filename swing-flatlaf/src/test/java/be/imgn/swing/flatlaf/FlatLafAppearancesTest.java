package be.imgn.swing.flatlaf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Injector;
import be.imgn.swing.Appearance;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.Subscription;
import be.imgn.swing.internal.DefaultApplicationInfo;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.Launcher;
import be.imgn.swing.internal.ResourceState;
import be.imgn.swing.internal.SwitchableAppearances;
import be.imgn.swing.spi.ApplicationDescriptor;
import be.imgn.swing.ui.internal.Edt;
import be.imgn.toml.Toml;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlatLafAppearancesTest {

    private final DisposalScope application = DisposalScope.root("application");
    private final Injector root = root();
    private final AtomicBoolean dark = new AtomicBoolean();
    private final AtomicInteger commands = new AtomicInteger();
    private final List<Runnable> reads = new ArrayList<>();
    private final List<Runnable> periodic = new ArrayList<>();
    private final List<Duration> periods = new ArrayList<>();
    private final List<String> refreshes = new ArrayList<>();

    private Injector root() {
        ApplicationDescriptor descriptor = new ApplicationDescriptor() {
            @Override
            public Class<?> type() {
                return FlatLafAppearancesTest.class;
            }

            @Override
            public String name() {
                return "Test";
            }

            @Override
            public Optional<String> vendor() {
                return Optional.empty();
            }

            @Override
            public Optional<String> qualifier() {
                return Optional.empty();
            }

            @Override
            public Optional<String> version() {
                return Optional.empty();
            }
        };
        DefaultApplicationInfo info = new DefaultApplicationInfo(descriptor);
        return Launcher.build(Launcher.rootBuilder(info, List.of(), Locale.ROOT, application), info);
    }

    @AfterEach
    void close() {
        root.close();
        application.close();
    }

    // Reads "Dark" or "Light" the way each system's command answers.
    private DarkMode darkMode() {
        return new DarkMode(command -> {
            commands.incrementAndGet();
            if (command.getFirst().equals("reg")) {
                return Optional.of("AppsUseLightTheme    REG_DWORD    " + (dark.get() ? "0x0" : "0x1"));
            }
            return Optional.of(dark.get() ? "Dark" : "'default'");
        });
    }

    private FlatLafAppearances appearances(String theme) {
        ResourceState state = root.getInstance(ResourceState.class);
        FlatLafInstaller installer = new FlatLafInstaller(
                root.getInstance(ApplicationInfo.class),
                Platform.current(),
                root.getInstance(Resources.class),
                darkMode(),
                property -> property.equals(Theme.PROPERTY) ? theme : null);
        return new FlatLafAppearances(
                installer,
                new SwitchableAppearances(
                        Appearance.LIGHT,
                        state,
                        () -> refreshes.add("refresh in " + state.snapshot().appearance())),
                Runnable::run,
                reads::add,
                (period, action) -> {
                    periods.add(period);
                    periodic.add(action);
                    Subscription subscription = () -> periodic.remove(action);
                    return subscription;
                },
                () -> {},
                application);
    }

    private void runReads() {
        List<Runnable> pending = List.copyOf(reads);
        reads.clear();
        pending.forEach(read -> Edt.invokeAndWait(read));
    }

    @Test
    void startsInTheSystemsSettingAndFollowsItEveryTenSeconds() {
        dark.set(true);
        FlatLafAppearances appearances = appearances("system");

        appearances.start();

        assertThat(appearances.current().get()).isEqualTo(Appearance.DARK);
        assertThat(periods).containsExactly(Duration.ofSeconds(10));

        dark.set(false);
        periodic.getFirst().run();
        runReads();

        assertThat(appearances.current().get()).isEqualTo(Appearance.LIGHT);
        assertThat(refreshes).containsExactly("refresh in LIGHT");
        assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("FlatLaf Light");
    }

    @Test
    void dropsAReadAskedForWhileOneRuns() {
        FlatLafAppearances appearances = appearances("system");
        appearances.start();
        int started = commands.get();

        appearances.readAgain();
        appearances.readAgain();

        assertThat(reads).hasSize(1);
        runReads();
        assertThat(commands.get()).isEqualTo(started + 1);
        appearances.readAgain();
        assertThat(reads).hasSize(1);
    }

    @Test
    void readsNothingWithAFixedTheme() {
        dark.set(true);
        FlatLafAppearances appearances = appearances("light");

        appearances.start();

        assertThat(appearances.current().get()).isEqualTo(Appearance.LIGHT);
        assertThat(periods).isEmpty();
        assertThat(commands).hasValue(0);
    }

    @Test
    void givesTheTokensAsTheStringsOfFlatLafsPropertiesFiles() {
        Map<String, String> tokens = FlatLafInstaller.tokens(Toml.parse("""
                        [flatlaf]
                        "@accentColor" = "#1a73e8"
                        "Component.arc" = 8
                        "Button.borderless" = true
                        """).root(), FlatLafAppearancesTest.class);

        assertThat(tokens)
                .containsExactly(
                        Map.entry("@accentColor", "#1a73e8"),
                        Map.entry("Component.arc", "8"),
                        Map.entry("Button.borderless", "true"));
    }
}
