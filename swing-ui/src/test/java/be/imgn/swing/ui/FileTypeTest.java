package be.imgn.swing.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileTypeTest {

    @Test
    void copiesItsExtensions() {
        List<String> extensions = new ArrayList<>(List.of("txt"));
        FileType type = new FileType("Text files", extensions);

        extensions.add("md");

        assertThat(type.extensions()).containsExactly("txt");
    }

    @Test
    void rejectsNoExtensionABlankOneAndOneWithADot() {
        assertThatThrownBy(() -> new FileType("Text", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileType("Text", List.of(" "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileType("Text", List.of(".txt"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullExtension() {
        assertThatThrownBy(() -> new FileType("Text", Arrays.asList("txt", null)))
                .isInstanceOf(NullPointerException.class);
    }
}
