package be.imgn.swing.ui.internal;

import be.imgn.swing.ui.CommandPalette;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.jspecify.annotations.Nullable;

/**
 * The content of the command palette's dialog: a field, with a placeholder that is also its accessible name, and the
 * entries its text matches, each its text with its shortcut at its end, the first one selected. Up and Down move the
 * selection, Enter chooses the selected entry, Escape cancels. With no match, the list shows the text of none, and
 * Enter does nothing.
 *
 * @param <E> the type of the entries
 */
public final class PalettePanel<E> extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int ROWS = 12;

    private final transient Function<String, List<E>> search;
    private final transient Class<E> type;
    private final transient Consumer<@Nullable E> close;
    private final JTextField field = new JTextField();
    private final DefaultListModel<Object> model = new DefaultListModel<>();
    private final JList<Object> list = new JList<>(model);
    private final String none;
    // Whether the list shows entries rather than the text of none.
    private boolean matched;

    /**
     * Creates the content.
     *
     * @param type the type of the entries
     * @param search the entries a query matches, best first, as {@link CommandPalette#search(List, String)} ranks them
     * @param text the text of an entry
     * @param shortcut the shortcut of an entry, or {@code null} for none
     * @param placeholder the field's placeholder, also its accessible name
     * @param none what the list shows when no entry matches
     * @param close takes the entry chosen, or {@code null} for a cancel
     */
    public PalettePanel(
            Class<E> type,
            Function<String, List<E>> search,
            Function<E, String> text,
            Function<E, @Nullable KeyStroke> shortcut,
            String placeholder,
            String none,
            Consumer<@Nullable E> close) {
        super(new BorderLayout());
        this.type = type;
        this.search = search;
        this.close = close;
        this.none = none;
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.getAccessibleContext().setAccessibleName(placeholder);
        list.getAccessibleContext().setAccessibleName(placeholder);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(ROWS);
        list.setFocusable(false);
        list.setCellRenderer(renderer(text, shortcut));
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filter();
            }
        });
        bind("UP", () -> move(-1));
        bind("DOWN", () -> move(1));
        bind("ENTER", this::choose);
        bind("ESCAPE", () -> close.accept(null));
        add(field, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        filter();
    }

    /**
     * Returns the field the user types in.
     *
     * @return the field
     */
    public JTextField field() {
        return field;
    }

    /**
     * Returns the entries shown, best first, empty when none matches.
     *
     * @return the entries
     */
    public List<E> shown() {
        return matched
                ? Collections.list(model.elements()).stream().map(type::cast).toList()
                : List.of();
    }

    /**
     * Returns the entry selected.
     *
     * @return the entry, or {@code null} when none is
     */
    public @Nullable E selected() {
        Object value = list.getSelectedValue();
        return matched && value != null ? type.cast(value) : null;
    }

    private void bind(String key, Runnable action) {
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        field.getActionMap().put(key, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void filter() {
        model.clear();
        List<E> matches = search.apply(field.getText());
        matched = !matches.isEmpty();
        if (!matched) {
            model.addElement(none);
            list.clearSelection();
        } else {
            matches.forEach(model::addElement);
            list.setSelectedIndex(0);
        }
    }

    private void move(int by) {
        if (selected() == null) {
            return;
        }
        int index = Math.floorMod(list.getSelectedIndex() + by, model.size());
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    private void choose() {
        E entry = selected();
        if (entry != null) {
            close.accept(entry);
        }
    }

    // The entry's text, with its shortcut at its end, as menu items show accelerators.
    private ListCellRenderer<Object> renderer(Function<E, String> text, Function<E, @Nullable KeyStroke> shortcut) {
        return (list, value, index, isSelected, cellHasFocus) -> {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(true);
            row.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            boolean entry = matched;
            JLabel label = new JLabel(entry ? text.apply(type.cast(value)) : String.valueOf(value));
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            label.setEnabled(entry);
            row.add(label, BorderLayout.CENTER);
            KeyStroke keyStroke = entry ? shortcut.apply(type.cast(value)) : null;
            if (keyStroke != null) {
                String modifiers = InputEvent.getModifiersExText(keyStroke.getModifiers());
                String key = KeyEvent.getKeyText(keyStroke.getKeyCode());
                JLabel accelerator = new JLabel(modifiers.isEmpty() ? key : modifiers + "+" + key);
                accelerator.setForeground(label.getForeground());
                row.add(accelerator, BorderLayout.EAST);
            }
            return (Component) row;
        };
    }
}
