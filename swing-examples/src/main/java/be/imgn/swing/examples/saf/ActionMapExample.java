package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Three panels whose commands come from a class and its subclasses, each in its own popup menu. In SwingAppFramework,
 * each panel has an action map of its class's actions; in swing, each panel is a bean of the view with its own
 * generated commands.
 */
@Application(name = "ActionMapExample")
public final class ActionMapExample implements SingleFrame {

    private static final double HALF = 0.5;

    private final BaseScene base;
    private final DerivedSceneA sceneA;
    private final DerivedSceneB sceneB;

    @Inject
    ActionMapExample(BaseScene base, DerivedSceneA sceneA, DerivedSceneB sceneB) {
        this.base = base;
        this.sceneA = sceneA;
        this.sceneB = sceneB;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(ActionMapExample.class, args);
    }

    /**
     * Returns the panels of the scenes, the base one first.
     *
     * @return the panels
     */
    List<ScenePanel> scenes() {
        return List.of(base.panel(), sceneA.panel(), sceneB.panel());
    }

    @Override
    public JComponent content() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints top = new GridBagConstraints();
        top.anchor = GridBagConstraints.WEST;
        top.gridwidth = GridBagConstraints.REMAINDER;
        top.fill = GridBagConstraints.HORIZONTAL;
        panel.add(base.panel(), top);
        GridBagConstraints left = new GridBagConstraints();
        left.weightx = HALF;
        left.weighty = 1;
        left.fill = GridBagConstraints.BOTH;
        panel.add(sceneA.panel(), left);
        GridBagConstraints right = (GridBagConstraints) left.clone();
        right.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(sceneB.panel(), right);
        return panel;
    }
}
