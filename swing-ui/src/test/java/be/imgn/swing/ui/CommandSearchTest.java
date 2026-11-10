package be.imgn.swing.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandSearchTest {

    private static int rank(String text, String query) {
        return CommandSearch.rank(CommandSearch.normalize(text), CommandSearch.normalize(query));
    }

    @Test
    void ranksAPrefixThenAWordThenASubstringThenASubsequence() {
        assertThat(rank("Save As…", "sav")).isZero();
        assertThat(rank("Quick Save", "sav")).isEqualTo(1);
        assertThat(rank("Unsaved", "sav")).isEqualTo(2);
        assertThat(rank("Select All", "sal")).isEqualTo(3);
        assertThat(rank("Copy", "sav")).isEqualTo(CommandSearch.NO_MATCH);
    }

    @Test
    void ignoresCaseAndAccents() {
        assertThat(rank("Rétablir", "RETA")).isZero();
        assertThat(rank("  Copier  ", "cop")).isZero();
    }
}
