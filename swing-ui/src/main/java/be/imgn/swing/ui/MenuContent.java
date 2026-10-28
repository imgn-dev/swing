package be.imgn.swing.ui;

import be.imgn.swing.ui.internal.EdtDelivery;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPopupMenu;

/**
 * The content of one menu: fixed entries and dynamic sections. Rebuilding a section drops the separators it leaves
 * doubled, and checks the mnemonics again.
 */
final class MenuContent {

    private final MenuBuilder builder;
    private final JPopupMenu popup;
    private final String name;
    private final List<List<Component>> parts = new ArrayList<>();

    MenuContent(MenuBuilder builder, JPopupMenu popup, String name) {
        this.builder = builder;
        this.popup = popup;
        this.name = name;
    }

    void add(MenuEntry entry) {
        switch (entry) {
            case BoundCommand command -> fixed(builder.commandItem(command));
            case Separator ignored -> fixed(List.of(new JPopupMenu.Separator()));
            case MenuSpec spec -> fixed(List.of(builder.createMenu(spec)));
            case MenuEntry.Dynamic dynamic -> section(dynamic);
        }
    }

    private void fixed(List<Component> components) {
        parts.add(components);
        components.forEach(popup::add);
    }

    private void section(MenuEntry.Dynamic dynamic) {
        List<Component> section = new ArrayList<>();
        parts.add(section);
        int part = parts.size() - 1;
        fill(section, dynamic.entries().get());
        section.forEach(popup::add);
        EdtDelivery.subscribe(builder.view().scope(), dynamic.entries(), entries -> rebuild(part, entries));
    }

    private void fill(List<Component> section, List<? extends DynamicEntry> entries) {
        for (DynamicEntry entry : entries) {
            section.addAll(builder.components(entry));
        }
    }

    private void rebuild(int part, List<? extends DynamicEntry> entries) {
        List<Component> section = parts.get(part);
        section.forEach(popup::remove);
        section.clear();
        fill(section, entries);
        int index = 0;
        for (int previous = 0; previous < part; previous++) {
            index += parts.get(previous).size();
        }
        for (Component component : section) {
            popup.insert(component, index++);
        }
        built();
        if (popup.isVisible()) {
            popup.revalidate();
            popup.pack();
        }
    }

    /** Drops the leading, trailing and doubled separators, then checks the mnemonics. */
    void built() {
        JPopupMenu.Separator pending = null;
        boolean seenItem = false;
        for (Component component : popup.getComponents()) {
            if (component instanceof JPopupMenu.Separator separator) {
                separator.setVisible(false);
                if (seenItem && pending == null) {
                    pending = separator;
                }
            } else {
                if (pending != null) {
                    pending.setVisible(true);
                    pending = null;
                }
                seenItem = true;
            }
        }
        builder.check(List.of(popup.getComponents()), name);
    }
}
