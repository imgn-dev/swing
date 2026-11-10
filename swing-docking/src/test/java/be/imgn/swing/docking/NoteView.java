package be.imgn.swing.docking;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.Exposed;
import javax.swing.JComponent;
import javax.swing.JTextArea;

/** A dock view created through its assisted factory for one note, titled with it. */
public final class NoteView implements DockView {

    @Exposed
    final JTextArea text = new JTextArea();

    @Inject
    NoteView(@Assisted String note, Docked docked) {
        text.setText(note);
        docked.setTitle(note);
    }

    @Override
    public JComponent content() {
        return text;
    }
}
