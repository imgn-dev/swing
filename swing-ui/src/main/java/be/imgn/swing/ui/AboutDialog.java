package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.ui.internal.UiTexts;
import java.awt.GridLayout;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The minimal About dialog: the displayed name, the version and the vendor. */
final class AboutDialog implements DialogView<Void> {

    private static final int PADDING = 12;

    private final ApplicationInfo application;
    private final UiTexts texts;

    @Inject
    AboutDialog(ApplicationInfo application, UiTexts texts) {
        this.application = application;
        this.texts = texts;
    }

    @Override
    public JComponent content() {
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        panel.add(new JLabel(application.displayedName()));
        application.version().ifPresent(version -> panel.add(new JLabel(texts.version(version))));
        application.vendor().ifPresent(vendor -> panel.add(new JLabel(texts.by(vendor))));
        return panel;
    }

    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return List.of(commands.ok());
    }
}
