package be.imgn.swing.examples.saf;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Ui;

/**
 * A scene that inherits the create and remove commands of {@link BaseScene}, with its own icon. It declares create
 * again, so that it gets its own generated commands, which act on it rather than on the base scene.
 */
@ExposeAs(DerivedSceneA.class)
public final class DerivedSceneA extends BaseScene {

    @Inject
    DerivedSceneA(Ui ui, DerivedSceneACommands commands) {
        super("resources/blue-square.png", "DerivedScenePanelA");
        ui.popupMenu(panel(), commands.create(), commands.remove());
    }

    @Override
    @Command
    void create() {
        super.create();
    }
}
