package be.imgn.swing.ui;

import be.imgn.swing.internal.ResourceTables;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.util.Optional;

/**
 * The resources of a command or a menu: its table, merged from the framework's and the class's.
 *
 * @param table the merged table
 */
record CommandResources(TomlTable table) {

    static CommandResources of(TomlTable... tables) {
        TomlTable merged = TomlTable.builder().build();
        for (TomlTable table : tables) {
            merged = ResourceTables.merge(merged, table);
        }
        return new CommandResources(merged);
    }

    static TomlTable section(TomlTable resources, String kind, String id) {
        return resources.entries().get(kind) instanceof TomlTable all
                        && all.entries().get(id) instanceof TomlTable table
                ? table
                : TomlTable.builder().build();
    }

    Optional<String> string(String key) {
        return table.entries().get(key) instanceof TomlString string ? Optional.of(string.value()) : Optional.empty();
    }
}
