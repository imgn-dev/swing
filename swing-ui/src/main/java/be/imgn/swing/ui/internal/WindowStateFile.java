package be.imgn.swing.ui.internal;

import be.imgn.toml.TomlArray;
import be.imgn.toml.TomlBoolean;
import be.imgn.toml.TomlInteger;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Converts the window state to and from {@code window-state.toml}, by hand: toml has no writer from records. Unknown
 * keys are rejected.
 */
public final class WindowStateFile {

    private static final String WINDOWS = "windows";
    private static final String BOUNDS = "bounds";
    private static final String MAXIMIZED = "maximized";
    private static final String COMPONENTS = "components";
    private static final String DIVIDER_LOCATION = "divider-location";
    private static final String SELECTED_INDEX = "selected-index";
    private static final String COLUMNS = "columns";
    private static final String MODEL_INDEX = "model-index";
    private static final String WIDTH = "width";
    private static final int BOUNDS_SIZE = 4;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int WIDTH_INDEX = 2;
    private static final int HEIGHT_INDEX = 3;

    private WindowStateFile() {}

    /**
     * Reads the state of the windows from a file's table.
     *
     * @param root the root table of the file
     * @return the state of each window, by key
     * @throws IllegalArgumentException if the table does not bind: an unknown key, or a value of the wrong type
     */
    public static Map<String, SavedWindow> read(TomlTable root) {
        keys(root, Set.of(WINDOWS), "the file");
        Map<String, SavedWindow> windows = new LinkedHashMap<>();
        TomlValue all = root.entries().get(WINDOWS);
        if (all != null) {
            table(all, WINDOWS).entries().forEach((key, window) -> windows.put(key, window(table(window, key), key)));
        }
        return windows;
    }

    private static SavedWindow window(TomlTable table, String key) {
        keys(table, Set.of(BOUNDS, MAXIMIZED, COMPONENTS), key);
        Optional<Rectangle> bounds =
                Optional.ofNullable(table.entries().get(BOUNDS)).map(value -> bounds(value, key));
        TomlValue maximizedValue = table.entries().get(MAXIMIZED);
        boolean maximized = maximizedValue != null && bool(maximizedValue, MAXIMIZED);
        Map<String, SavedComponent> components = new LinkedHashMap<>();
        TomlValue componentsValue = table.entries().get(COMPONENTS);
        if (componentsValue != null) {
            table(componentsValue, COMPONENTS)
                    .entries()
                    .forEach((field, component) -> components.put(field, component(table(component, field), field)));
        }
        return new SavedWindow(bounds, maximized, components);
    }

    private static Rectangle bounds(TomlValue value, String key) {
        if (!(value instanceof TomlArray array) || array.values().size() != BOUNDS_SIZE) {
            throw new IllegalArgumentException("The bounds of " + key + " are not 4 integers");
        }
        List<Integer> values =
                array.values().stream().map(element -> integer(element, BOUNDS)).toList();
        return new Rectangle(values.get(X), values.get(Y), values.get(WIDTH_INDEX), values.get(HEIGHT_INDEX));
    }

    private static SavedComponent component(TomlTable table, String field) {
        keys(table, Set.of(DIVIDER_LOCATION, SELECTED_INDEX, COLUMNS), field);
        OptionalInt divider = optionalInt(table, DIVIDER_LOCATION);
        OptionalInt selected = optionalInt(table, SELECTED_INDEX);
        Optional<List<SavedComponent.Column>> columns = Optional.empty();
        TomlValue columnsValue = table.entries().get(COLUMNS);
        if (columnsValue != null) {
            if (!(columnsValue instanceof TomlArray array)) {
                throw new IllegalArgumentException("The columns of " + field + " are not an array");
            }
            List<SavedComponent.Column> list = new ArrayList<>();
            for (TomlValue element : array.values()) {
                TomlTable column = table(element, COLUMNS);
                keys(column, Set.of(MODEL_INDEX, WIDTH), COLUMNS);
                list.add(new SavedComponent.Column(
                        integer(required(column, MODEL_INDEX), MODEL_INDEX), integer(required(column, WIDTH), WIDTH)));
            }
            columns = Optional.of(list);
        }
        return new SavedComponent(divider, selected, columns);
    }

    private static OptionalInt optionalInt(TomlTable table, String key) {
        TomlValue value = table.entries().get(key);
        return value == null ? OptionalInt.empty() : OptionalInt.of(integer(value, key));
    }

    private static TomlValue required(TomlTable table, String key) {
        TomlValue value = table.entries().get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value;
    }

    private static void keys(TomlTable table, Set<String> allowed, String where) {
        for (String key : table.entries().keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown key " + key + " in " + where);
            }
        }
    }

    private static TomlTable table(TomlValue value, String key) {
        if (value instanceof TomlTable table) {
            return table;
        }
        throw new IllegalArgumentException(key + " is not a table");
    }

    private static int integer(TomlValue value, String key) {
        if (value instanceof TomlInteger integer) {
            return Math.toIntExact(integer.value());
        }
        throw new IllegalArgumentException(key + " is not an integer");
    }

    private static boolean bool(TomlValue value, String key) {
        if (value instanceof TomlBoolean bool) {
            return bool.value();
        }
        throw new IllegalArgumentException(key + " is not a boolean");
    }

    /**
     * Writes the state of the windows to a table.
     *
     * @param windows the state of each window, by key
     * @return the root table of the file
     */
    public static TomlTable write(Map<String, SavedWindow> windows) {
        TomlTable.Builder all = TomlTable.builder();
        windows.forEach((key, window) -> all.put(key, writeWindow(window)));
        return TomlTable.builder().put(WINDOWS, all.build()).build();
    }

    private static TomlTable writeWindow(SavedWindow window) {
        TomlTable.Builder table = TomlTable.builder();
        window.bounds()
                .ifPresent(bounds -> table.put(
                        BOUNDS,
                        TomlArray.of(
                                new TomlInteger(bounds.x),
                                new TomlInteger(bounds.y),
                                new TomlInteger(bounds.width),
                                new TomlInteger(bounds.height))));
        if (window.maximized()) {
            table.put(MAXIMIZED, true);
        }
        if (!window.components().isEmpty()) {
            TomlTable.Builder components = TomlTable.builder();
            window.components().forEach((field, component) -> components.put(field, writeComponent(component)));
            table.put(COMPONENTS, components.build());
        }
        return table.build();
    }

    private static TomlTable writeComponent(SavedComponent component) {
        TomlTable.Builder table = TomlTable.builder();
        component.dividerLocation().ifPresent(location -> table.put(DIVIDER_LOCATION, location));
        component.selectedIndex().ifPresent(index -> table.put(SELECTED_INDEX, index));
        component
                .columns()
                .ifPresent(columns -> table.put(
                        COLUMNS,
                        new TomlArray(columns.stream()
                                .<TomlValue>map(column -> TomlTable.builder()
                                        .put(MODEL_INDEX, column.modelIndex())
                                        .put(WIDTH, column.width())
                                        .build())
                                .toList())));
        return table.build();
    }
}
