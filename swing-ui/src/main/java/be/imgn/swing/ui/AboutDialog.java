package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.ui.internal.IconCache;
import be.imgn.swing.ui.internal.UiTexts;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.font.TextAttribute;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 * The About dialog of the standard {@code about()} command. It shows, from top to bottom, each one that is present: the
 * application's icon, its displayed name, its version and its vendor, then the {@code description}, {@code copyright}
 * and {@code website} of the application class's resources. When the system can browse, the website is a link that
 * opens it through {@link SystemShell}; otherwise, it is a read-only, selectable text field without border, so the user
 * can copy it.
 *
 * <p>Tests answer it with {@code FakeDialogs.answerDialog(AboutDialog.class, ...)}; only the framework creates it.
 */
public final class AboutDialog implements DialogView<Void> {

    private static final int PADDING = 12;

    private final ApplicationInfo application;
    private final UiTexts texts;
    private final Resources resources;
    private final IconCache icons;
    private final SystemShell shell;

    @Inject
    AboutDialog(ApplicationInfo application, UiTexts texts, Resources resources, IconCache icons, SystemShell shell) {
        this.application = application;
        this.texts = texts;
        this.resources = resources;
        this.icons = icons;
        this.shell = shell;
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

    // A link when the system can browse, else a field the user can copy the website from.
    private JComponent website(String website) {
        if (!shell.canBrowse()) {
            return field(website);
        }
        JButton link = new JButton(website);
        link.setBorderPainted(false);
        link.setContentAreaFilled(false);
        link.setFocusPainted(false);
        link.setBorder(BorderFactory.createEmptyBorder());
        link.setForeground(Objects.requireNonNullElse(UIManager.getColor("Component.linkColor"), Color.BLUE));
        link.setFont(link.getFont().deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addActionListener(event -> shell.browse(URI.create(website)));
        return link;
    }

    private static JTextField field(String website) {
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
