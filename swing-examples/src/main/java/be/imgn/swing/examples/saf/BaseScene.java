package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.inject.Provider;
import be.imgn.swing.Observable;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Ui;
import be.imgn.swing.ui.ViewScoped;

/**
 * The scene of green circles, whose create and remove commands its subclasses inherit. In SwingAppFramework, the scenes
 * are panels; here they hold one, as a bean extending a Swing component exposes its non-public supertypes.
 */
@ViewScoped
public class BaseScene {

    private final ScenePanel panel;

    @Inject
    BaseScene(Ui ui, Provider<BaseSceneCommands> commands) {
        this("resources/green-circle.png", "BaseScenePanel");
        ui.popupMenu(panel, commands.get().create(), commands.get().remove());
    }

    BaseScene(String iconPath, String title) {
        this.panel = new ScenePanel(iconPath, title);
    }

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
