package be.imgn.swing.examples.saf;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.Command;
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

    // Declared again only so that the processor sees the class, which names its generated commands in its constructor:
    // gaia holds back a root element whose signature it can't resolve yet.
    @Override
    @Command
    void create() {
        super.create();
    }
}
