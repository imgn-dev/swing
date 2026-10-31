package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.Window;
import java.awt.Component;
import javax.swing.JOptionPane;

/** The real {@link Dialogs}: option panes and the error dialog, owned by its window. */
public final class SwingDialogs implements Dialogs {

    private final Window window;
    private final ApplicationInfo application;
    private final UiTexts texts;

    @Inject
    SwingDialogs(Window window, ApplicationInfo application, UiTexts texts) {
        this.window = window;
        this.application = application;
        this.texts = texts;
    }

    private Component owner() {
        return window instanceof AwtWindow real ? real.awt() : window.rootPane();
    }

    @Override
    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(owner(), message, application.displayedName(), JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    @Override
    public void inform(String message) {
        JOptionPane.showMessageDialog(owner(), message, application.displayedName(), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void error(String message, Throwable cause) {
        ErrorDialog.show(owner(), texts, texts.error(), message, cause);
    }
}
