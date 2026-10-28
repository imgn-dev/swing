package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Task;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * A map of keys and values that the application saves to, and loads from, a file of its data directory. In
 * SwingAppFramework, the file is written by {@code LocalStorage} with {@code XMLEncoder}; in swing, it is a TOML table
 * in {@link ApplicationInfo#directories()}, written atomically.
 */
@Application(name = "LocalStorageExample1", vendor = "Sun")
// The framework reaches component fields from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class LocalStorageExample1 implements SingleFrame {

    private static final String FILE = "map.toml";
    private static final int COLUMNS = 16;
    private static final int ROWS = 12;
    private static final int BOUND = 10_000;
    private static final int BORDER = 4;

    final JTextField key = new JTextField(" Key ", COLUMNS);
    final JTextField value = new JTextField(" Value ", COLUMNS);
    final JTextField message = new JTextField();
    final JList<String> mapList = new JList<>();
    private final Map<String, String> map = new LinkedHashMap<>();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final LocalStorageExample1Commands commands;
    private final ApplicationInfo application;
    private final ExampleMessages messages;

    @Inject
    LocalStorageExample1(LocalStorageExample1Commands commands, ApplicationInfo application, ExampleMessages messages) {
        this.commands = commands;
        this.application = application;
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(LocalStorageExample1.class, args);
    }

    @Override
    public JComponent content() {
        message.setEditable(false);
        mapList.setModel(listModel);
        mapList.setPrototypeCellValue("Hello = World");
        mapList.setVisibleRowCount(ROWS);
        key.setAction(commands.addRandomEntry().asAction());
        value.setAction(commands.addRandomEntry().asAction());
        JPanel fields = new JPanel(new FlowLayout());
        fields.add(key);
        fields.add(value);
        fields.add(commands.addRandomEntry().createButton());
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(commands.saveMap().createButton());
        buttons.add(commands.loadMap().createButton());
        buttons.add(commands.clearMap().createButton());
        JScrollPane scroll = new JScrollPane(mapList);
        scroll.setBorder(BorderFactory.createEmptyBorder(BORDER / 2, BORDER, BORDER / 2, BORDER));
        JPanel center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        center.add(buttons, BorderLayout.PAGE_END);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(fields, BorderLayout.PAGE_START);
        panel.add(center, BorderLayout.CENTER);
        panel.add(message, BorderLayout.PAGE_END);
        return panel;
    }

    @Command
    void addRandomEntry() {
        put(key.getText().strip() + random.nextInt(BOUND), value.getText().strip() + random.nextInt(BOUND));
    }

    @Command
    void clearMap() {
        map.clear();
        showMap();
    }

    @Command
    Task<Path> saveMap() {
        Path file = file();
        TomlTable.Builder table = TomlTable.builder();
        map.forEach(table::put);
        TomlTable saved = table.build();
        return Task.of(() -> {
                    Files.createDirectories(file.getParent());
                    Toml.writeAtomically(saved, file);
                    return file;
                })
                .onSuccess(path -> message.setText(messages.savedFile(path.toString())));
    }

    @Command
    Task<Map<String, String>> loadMap() {
        Path file = file();
        return Task.of(() -> {
                    Map<String, String> loaded = new LinkedHashMap<>();
                    for (Map.Entry<String, TomlValue> entry :
                            Toml.parse(file).root().entries().entrySet()) {
                        if (entry.getValue() instanceof TomlString text) {
                            loaded.put(entry.getKey(), text.value());
                        }
                    }
                    return loaded;
                })
                .onSuccess(loaded -> {
                    map.clear();
                    map.putAll(loaded);
                    showMap();
                    message.setText(messages.loadedFile(file.toString()));
                });
    }

    /**
     * Puts an entry in the map, and shows it.
     *
     * @param entryKey the key
     * @param entryValue the value
     */
    void put(String entryKey, String entryValue) {
        map.put(entryKey, entryValue);
        showMap();
    }

    private void showMap() {
        listModel.clear();
        map.forEach((entryKey, entryValue) -> listModel.addElement(entryKey + " = " + entryValue));
    }

    private Path file() {
        return application.directories().data().resolve(FILE);
    }
}
