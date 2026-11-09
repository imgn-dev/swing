package be.imgn.swing.examples.notes;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.examples.counter.CounterApp;
import be.imgn.swing.examples.saf.SingleFrameExample1;
import be.imgn.swing.testing.AppTester;
import be.imgn.swing.testing.OpenDialog;
import be.imgn.swing.ui.AboutDialog;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Window;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Insets;
import java.awt.font.TextAttribute;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The About dialog of the notes, with the four values of their resources, the main window's icon image and the focus,
 * as the tester records them.
 */
class AboutDialogTest {

    private static final String WEBSITE = "https://github.com/imgn-dev/swing";

    // Each line of the dialog's content, from top to bottom: "icon" for the icon, the text of any other line.
    private static List<String> lines(OpenDialog<AboutDialog> dialog) {
        JLabel name = dialog.components(JLabel.class).stream()
                .filter(label -> label.getIcon() == null)
                .findFirst()
                .orElseThrow();
        return Arrays.stream(name.getParent().getComponents())
                .map(AboutDialogTest::line)
                .toList();
    }

    private static String line(Component component) {
        return switch (component) {
            case JLabel label when label.getIcon() != null -> "icon";
            case JLabel label -> label.getText();
            case JTextField field -> field.getText();
            case JButton link -> link.getText();
            default -> throw new AssertionError("Unexpected line " + component);
        };
    }

    private static List<String> about(AppTester<?> app) {
        List<String> lines = new ArrayList<>();
        app.dialogs().answerDialog(AboutDialog.class, dialog -> lines.addAll(lines(dialog)));
        app.commands(StandardCommands.class).about().execute();
        return lines;
    }

    @Test
    void showsTheFourValuesFromTopToBottom() {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            List<String> lines = about(app);

            assertThat(app.dialogs().shown()).containsExactly(AboutDialog.class);
            assertThat(lines)
                    .containsExactly(
                            "icon",
                            "Notes",
                            "By imgn",
                            "Takes notes, and keeps them between runs.",
                            "Copyright 2026 imgn",
                            WEBSITE);
        }
    }

    @Test
    void showsTheFourValuesInFrench() {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class, Locale.FRENCH)) {
            assertThat(about(app))
                    .containsExactly(
                            "icon",
                            "Notes",
                            "Par imgn",
                            "Prend des notes, et les garde d'une exécution à l'autre.",
                            "Copyright 2026 imgn",
                            WEBSITE);
        }
    }

    @Test
    void showsTheApplicationIcon() {
        List<Icon> icons = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs()
                    .answerDialog(
                            AboutDialog.class,
                            dialog -> icons.add(
                                    dialog.components(JLabel.class).getFirst().getIcon()));

            app.commands(StandardCommands.class).about().execute();
        }
        assertThat(icons).singleElement().isInstanceOf(ImageIcon.class);
        assertThat(icons.getFirst().getIconWidth()).isEqualTo(16);
    }

    @Test
    void showsTheWebsiteAsALinkThatBrowsesIt() {
        List<Object> checks = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                JButton link = dialog.components(JButton.class).stream()
                        .filter(button -> WEBSITE.equals(button.getText()))
                        .findFirst()
                        .orElseThrow();
                checks.add(link.isBorderPainted());
                checks.add(link.isContentAreaFilled());
                checks.add(link.isFocusPainted());
                checks.add(link.getFont().getAttributes().get(TextAttribute.UNDERLINE));
                checks.add(link.getCursor().getType());
                link.doClick();
            });

            app.commands(StandardCommands.class).about().execute();

            assertThat(app.shell().browsed()).containsExactly(URI.create(WEBSITE));
        }
        assertThat(checks).containsExactly(false, false, false, TextAttribute.UNDERLINE_ON, Cursor.HAND_CURSOR);
    }

    @Test
    void showsTheWebsiteInAReadOnlySelectableFieldWithoutBorderWhenTheSystemCantBrowse() {
        List<Object> checks = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.shell().supportBrowsing(false);
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                JTextField website = dialog.component(JTextField.class);
                website.selectAll();
                checks.add(website.isEditable());
                checks.add(website.getSelectedText());
                checks.add(website.getBorder().getBorderInsets(website));
            });

            app.commands(StandardCommands.class).about().execute();
        }
        assertThat(checks).containsExactly(false, WEBSITE, new Insets(0, 0, 0, 0));
    }

    @Test
    void showsTheVersionOfAnApplicationThatHasOne() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            assertThat(about(app)).containsExactly("Counter", "Version 1.0", "By imgn");
        }
    }

    @Test
    void omitsTheValuesThatAreAbsent() {
        List<String> lines = new ArrayList<>();
        List<Integer> fields = new ArrayList<>();
        try (AppTester<SingleFrameExample1> app = AppTester.start(SingleFrameExample1.class)) {
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                lines.addAll(lines(dialog));
                fields.add(dialog.components(JTextField.class).size());
            });

            app.commands(StandardCommands.class).about().execute();
        }
        assertThat(lines).containsExactly("SingleFrameExample1");
        assertThat(fields).containsExactly(0);
    }

    @Test
    void setsTheIconImageOfTheMainWindow() {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            Optional<Image> image = app.iconImage();

            assertThat(image).isPresent();
            assertThat(image.get().getWidth(null)).isEqualTo(16);
        }
    }

    @Test
    void setsNoIconImageWithoutAnIcon() {
        try (AppTester<CounterApp> app = AppTester.start(CounterApp.class)) {
            assertThat(app.iconImage()).isEmpty();
        }
    }

    @Test
    void recordsTheComponentTheMainWindowFocuses() throws Exception {
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            Window window = app.commands(Window.class);
            JTextArea editor = app.component(JTextArea.class);
            assertThat(app.focused()).isEmpty();

            EventQueue.invokeAndWait(() -> window.focus(editor));

            assertThat(app.focused()).containsSame(editor);
        }
    }

    @Test
    void recordsTheComponentADialogFocusesInItsOwnWindow() {
        List<@Nullable Component> focused = new ArrayList<>();
        List<Component> fields = new ArrayList<>();
        try (AppTester<NotesApp> app = AppTester.start(NotesApp.class)) {
            app.shell().supportBrowsing(false);
            app.dialogs().answerDialog(AboutDialog.class, dialog -> {
                JTextField website = dialog.component(JTextField.class);
                fields.add(website);
                focused.add(dialog.focused().orElse(null));
                dialog.commands(Window.class).focus(website);
                focused.add(dialog.focused().orElse(null));
            });

            app.commands(StandardCommands.class).about().execute();

            assertThat(app.focused()).isEmpty();
        }
        assertThat(focused).containsExactly(null, fields.getFirst());
    }
}
