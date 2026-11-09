package be.imgn.swing.examples.frames;

import be.imgn.inject.Inject;
import be.imgn.inject.PreDestroy;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.Property;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FrameView;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

/** A board, in a frame of its own, which asks before closing once guarded. */
public final class BoardView implements FrameView, ExitVeto {

    @Exposed
    final JTabbedPane tabs = new JTabbedPane();

    @Exposed
    final JTextField name = new JTextField();

    private final Property<String> title = Property.of("");
    private final Dialogs dialogs;
    private final BoardLog log;
    private final Form form;
    private boolean guarded;

    @Inject
    BoardView(Dialogs dialogs, BoardLog log, Form form) {
        this.dialogs = dialogs;
        this.log = log;
        this.form = form;
    }

    @Override
    public JComponent content() {
        tabs.addTab("One", name);
        tabs.addTab("Two", new JLabel("Two"));
        tabs.addTab("Three", new JLabel("Three"));
        form.bind(name, title);
        return tabs;
    }

    Property<String> title() {
        return title;
    }

    void guard() {
        guarded = true;
    }

    @Override
    public boolean canExit() {
        log.add("board asked");
        return !guarded || dialogs.confirm("Close the board?");
    }

    @PreDestroy
    void closed() {
        log.add("board closed");
    }
}
