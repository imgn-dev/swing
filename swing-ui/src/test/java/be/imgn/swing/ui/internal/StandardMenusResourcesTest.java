package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.io.ByteSource;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The framework's standard menus, in every locale it ships, have distinct mnemonics. */
class StandardMenusResourcesTest {

    private static final List<String> FILES = List.of("StandardMenus.toml", "StandardMenus_fr.toml");

    @Test
    void theStandardMenusHaveDistinctMnemonicsInEveryLocale() throws IOException {
        for (String file : FILES) {
            TomlTable menus = (TomlTable) read(file).get("menus").orElseThrow();
            Map<Character, String> byMnemonic = new HashMap<>();
            menus.entries().forEach((id, value) -> {
                String text = ((TomlString) ((TomlTable) value).get("text").orElseThrow()).value();
                Mnemonics mnemonics = Mnemonics.parse(text);
                assertThat(mnemonics.hasMnemonic())
                        .as("The menu %s has a mnemonic in %s", id, file)
                        .isTrue();
                String previous = byMnemonic.put(mnemonics.character(), id);
                assertThat(previous)
                        .as("%s and %s share the mnemonic %s in %s", previous, id, mnemonics.character(), file)
                        .isNull();
            });
        }
    }

    private static TomlTable read(String file) throws IOException {
        try (InputStream in = StandardMenusResourcesTest.class.getResourceAsStream("/be/imgn/swing/ui/" + file)) {
            assertThat(in).as(file).isNotNull();
            return Toml.parse(ByteSource.wrap(in.readAllBytes())).root();
        }
    }
}
