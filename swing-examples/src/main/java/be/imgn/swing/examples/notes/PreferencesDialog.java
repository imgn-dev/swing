package be.imgn.swing.examples.notes;

import be.imgn.inject.Inject;
import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.SettingsStore;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.Converter;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.StandardCommands;
import java.util.List;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * The preferences of the editor: its font size, a whole number from 6 to 72, and whether it wraps its lines, with OK,
 * Cancel and Apply. The {@link EditorSettings} change when OK or Apply commits the form.
 */
public final class PreferencesDialog implements DialogView<EditorSettings> {

    private static final int MIN_FONT_SIZE = 6;
    private static final int MAX_FONT_SIZE = 72;
    private static final int COLUMNS = 4;

    @Exposed
    final JLabel fontSizeLabel = new JLabel();

    @Exposed
    final JCheckBox wordWrapBox = new JCheckBox();

    private final JTextField fontSizeField = new JTextField(COLUMNS);
    private final Property<EditorSettings> settings;
    private final Property<Integer> fontSize;
    private final Property<Boolean> wordWrap;
    private final Form form;
    private final NotesMessages messages;

    @Inject
    PreferencesDialog(SettingsStore store, Form form, NotesMessages messages) {
        this.settings = store.get(EditorSettings.class);
        this.form = form;
        this.messages = messages;
        EditorSettings current = settings.get();
        this.fontSize = Property.of(current.fontSize());
        this.wordWrap = Property.of(current.wordWrap());
    }

    @Override
    public JComponent content() {
        form.bind(fontSizeField, fontSize, Converter.of(Integer::valueOf, String::valueOf, messages.wholeNumber()))
                .check(Rule.of(
                        messages.fontSizeRange(MIN_FONT_SIZE, MAX_FONT_SIZE),
                        (Integer size) -> size >= MIN_FONT_SIZE && size <= MAX_FONT_SIZE));
        form.bind(wordWrapBox, wordWrap);
        return FormPanel.create()
                .row(fontSizeLabel, fontSizeField)
                .wide(wordWrapBox)
                .build();
    }

    /**
     * Returns the settings of the form.
     *
     * @return the settings the dialog shows
     */
    @Override
    public Optional<EditorSettings> result() {
        return Optional.of(edited());
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return List.of(commands.ok(), commands.cancel(), commands.apply());
    }

    @Override
    public void commit() {
        settings.set(edited());
    }

    private EditorSettings edited() {
        return new EditorSettings(fontSize.get(), wordWrap.get());
    }
}
