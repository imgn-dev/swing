package be.imgn.swing;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.internal.ResourceState;
import be.imgn.swing.internal.ResourceTables;
import be.imgn.swing.internal.ResourceWatcher;
import be.imgn.swing.spi.MessagePattern;
import be.imgn.swing.spi.MessageSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcesTest {

    private final Map<String, String> files = new HashMap<>(Map.of(
            "Greeting.toml", "hello = \"Hello, {name}.\"\n",
            "Greeting_fr.toml", "hello = \"Bonjour, {name}.\"\n"));
    private final ResourceState state = new ResourceState(new DisplayLocale(Locale.ROOT));
    private final Resources resources =
            new Resources(new ResourceTables(Map.of("be.imgn.swing", opener()), Optional.empty()), state);

    private ResourceOpener opener() {
        return new ResourceOpener() {
            @Override
            public String packageName() {
                return "be.imgn.swing";
            }

            @Override
            public Optional<InputStream> open(String fileName) {
                return Optional.ofNullable(files.get(fileName))
                        .map(text -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public Set<String> icons() {
                return Set.of();
            }
        };
    }

    @Test
    void readsTheFilesOfTheCurrentLocale() {
        assertThat(resources.table(Greeting.class).getString("hello")).isEqualTo("Hello, {name}.");
        assertThat(resources.locale()).isEqualTo(Locale.ROOT);

        state.locale(Locale.FRENCH);

        assertThat(resources.table(Greeting.class).getString("hello")).isEqualTo("Bonjour, {name}.");
        assertThat(resources.locale()).isEqualTo(Locale.FRENCH);
    }

    @Test
    void cachesATableUntilTheLocaleChanges() {
        assertThat(resources.table(Greeting.class)).isSameAs(resources.table(Greeting.class));
        files.put("Greeting.toml", "hello = \"Hi, {name}.\"\n");

        assertThat(resources.table(Greeting.class).getString("hello")).isEqualTo("Hello, {name}.");
        state.locale(Locale.FRENCH);
        state.locale(Locale.ROOT);
        assertThat(resources.table(Greeting.class).getString("hello")).isEqualTo("Hi, {name}.");
    }

    @Test
    void readsTheFilesOfAGivenLocaleWhateverTheDisplayLocale() {
        assertThat(resources.table(Greeting.class, Locale.FRENCH).getString("hello"))
                .isEqualTo("Bonjour, {name}.");
        assertThat(resources.table(Greeting.class).getString("hello")).isEqualTo("Hello, {name}.");
    }

    @Test
    void readsThePatternsOfMessagesAgainOnlyWhenTheirTableChanges() {
        AtomicInteger reads = new AtomicInteger();
        MessageSource<MessagePattern> source = MessageSource.following(resources, Greeting.class, (table, locale) -> {
            reads.incrementAndGet();
            return MessagePattern.of(table, "hello", List.of("name"), locale);
        });

        assertThat(source.get().format("Ann")).isEqualTo("Hello, Ann.");
        assertThat(source.get().format("Bob")).isEqualTo("Hello, Bob.");
        assertThat(reads).hasValue(1);

        state.locale(Locale.FRENCH);

        assertThat(source.get().format("Ann")).isEqualTo("Bonjour, Ann.");
        assertThat(reads).hasValue(2);
    }

    @Test
    void keepsTheLocaleOfAFixedSource() {
        MessageSource<MessagePattern> source = MessageSource.fixed(
                resources,
                Greeting.class,
                Locale.FRENCH,
                (table, locale) -> MessagePattern.of(table, "hello", List.of("name"), locale));

        assertThat(source.get().format("Ann")).isEqualTo("Bonjour, Ann.");
        state.locale(Locale.ENGLISH);
        assertThat(source.get().format("Ann")).isEqualTo("Bonjour, Ann.");
    }

    @Test
    void readsTheNewContentOfAFileAfterAReload(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("Greeting.toml"), "hello = \"Hello, {name}.\"\n");
        ResourceOpener watched = new ResourceOpener() {
            @Override
            public String packageName() {
                return "be.imgn.swing";
            }

            @Override
            public Optional<InputStream> open(String fileName) {
                try {
                    return Optional.of(Files.newInputStream(directory.resolve(fileName)));
                } catch (IOException e) {
                    return Optional.empty();
                }
            }

            @Override
            public Set<String> icons() {
                return Set.of();
            }

            @Override
            public Optional<Path> directory() {
                return Optional.of(directory);
            }
        };
        Resources reloaded =
                new Resources(new ResourceTables(Map.of("be.imgn.swing", watched), Optional.empty()), state);
        MessageSource<MessagePattern> source = MessageSource.following(
                reloaded,
                Greeting.class,
                (table, locale) -> MessagePattern.of(table, "hello", List.of("name"), locale));
        assertThat(source.get().format("Ann")).isEqualTo("Hello, Ann.");
        ResourceWatcher watcher = new ResourceWatcher(List.of(watched), state, () -> {}, new UiExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }

            @Override
            public boolean isUiThread() {
                return true;
            }
        });
        Files.writeString(directory.resolve("Greeting.toml"), "hello = \"Hi there, {name}.\"\n");

        watcher.poll();
        watcher.poll();

        assertThat(reloaded.table(Greeting.class).getString("hello")).isEqualTo("Hi there, {name}.");
        assertThat(source.get().format("Ann")).isEqualTo("Hi there, Ann.");
    }

    private interface Greeting {}
}
