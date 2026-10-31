package be.imgn.swing.ui;

import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.ui.internal.EdtDelivery;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPopupMenu;

/**
 * The content of one menu: fixed entries and dynamic sections. Rebuilding a section drops the separators it leaves
 * doubled, and checks the mnemonics again. The subscriptions of the menu belong to a scope: the view's, or, for a
 * submenu inside a dynamic section, that section's, which is closed and opened again at each rebuild, so that the
 * subscriptions of a replaced submenu are closed with it.
 */
final class MenuContent {

    private final MenuBuilder builder;
    private final JPopupMenu popup;
    private final String name;
    private final DisposalScope scope;
    private final List<List<Component>> parts = new ArrayList<>();
    private final Map<Integer, DisposalScope> sectionScopes = new HashMap<>();

    MenuContent(MenuBuilder builder, JPopupMenu popup, String name, DisposalScope scope) {
        this.builder = builder;
        this.popup = popup;
        this.name = name;
        this.scope = scope;
    }

    void add(MenuEntry entry) {
        switch (entry) {
            case BoundCommand command -> fixed(builder.commandItem(command));
            case Separator ignored -> fixed(List.of(new JPopupMenu.Separator()));
            case MenuSpec spec -> fixed(List.of(builder.createMenu(spec, scope)));
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
        fill(section, dynamic.entries().get(), open(part));
        section.forEach(popup::add);
        // The section's own subscription stays in the menu's scope; only its components' go with each rebuild.
        EdtDelivery.subscribe(scope, dynamic.entries(), entries -> rebuild(part, entries), EdtDelivery.Order.FRAMEWORK);
    }

    /** Opens the scope of a section's components, a child of the menu's, closing the previous one. */
    private DisposalScope open(int part) {
        DisposalScope previous = sectionScopes.get(part);
        if (previous != null) {
            previous.close();
        }
        DisposalScope opened = scope.child(name + ", dynamic section " + part);
        sectionScopes.put(part, opened);
        return opened;
    }

    private void fill(List<Component> section, List<? extends DynamicEntry> entries, DisposalScope sectionScope) {
        for (DynamicEntry entry : entries) {
            section.addAll(builder.components(entry, sectionScope));
        }
    }

    private void rebuild(int part, List<? extends DynamicEntry> entries) {
        List<Component> section = parts.get(part);
        section.forEach(popup::remove);
        section.clear();
        fill(section, entries, open(part));
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
