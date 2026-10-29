package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.inject.Provider;
import be.imgn.swing.Observable;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.ViewScoped;

/**
 * The scene whose create and remove commands its subclasses inherit. In SwingAppFramework, the scenes are panels; here
 * they hold one, as a bean extending a Swing component exposes its non-public supertypes. Each panel's title and icon
 * are properties of {@link ActionMapExample}'s resources, which holds the panels in its fields.
 */
@ViewScoped
public class BaseScene {

    private final ScenePanel panel = new ScenePanel();

    @Inject
    BaseScene(Ui ui, Provider<BaseSceneCommands> commands) {
        ui.popupMenu(panel, commands.get().create(), commands.get().remove());
    }

    BaseScene() {}

    /**
     * Returns the panel of the scene.
     *
     * @return the panel
     */
    public ScenePanel panel() {
        return panel;
    }

    @Command
    void create() {
        panel.createNode();
    }

    @Command(enabled = "hasSelection")
    void remove() {
        panel.removeNode();
    }

    Observable<Boolean> hasSelection() {
        return panel.hasSelection();
    }
}
