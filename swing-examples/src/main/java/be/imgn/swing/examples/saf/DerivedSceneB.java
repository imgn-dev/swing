package be.imgn.swing.examples.saf;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Ui;

/**
 * A scene that inherits the create and remove commands of {@link BaseScene}. It declares create again, so that it gets
 * its own generated commands, which act on it rather than on the base scene.
 */
@ExposeAs(DerivedSceneB.class)
public final class DerivedSceneB extends BaseScene {

    @Inject
    DerivedSceneB(Ui ui, DerivedSceneBCommands commands) {
        ui.popupMenu(panel(), commands.create(), commands.remove());
    }

    @Override
    @Command
    void create() {
        super.create();
    }
}
