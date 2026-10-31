package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Ui;

/**
 * A scene that inherits the create and remove commands of {@link BaseScene}. Its generated commands extend the base
 * scene's and act on it, so the base scene's constructor builds its popup menu with them.
 */
public final class DerivedSceneB extends BaseScene {

    private static final long serialVersionUID = 1L;

    @Inject
    DerivedSceneB(Ui ui, DerivedSceneBCommands commands) {
        super(ui, commands);
    }
}
