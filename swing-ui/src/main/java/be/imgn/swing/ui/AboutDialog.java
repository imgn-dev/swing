package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.ui.internal.IconCache;
import be.imgn.swing.ui.internal.UiTexts;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The About dialog of the standard {@code about()} command. It shows, from top to bottom, each one that is present: the
 * application's icon, its displayed name, its version and its vendor, then the {@code description}, {@code copyright}
 * and {@code website} of the application class's resources. The website is a read-only, selectable text field without
 * border, so the user can copy it.
 *
 * <p>Tests answer it with {@code FakeDialogs.answerDialog(AboutDialog.class, ...)}; only the framework creates it.
 */
public final class AboutDialog implements DialogView<Void> {

    private static final int PADDING = 12;

    private final ApplicationInfo application;
    private final UiTexts texts;
    private final Resources resources;
    private final IconCache icons;

    @Inject
    AboutDialog(ApplicationInfo application, UiTexts texts, Resources resources, IconCache icons) {
        this.application = application;
        this.texts = texts;
        this.resources = resources;
        this.icons = icons;
    }

    /**
     * Returns the dialog's content: one line per value that is present, from top to bottom.
     *
     * @return the content
     */
    @Override
    public JComponent content() {
        TomlTable table = resources.table(application.type());
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        string(table, "icon").ifPresent(path -> add(panel, new JLabel(icons.load(application.type(), path))));
        add(panel, new JLabel(application.displayedName()));
        application.version().ifPresent(version -> add(panel, new JLabel(texts.version(version))));
        application.vendor().ifPresent(vendor -> add(panel, new JLabel(texts.by(vendor))));
        string(table, "description").ifPresent(description -> add(panel, new JLabel(description)));
        string(table, "copyright").ifPresent(copyright -> add(panel, new JLabel(copyright)));
        string(table, "website").ifPresent(website -> add(panel, website(website)));
        return panel;
    }

    private static Optional<String> string(TomlTable table, String key) {
        return table.entries().get(key) instanceof TomlString string ? Optional.of(string.value()) : Optional.empty();
    }

    private static void add(JPanel panel, JComponent component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(component);
    }

    private static JTextField website(String website) {
        JTextField field = new JTextField(website);
        field.setEditable(false);
        field.setBorder(BorderFactory.createEmptyBorder());
        // A box layout stretches a text field to its maximum size, which is unbounded.
        field.setMaximumSize(field.getPreferredSize());
        return field;
    }

    /**
     * Returns no result: the dialog only informs.
     *
     * @return an empty result
     */
    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }

    /**
     * Returns the command of the dialog's only button.
     *
     * @param commands the dialog's standard commands
     * @return {@code ok()}
     */
    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return List.of(commands.ok());
    }
}
