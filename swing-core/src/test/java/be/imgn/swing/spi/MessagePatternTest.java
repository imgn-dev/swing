package be.imgn.swing.spi;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.toml.Toml;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MessagePatternTest {

    @Test
    void rewritesNamesToIndices() {
        assertThat(MessagePattern.rewrite("{b} and {a,number,integer}", List.of("a", "b")))
                .isEqualTo("{1} and {0,number,integer}");
    }

    @Test
    void leavesQuotedSectionsAlone() {
        assertThat(MessagePattern.rewrite("'{a}' {a} n''existe pas", List.of("a")))
                .isEqualTo("'{a}' {0} n''existe pas");
    }

    @Test
    void formatsNestedChoices() {
        MessagePattern pattern = MessagePattern.of(
                Toml.parse("words = \"{count,choice,0#no words|1#one word|1<{count,number,integer} words}\"")
                        .root(),
                "words",
                List.of("count"),
                Locale.ROOT);

        assertThat(pattern.format(0)).isEqualTo("no words");
        assertThat(pattern.format(1)).isEqualTo("one word");
        assertThat(pattern.format(3)).isEqualTo("3 words");
    }
}
