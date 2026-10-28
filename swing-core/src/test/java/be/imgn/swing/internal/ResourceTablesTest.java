package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.swing.ResourceOpener;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlTable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceTablesTest {

    private static ResourceOpener opener(Map<String, String> files) {
        return new ResourceOpener() {
            @Override
            public String packageName() {
                return "be.imgn.swing.internal";
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
    void namesTheFilesInMergeOrder() {
        ResourceTables tables = new ResourceTables(Map.of(), Optional.of("macos"));

        assertThat(tables.fileNames("NotesView", Locale.of("fr", "BE")))
                .containsExactly(
                        "NotesView.toml",
                        "NotesView.macos.toml",
                        "NotesView_fr.toml",
                        "NotesView_fr.macos.toml",
                        "NotesView_fr_BE.toml",
                        "NotesView_fr_BE.macos.toml");
        assertThat(new ResourceTables(Map.of(), Optional.empty()).fileNames("NotesView", Locale.ROOT))
                .containsExactly("NotesView.toml");
    }

    @Test
    void mergesAlongTheHierarchyAndTheVariants() {
        ResourceOpener opener = opener(Map.of(
                "Base.toml", "[editor]\nfont = \"Serif-12\"\ntext = \"base\"\n",
                "NotesView.toml", "title = \"Notes\"\n[editor]\ntext = \"notes\"\n",
                "NotesView.linux.toml", "[editor]\nrows = 4\n",
                "NotesView_fr.toml", "title = \"Notes (fr)\"\n"));
        ResourceTables tables = new ResourceTables(Map.of(opener.packageName(), opener), Optional.of("linux"));

        TomlTable merged = tables.merged(NotesView.class, Locale.FRENCH);

        assertThat(merged.getString("title")).isEqualTo("Notes (fr)");
        assertThat(merged.getString("editor.font")).isEqualTo("Serif-12");
        assertThat(merged.getString("editor.text")).isEqualTo("notes");
        assertThat(merged.getLong("editor.rows")).isEqualTo(4);
    }

    @Test
    void resolvesReferences() {
        TomlTable table = Toml.parse("""
                name = "Notes"
                size = 12
                title = "${name} (${size})"
                [about]
                text = "About ${title}"
                """).root();

        TomlTable resolved = ResourceTables.resolve(table);

        assertThat(resolved.getString("about.text")).isEqualTo("About Notes (12)");
    }

    @Test
    void rejectsUnknownAndCyclicReferences() {
        assertThatIllegalStateException()
                .isThrownBy(
                        () -> ResourceTables.resolve(Toml.parse("a = \"${b}\"").root()))
                .withMessageContaining("${b}");
        assertThatIllegalStateException()
                .isThrownBy(
                        () -> ResourceTables.resolve(Toml.parse("a = \"${a}\"").root()))
                .withMessageContaining("itself");
    }

    static class Base {}

    static class NotesView extends Base {}
}
