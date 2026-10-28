package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;

/**
 * Tabs of split panes and a table, whose selected tab, divider locations and columns are saved with the window when the
 * application exits, and restored when it starts: the framework finds them through the view's fields, which need no
 * name.
 */
@Application(name = "SessionStorageExample1")
// The framework reaches component fields and command targets from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class SessionStorageExample1 implements SingleFrame {

    private static final int FONT_SIZE = 24;
    private static final double HALF = 0.5;

    final JTabbedPane colors = new JTabbedPane();
    final JSplitPane red = split("red", Color.RED);
    final JSplitPane blue = split("blue", Color.BLUE);
    final JSplitPane green = split("green", Color.GREEN);
    final JSplitPane yellow = split("yellow", Color.YELLOW);
    final JSplitPane orange = split("orange", Color.ORANGE);
    final JSplitPane pink = split("pink", Color.PINK);
    final JTable all = new JTable(new TabColorTableModel());

    @Inject
    SessionStorageExample1() {}

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(SessionStorageExample1.class, args);
    }

    @Override
    public JComponent content() {
        Map<String, JSplitPane> tabs = new LinkedHashMap<>();
        tabs.put("red", red);
        tabs.put("blue", blue);
        tabs.put("green", green);
        tabs.put("yellow", yellow);
        tabs.put("orange", orange);
        tabs.put("pink", pink);
        tabs.forEach(colors::add);
        String[] titles = {"Name", "Red", "Green", "Blue"};
        for (int index = 0; index < titles.length; index++) {
            all.getColumnModel().getColumn(index).setHeaderValue(titles[index]);
        }
        colors.add("all colors", new JScrollPane(all));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(
                new JLabel("Tab selection and split divider locations are saved and restored between executions"),
                BorderLayout.PAGE_START);
        panel.add(colors, BorderLayout.CENTER);
        return panel;
    }

    private static JSplitPane split(String title, Color color) {
        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                label(title + ".darker()", color.darker()),
                label(title + ".brighter()", color.brighter()));
        split.setResizeWeight(HALF);
        return split;
    }

    private static JLabel label(String title, Color color) {
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setFont(new Font("LucidaSans", Font.PLAIN, FONT_SIZE));
        label.setOpaque(true);
        label.setBackground(color.darker());
        label.setForeground(Color.WHITE);
        return label;
    }

    /** The colors of the tabs: their names and their components. */
    private final class TabColorTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final int COLUMNS = 4;
        private static final int BLUE = 3;

        @Override
        public int getColumnCount() {
            return COLUMNS;
        }

        @Override
        public int getRowCount() {
            return colors.getTabCount();
        }

        @Override
        public Object getValueAt(int row, int column) {
            Color background = colors.getComponentAt(row).getBackground();
            return switch (column) {
                case 0 -> colors.getTitleAt(row);
                case 1 -> background.getRed();
                case 2 -> background.getGreen();
                case BLUE -> background.getBlue();
                default -> "error";
            };
        }
    }
}
