package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.inject.Injector;
import be.imgn.swing.DisplayLocale;
import be.imgn.swing.Resources;
import be.imgn.swing.ShutdownStep;
import be.imgn.swing.StartupStep;
import be.imgn.swing.UiExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Run on the class path, where the service loader finds the binder of SavedLocale in META-INF/services.
class DefaultLocalesTest {

    @TempDir
    Path config;

    private final DisposalScope application = DisposalScope.root("application");
    private final Injector root = TestInjectors.root(application);
    private final ResourceState state = root.getInstance(ResourceState.class);
    private final List<String> events = new ArrayList<>();
    private final List<Runnable> posted = new ArrayList<>();
    private final UiExecutor executor = new UiExecutor() {
        @Override
        public void execute(Runnable task) {
            posted.add(task);
        }

        @Override
        public boolean isUiThread() {
            return true;
        }
    };

    @AfterEach
    void close() {
        root.close();
        application.close();
    }

    private DefaultLocales locales() {
        LocaleFile file = new LocaleFile(config.resolve("locale.toml"), write -> {
            events.add("save");
            try {
                write.run();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
        DefaultLocales locales = new DefaultLocales(
                new DisplayLocale(Locale.ENGLISH),
                state,
                root.getInstance(Resources.class),
                executor,
                () -> events.add("refresh in " + state.snapshot().locale()),
                file);
        locales.current().subscribe(application, locale -> events.add("current " + locale));
        return locales;
    }

    private void runPosted() {
        List<Runnable> tasks = List.copyOf(posted);
        posted.clear();
        tasks.forEach(Runnable::run);
    }

    @Test
    void switchesTheResourcesAndRefreshesBeforeTheCurrentLocaleChanges() {
        DefaultLocales locales = locales();

        locales.set(Locale.FRENCH);
        assertThat(events).isEmpty();
        runPosted();

        assertThat(events).containsExactly("refresh in fr", "current fr", "save");
        assertThat(locales.current().get()).isEqualTo(Locale.FRENCH);
        assertThat(config.resolve("locale.toml")).content().contains("locale = \"fr\"");
    }

    @Test
    void doesNothingForTheCurrentLocale() {
        DefaultLocales locales = locales();

        locales.set(Locale.ENGLISH);
        runPosted();

        assertThat(events).isEmpty();
        assertThat(config.resolve("locale.toml")).doesNotExist();
    }

    @Test
    void resetsToTheLaunchLocaleAndDeletesTheChoice() {
        DefaultLocales locales = locales();
        locales.set(Locale.GERMAN);
        runPosted();
        events.clear();

        locales.reset();
        runPosted();

        assertThat(events).containsExactly("refresh in en", "current en", "save");
        assertThat(config.resolve("locale.toml")).doesNotExist();
    }

    @Test
    void startsWithTheSavedChoice() throws IOException {
        Files.writeString(config.resolve("locale.toml"), "locale = \"fr-BE\"\n");
        DefaultLocales locales = locales();

        locales.load();

        assertThat(locales.current().get()).isEqualTo(Locale.of("fr", "BE"));
        assertThat(state.snapshot().locale()).isEqualTo(Locale.of("fr", "BE"));
    }

    @Test
    void rejectsANullLocale() {
        DefaultLocales locales = locales();
        // Through a list, which NullAway doesn't check, to reach the method's own check.
        List<Locale> nulls = new ArrayList<>();
        nulls.add(null);

        assertThatNullPointerException()
                .isThrownBy(() -> locales.set(nulls.getFirst()))
                .withMessage("locale");
    }

    @Test
    void givesMessagesInALocaleWhateverTheDisplayLocale() {
        DefaultLocales locales = locales();

        Greetings french = locales.messages(Greetings.class, Locale.FRENCH);

        assertThat(french.hello("Ann")).isEqualTo("Bonjour, Ann.");
        assertThat(locales.messages(Greetings.class, Locale.ROOT).hello("Ann")).isEqualTo("Hello, Ann.");
        assertThat(locales.messages(Greetings.class, Locale.FRENCH)).isSameAs(french);
    }

    @Test
    void readsTheChoiceBeforeTheResourcesAndWritesItAfterTheWindows() {
        assertThat(Contributions.sorted(root, StartupStep.class, StartupStep::order))
                .filteredOn(step -> step instanceof LocaleStep || step instanceof ResourcesStep)
                .extracting(StartupStep::order)
                .containsExactly(50, 100);
        assertThat(Contributions.of(root, ShutdownStep.class))
                .filteredOn(SaveLocaleStep.class::isInstance)
                .extracting(ShutdownStep::order)
                .containsExactly(330);
    }

    @Test
    void rejectsATypeThatIsNotCompiledMessages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> locales().messages(Runnable.class, Locale.FRENCH))
                .withMessageContaining("java.lang.Runnable is not a @Messages interface");
    }
}
