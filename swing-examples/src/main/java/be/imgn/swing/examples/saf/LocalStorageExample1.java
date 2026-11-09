package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
 * A map of keys and values that the application keeps between runs. In SwingAppFramework, the map is saved and loaded
 * by {@code LocalStorage} with {@code XMLEncoder}, from commands; in swing, it is the {@link Entries} settings record,
 * loaded at startup and saved at each change, in the config directory.
 */
@Application(vendor = "Sun")
public final class LocalStorageExample1 implements SingleFrame {

    private static final int COLUMNS = 16;
    private static final int ROWS = 12;
    private static final int BOUND = 10_000;
    private static final int BORDER = 4;

    private final JTextField key = new JTextField(" Key ", COLUMNS);
    private final JTextField value = new JTextField(" Value ", COLUMNS);
    private final JList<String> mapList = new JList<>();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final Property<Entries> entries;
    private final LocalStorageExample1Commands commands;
    private final Ui ui;

    @Inject
    LocalStorageExample1(LocalStorageExample1Commands commands, Ui ui, SettingsStore settings) {
        this.commands = commands;
        this.ui = ui;
        this.entries = settings.get(Entries.class);
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
        mapList.setModel(listModel);
        mapList.setPrototypeCellValue("Hello = World");
        mapList.setVisibleRowCount(ROWS);
        key.setAction(commands.addRandomEntry().asAction());
        value.setAction(commands.addRandomEntry().asAction());
        JPanel fields = new JPanel(new FlowLayout());
        fields.add(key);
        fields.add(value);
        fields.add(commands.addRandomEntry().createButton());
        JPanel buttons = ui.buttonBar(commands.clearMap());
        JScrollPane scroll = new JScrollPane(mapList);
        scroll.setBorder(BorderFactory.createEmptyBorder(BORDER / 2, BORDER, BORDER / 2, BORDER));
        ui.bind(this::showMap, entries);
        JPanel center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        center.add(buttons, BorderLayout.PAGE_END);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(fields, BorderLayout.PAGE_START);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    @Command
    void addRandomEntry() {
        put(key.getText().strip() + random.nextInt(BOUND), value.getText().strip() + random.nextInt(BOUND));
    }

    @Command
    void clearMap() {
        entries.set(new Entries());
    }

    /**
     * Puts an entry in the map, which saves it and shows it.
     *
     * @param entryKey the key
     * @param entryValue the value
     */
    void put(String entryKey, String entryValue) {
        Map<String, String> map = new LinkedHashMap<>(entries.get().entries());
        map.put(entryKey, entryValue);
        entries.set(new Entries(map));
    }

    private void showMap(Entries shown) {
        listModel.clear();
        shown.entries().forEach((entryKey, entryValue) -> listModel.addElement(entryKey + " = " + entryValue));
    }
}
