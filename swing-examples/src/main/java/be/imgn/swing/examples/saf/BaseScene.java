package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.ViewScoped;

/**
 * The scene whose create and remove commands its subclasses inherit. As in SwingAppFramework, a scene is its panel.
 * Each panel's title and icon are properties of {@link ActionMapExample}'s resources, which holds the scenes in its
 * fields.
 */
@ViewScoped
public class BaseScene extends ScenePanel {

    private static final long serialVersionUID = 1L;

    @Inject
    BaseScene(Ui ui, BaseSceneCommands commands) {
        ui.popupMenu(this, commands.create(), commands.remove());
    }

    @Command
    void create() {
        createNode();
    }

    @Command(enabled = "hasSelection")
    void remove() {
        removeNode();
    }
}
