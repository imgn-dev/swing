package be.imgn.swing.ui;

import be.imgn.swing.internal.ResourceTables;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.util.Map;
import java.util.Optional;

/**
 * The resources of a command or a menu: its table, merged from the framework's and the class's.
 *
 * @param table the merged table
 * @param iconPackage the package the icon's path is relative to: that of the class whose table gives it
 */
record CommandResources(TomlTable table, String iconPackage) {

    /**
     * Merges the tables of a command, each given with the class whose resources hold it.
     *
     * @param tables the tables, by class, in merge order
     * @return the resources
     */
    static CommandResources of(Map<Class<?>, TomlTable> tables) {
        TomlTable merged = TomlTable.builder().build();
        String iconPackage = "";
        for (Map.Entry<Class<?>, TomlTable> table : tables.entrySet()) {
            merged = ResourceTables.merge(merged, table.getValue());
            if (table.getValue().entries().containsKey("icon")) {
                iconPackage = table.getKey().getPackageName();
            }
        }
        return new CommandResources(merged, iconPackage);
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
