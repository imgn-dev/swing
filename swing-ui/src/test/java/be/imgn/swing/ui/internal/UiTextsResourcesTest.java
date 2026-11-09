package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.io.ByteSource;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

/** The framework's own texts: every locale it ships has every key, and the About dialog's lines are patterns. */
class UiTextsResourcesTest {

    private static final List<String> FILES = List.of("UiTexts.toml", "UiTexts_fr.toml");
    private static final List<String> PATTERNS = List.of("version", "by", "replace", "saveChanges");

    @Test
    void everyLocaleHasEveryKey() throws IOException {
        TomlTable base = read(FILES.getFirst());
        for (String file : FILES) {
            assertThat(read(file).entries().keySet())
                    .as(file)
                    .isEqualTo(base.entries().keySet());
        }
    }

    @Test
    void theAboutDialogLinesArePatternsWithOneArgument() throws IOException {
        for (String file : FILES) {
            TomlTable texts = read(file);
            for (String key : PATTERNS) {
                String pattern = ((TomlString) texts.get(key).orElseThrow()).value();
                assertThat(new MessageFormat(pattern, Locale.ROOT).format(new Object[] {"X"}))
                        .as("%s in %s", key, file)
                        .contains("X")
                        .doesNotContain("{0}");
            }
        }
    }

    // The real save prompt's buttons, built from each locale's texts, have distinct mnemonics, and none on macOS.
    @Test
    void theSavePromptsButtonsHaveDistinctMnemonics() throws IOException {
        for (String file : FILES) {
            TomlTable texts = read(file);
            List<String> marked = Stream.of("save", "discard", "cancel")
                    .map(key -> ((TomlString) texts.get(key).orElseThrow()).value())
                    .toList();

            List<Integer> mnemonics = Arrays.stream(SwingDialogs.saveButtons(marked, false))
                    .map(JButton::getMnemonic)
                    .filter(mnemonic -> mnemonic != 0)
                    .toList();
            List<Integer> onMacOS = Arrays.stream(SwingDialogs.saveButtons(marked, true))
                    .map(JButton::getMnemonic)
                    .filter(mnemonic -> mnemonic != 0)
                    .toList();

            assertThat(mnemonics).as(file).hasSize(2).doesNotHaveDuplicates();
            assertThat(onMacOS).as(file).isEmpty();
        }
    }

    private static TomlTable read(String file) throws IOException {
        try (InputStream in = UiTextsResourcesTest.class.getResourceAsStream("/be/imgn/swing/ui/internal/" + file)) {
            assertThat(in).as(file).isNotNull();
            return Toml.parse(ByteSource.wrap(in.readAllBytes())).root();
        }
    }
}
