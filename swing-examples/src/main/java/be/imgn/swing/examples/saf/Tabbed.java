package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicButtonUI;
import org.jspecify.annotations.Nullable;

/**
 * Tabs whose headers each hold a button that closes the tab. In SwingAppFramework, {@code Tabbed} is that header alone:
 * a panel with a label that reads the title of its tab, and the button. Here three tabs show it, with their titles in
 * resources as {@code tab-title} properties and the button's tooltip as a message.
 */
@Application(name = "Tabbed")
public final class Tabbed implements SingleFrame {

    @Exposed
    final JTabbedPane tabs = new JTabbedPane();

    @Exposed
    final JLabel one = new JLabel();

    @Exposed
    final JLabel two = new JLabel();

    @Exposed
    final JLabel three = new JLabel();

    private final ExampleMessages messages;

    @Inject
    Tabbed(ExampleMessages messages) {
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(Tabbed.class, args);
    }

    @Override
    public JComponent content() {
        for (JLabel tab : List.of(one, two, three)) {
            tabs.addTab("", tab);
            tabs.setTabComponentAt(tabs.indexOfComponent(tab), new TabHeader(tabs, messages.closeTab()));
        }
        return tabs;
    }

    /** The header of a tab: a label that reads the title of the tab, and a button that closes it. */
    static final class TabHeader extends JPanel {

        private static final long serialVersionUID = 1L;
        private static final int LABEL_GAP = 5;
        private static final int TOP_GAP = 2;

        TabHeader(JTabbedPane tabs, String closeToolTip) {
            // Without the layout's default gaps, which would widen the header.
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            setOpaque(false);
            JLabel label = new TitleLabel(tabs, this);
            label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, LABEL_GAP));
            add(label);
            add(new TabButton(tabs, this, closeToolTip));
            setBorder(BorderFactory.createEmptyBorder(TOP_GAP, 0, 0, 0));
        }
    }

    /** A label whose text is the title of the tab its header belongs to, so a renamed tab needs no update. */
    static final class TitleLabel extends JLabel {

        private static final long serialVersionUID = 1L;

        private final JTabbedPane tabs;
        private final Component header;

        TitleLabel(JTabbedPane tabs, Component header) {
            this.tabs = tabs;
            this.header = header;
        }

        @Override
        public @Nullable String getText() {
            // JLabel's constructor calls getText() before the fields are set.
            if (tabs == null) {
                return null;
            }
            int index = tabs.indexOfTabComponent(header);
            return index == -1 ? null : tabs.getTitleAt(index);
        }
    }

    /** The button that closes its tab: a cross, with a border on rollover only. */
    static final class TabButton extends JButton {

        private static final long serialVersionUID = 1L;
        private static final int SIZE = 17;
        private static final int INSET = 6;
        private static final float STROKE = 2;

        TabButton(JTabbedPane tabs, Component header, String toolTip) {
            setPreferredSize(new Dimension(SIZE, SIZE));
            setToolTipText(toolTip);
            // The same look under every look and feel, transparent, and out of the focus cycle.
            setUI(new BasicButtonUI());
            setContentAreaFilled(false);
            setFocusable(false);
            setBorder(BorderFactory.createEtchedBorder());
            setBorderPainted(false);
            setRolloverEnabled(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    setBorderPainted(true);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    setBorderPainted(false);
                }
            });
            addActionListener(event -> {
                int index = tabs.indexOfTabComponent(header);
                if (index != -1) {
                    tabs.remove(index);
                }
            });
        }

        // The button keeps its basic UI through look and feel changes.
        @Override
        public void updateUI() {}

        // The cross is drawn in the look and feel's colors: the text color, and the highlight color under the mouse.
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            if (getModel().isPressed()) {
                g2.translate(1, 1);
            }
            g2.setStroke(new BasicStroke(STROKE));
            g2.setColor(getModel().isRollover() ? UIManager.getColor("textHighlight") : getForeground());
            g2.drawLine(INSET, INSET, getWidth() - INSET - 1, getHeight() - INSET - 1);
            g2.drawLine(getWidth() - INSET - 1, INSET, INSET, getHeight() - INSET - 1);
            g2.dispose();
        }
    }
}
