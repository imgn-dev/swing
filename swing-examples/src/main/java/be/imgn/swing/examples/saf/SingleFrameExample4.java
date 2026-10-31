package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.MenuEntry;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 * A text editor whose cut, copy and paste commands, in a menu and a toolbar, follow the selection of the editor: the
 * standard commands do. Open loads a file; close restores the default text.
 */
@Application(name = "SingleFrameExample4", vendor = "Sun", version = "1.0")
public final class SingleFrameExample4 implements SingleFrame {

    private final JTextPane textPane = new JTextPane();
    private final SingleFrameExample4Commands commands;
    private final Ui ui;
    private final ExampleMessages messages;

    @Inject
    SingleFrameExample4(SingleFrameExample4Commands commands, Ui ui, ExampleMessages messages) {
        this.commands = commands;
        this.ui = ui;
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(SingleFrameExample4.class, args);
    }

    @Override
    public JComponent content() {
        textPane.setText(messages.defaultText());
        JScrollPane scroll = new JScrollPane(
                textPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 2));
        return scroll;
    }

    @Override
    public JMenuBar menuBar() {
        return ui.menuBar(
                ui.menus().file().with(commands.open(), commands.close(), MenuEntry.SEPARATOR, commands.exit()),
                ui.menus().edit().with(commands.cut(), commands.copy(), commands.paste()));
    }

    @Override
    public JToolBar toolBar() {
        JToolBar toolBar = ui.toolBar(commands.cut(), commands.copy(), commands.paste());
        toolBar.setFloatable(false);
        return toolBar;
    }

    @Command
    void open() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(ui.window().rootPane()))
                == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                textPane.setPage(file.toURI().toURL());
            } catch (IOException e) {
                ui.dialogs().error(messages.cannotOpen(file.toString()), e);
            }
        }
    }

    @Command
    void close() {
        textPane.setText(messages.defaultText());
    }

    JTextPane textPane() {
        return textPane;
    }
}
