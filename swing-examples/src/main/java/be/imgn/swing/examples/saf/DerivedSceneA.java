package be.imgn.swing.examples.saf;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.Ui;

/**
 * A scene that inherits the create and remove commands of {@link BaseScene}. Its generated commands extend the base
 * scene's and act on it, so the base scene's constructor builds its popup menu with them.
 */
@ExposeAs(DerivedSceneA.class)
public final class DerivedSceneA extends BaseScene {

    @Inject
    DerivedSceneA(Ui ui, DerivedSceneACommands commands) {
        super(ui, commands);
    }
}
