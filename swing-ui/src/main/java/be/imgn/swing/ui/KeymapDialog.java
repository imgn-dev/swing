package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.ui.internal.KeyStrokes;
import be.imgn.swing.ui.internal.Mnemonics;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.UiTexts;
import be.imgn.swing.ui.internal.ViewHost;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import org.jspecify.annotations.Nullable;

/**
 * The dialog where the user assigns, removes and resets the shortcuts of the commands of a window, which
 * {@link StandardCommands#keymap()} shows over its view's window.
 *
 * <p>It lists the commands that the views of the opener's window created, but the system actions on macOS, which the
 * application menu holds, sorted by text. A filter field filters them as the command palette searches. The field below
 * the table records the next key press, with the modifiers held, as the selected command's shortcut; Remove, Reset and
 * Reset All act on the selected row, or on every row. The edits apply through the {@link Keymap} when OK commits them,
 * and Cancel drops them.
 */
public final class KeymapDialog implements DialogView<Void> {

    private static final int PADDING = 12;
    private static final int RECORDED_MODIFIERS = InputEvent.SHIFT_DOWN_MASK
            | InputEvent.CTRL_DOWN_MASK
            | InputEvent.META_DOWN_MASK
            | InputEvent.ALT_DOWN_MASK;
    private static final Set<Integer> UNRECORDED = Set.of(
            KeyEvent.VK_SHIFT,
            KeyEvent.VK_CONTROL,
            KeyEvent.VK_ALT,
            KeyEvent.VK_ALT_GRAPH,
            KeyEvent.VK_META,
            KeyEvent.VK_TAB,
            KeyEvent.VK_ESCAPE,
            KeyEvent.VK_UNDEFINED);

    private final UiServices services;
    private final UiTexts texts;
    private final Keymap keymap;
    private final List<BoundCommand> commands;
    private final Map<BoundCommand, Edit> edits = new LinkedHashMap<>();
    private final Property<Integer> editCount = Property.of(0);
    private final Model model = new Model();
    private final JTextField filter = new JTextField(20);
    private final JTable table;
    private final JTextField shortcut = new JTextField(16);
    private final JLabel conflict = new JLabel(" ");
    private List<BoundCommand> shown = List.of();

    @Inject
    KeymapDialog(Scope scope, UiServices services, UiTexts texts, Keymap keymap, Form form) {
        this.services = services;
        this.texts = texts;
        this.keymap = keymap;
        this.commands = commandsOf(services.registry().viewOf(scope));
        // Once the texts are known, which the table's headers read.
        this.table = new JTable(model);
        form.track(editCount);
    }

    // The commands of the views of the opener's window, sorted by text in the display locale.
    private List<BoundCommand> commandsOf(ViewHost dialog) {
        List<BoundCommand> found = new ArrayList<>();
        dialog.window().opener().ifPresent(opener -> {
            for (ViewHost view : opener.window().views()) {
                for (BoundCommand command :
                        view.injector().getInstance(CommandRegistry.class).commands()) {
                    if (!(services.isMacOS() && command.isSystemAction())
                            && found.stream()
                                    .noneMatch(listed -> listed.identity().equals(command.identity()))) {
                        found.add(command);
                    }
                }
            }
        });
        Collator collator = Collator.getInstance(services.resources().locale());
        found.sort(Comparator.comparing(command -> command.text().text(), collator));
        return List.copyOf(found);
    }

    @Override
    public JComponent content() {
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                show();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                show();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                show();
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getAccessibleContext().setAccessibleName(texts.keyboardShortcuts());
        table.getSelectionModel().addListSelectionListener(event -> {
            conflict.setText(" ");
            BoundCommand command = selected();
            shortcut.setText(command == null ? "" : describe(effective(command)));
        });
        shortcut.setEditable(false);
        shortcut.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                record(event);
            }
        });
        show();

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING));
        top.add(label(texts.filter(), filter));
        top.add(filter);
        JPanel record = new JPanel(new FlowLayout(FlowLayout.LEADING));
        record.add(label(texts.shortcutLabel(), shortcut));
        record.add(shortcut);
        record.add(button(texts.remove(), () -> edit(Edit.REMOVE)));
        record.add(button(texts.reset(), () -> edit(Edit.RESET)));
        record.add(button(texts.resetAll(), this::resetAll));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(record, BorderLayout.NORTH);
        bottom.add(conflict, BorderLayout.SOUTH);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }

    @Override
    public void commit() {
        keymap.batch(() -> edits.forEach((command, edit) -> {
            if (edit.kind == Edit.Kind.ASSIGN) {
                keymap.assign(command, edit.shortcut());
            } else if (edit.kind == Edit.Kind.REMOVE) {
                keymap.remove(command);
            } else {
                keymap.reset(command);
            }
        }));
        edits.clear();
    }

    private JLabel label(String text, JComponent field) {
        JLabel label = new JLabel();
        Mnemonics parsed = Mnemonics.parse(text);
        label.setText(parsed.text());
        if (!services.isMacOS() && parsed.hasMnemonic()) {
            label.setDisplayedMnemonic(parsed.keyCode());
            label.setDisplayedMnemonicIndex(parsed.index());
        }
        label.setLabelFor(field);
        return label;
    }

    private AbstractButton button(String text, Runnable action) {
        JButton button = new JButton();
        Mnemonics parsed = Mnemonics.parse(text);
        button.setText(parsed.text());
        if (!services.isMacOS() && parsed.hasMnemonic()) {
            button.setMnemonic(parsed.keyCode());
            button.setDisplayedMnemonicIndex(parsed.index());
        }
        button.addActionListener(event -> action.run());
        return button;
    }

    // Shows the commands the filter matches, keeping the selected one selected when it still shows.
    private void show() {
        BoundCommand selected = selected();
        String query = CommandSearch.normalize(filter.getText());
        shown = query.isEmpty()
                ? commands
                : commands.stream()
                        .filter(command -> CommandSearch.rank(
                                        CommandSearch.normalize(command.text().text()), query)
                                != CommandSearch.NO_MATCH)
                        .toList();
        model.fireTableDataChanged();
        int index = selected == null ? -1 : shown.indexOf(selected);
        if (index >= 0) {
            table.setRowSelectionInterval(index, index);
        }
    }

    private @Nullable BoundCommand selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= shown.size() ? null : shown.get(row);
    }

    // A key that is not a modifier, Tab or Escape, with the modifiers held, becomes the selected command's shortcut.
    private void record(KeyEvent event) {
        BoundCommand command = selected();
        if (command == null || UNRECORDED.contains(event.getKeyCode())) {
            return;
        }
        event.consume();
        KeyStroke recorded = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiersEx() & RECORDED_MODIFIERS);
        if (!survivesSaving(recorded)) {
            return;
        }
        edit(Edit.assign(recorded));
        conflict.setText(commands.stream()
                .filter(other -> !other.identity().equals(command.identity()) && recorded.equals(effective(other)))
                .findFirst()
                .map(other -> texts.alsoUsedBy(other.text().text()))
                .orElse(" "));
    }

    // A key that has no name in the resources' syntax would be lost at the next start.
    private boolean survivesSaving(KeyStroke recorded) {
        boolean macOS = services.isMacOS();
        try {
            return KeyStrokes.parse(KeyStrokes.format(recorded, macOS), macOS).equals(recorded);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void edit(Edit edit) {
        BoundCommand command = selected();
        if (command == null) {
            return;
        }
        // Last in the order of the edits, so that the last recording of a shortcut wins at OK.
        edits.remove(command);
        edits.put(command, edit);
        edited();
    }

    private void resetAll() {
        shown.forEach(command -> edits.put(command, Edit.RESET));
        edited();
    }

    private void edited() {
        editCount.set(editCount.get() + 1);
        model.fireTableRowsUpdated(0, Math.max(0, shown.size() - 1));
        BoundCommand command = selected();
        shortcut.setText(command == null ? "" : describe(effective(command)));
    }

    // The shortcut a command will have once the edits apply.
    private @Nullable KeyStroke effective(BoundCommand command) {
        Edit edit = edits.get(command);
        if (edit == null) {
            return keymap.shortcut(command).orElse(null);
        }
        return switch (edit.kind) {
            case ASSIGN -> edit.shortcut();
            case REMOVE -> null;
            case RESET -> keymap.defaultShortcut(command).orElse(null);
        };
    }

    // As menu items show accelerators.
    private static String describe(@Nullable KeyStroke keyStroke) {
        if (keyStroke == null) {
            return "";
        }
        String modifiers = InputEvent.getModifiersExText(keyStroke.getModifiers());
        String key = KeyEvent.getKeyText(keyStroke.getKeyCode());
        return modifiers.isEmpty() ? key : modifiers + "+" + key;
    }

    private final class Model extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        @Override
        public int getRowCount() {
            return shown.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? texts.command() : texts.shortcut();
        }

        @Override
        public Object getValueAt(int row, int column) {
            BoundCommand command = shown.get(row);
            return column == 0 ? command.text().text() : describe(effective(command));
        }
    }

    // An edit of a command's shortcut, held until OK.
    private record Edit(Kind kind, @Nullable KeyStroke recorded) {

        static final Edit REMOVE = new Edit(Kind.REMOVE, null);
        static final Edit RESET = new Edit(Kind.RESET, null);

        static Edit assign(KeyStroke shortcut) {
            return new Edit(Kind.ASSIGN, shortcut);
        }

        KeyStroke shortcut() {
            KeyStroke assigned = recorded;
            if (assigned == null) {
                throw new IllegalStateException(kind + " assigns no shortcut");
            }
            return assigned;
        }

        enum Kind {
            ASSIGN,
            REMOVE,
            RESET
        }
    }
}
