package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
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
 * name. The tabs' titles and the labels' texts, font and foreground are in the resources; the colors they show are the
 * demo's data.
 */
@Application(name = "SessionStorageExample1")
// The framework reaches component fields and command targets from generated code, so they are package-private.
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class SessionStorageExample1 implements SingleFrame {

    private static final double HALF = 0.5;

    final JLabel explanation = new JLabel();
    final JTabbedPane colors = new JTabbedPane();
    final JLabel redDarker = swatch(Color.RED.darker());
    final JLabel redBrighter = swatch(Color.RED.brighter());
    final JSplitPane red = split(redDarker, redBrighter);
    final JLabel blueDarker = swatch(Color.BLUE.darker());
    final JLabel blueBrighter = swatch(Color.BLUE.brighter());
    final JSplitPane blue = split(blueDarker, blueBrighter);
    final JLabel greenDarker = swatch(Color.GREEN.darker());
    final JLabel greenBrighter = swatch(Color.GREEN.brighter());
    final JSplitPane green = split(greenDarker, greenBrighter);
    final JLabel yellowDarker = swatch(Color.YELLOW.darker());
    final JLabel yellowBrighter = swatch(Color.YELLOW.brighter());
    final JSplitPane yellow = split(yellowDarker, yellowBrighter);
    final JLabel orangeDarker = swatch(Color.ORANGE.darker());
    final JLabel orangeBrighter = swatch(Color.ORANGE.brighter());
    final JSplitPane orange = split(orangeDarker, orangeBrighter);
    final JLabel pinkDarker = swatch(Color.PINK.darker());
    final JLabel pinkBrighter = swatch(Color.PINK.brighter());
    final JSplitPane pink = split(pinkDarker, pinkBrighter);
    final JTable all = new JTable(new TabColorTableModel());
    final JScrollPane allColors = new JScrollPane(all);
    private final ExampleMessages messages;

    @Inject
    SessionStorageExample1(ExampleMessages messages) {
        this.messages = messages;
    }

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
        // The titles are the tab-title properties of the fields, applied once the tabs exist.
        for (JComponent tab : List.of(red, blue, green, yellow, orange, pink, allColors)) {
            colors.addTab("", tab);
        }
        String[] titles = {messages.nameHeader(), messages.redHeader(), messages.greenHeader(), messages.blueHeader()};
        for (int index = 0; index < titles.length; index++) {
            all.getColumnModel().getColumn(index).setHeaderValue(titles[index]);
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(explanation, BorderLayout.PAGE_START);
        panel.add(colors, BorderLayout.CENTER);
        return panel;
    }

    private static JSplitPane split(JLabel left, JLabel right) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(HALF);
        return split;
    }

    private static JLabel swatch(Color color) {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(color.darker());
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
