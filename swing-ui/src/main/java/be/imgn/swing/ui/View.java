package be.imgn.swing.ui;

import javax.swing.JComponent;

/** A component, with its commands and its scope. */
@ViewScoped
public interface View {

    /**
     * Returns the component of this view.
     *
     * @return the component of this view
     */
    JComponent content();
}
