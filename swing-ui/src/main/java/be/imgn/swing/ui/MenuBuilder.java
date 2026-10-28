package be.imgn.swing.ui;

import be.imgn.swing.ui.internal.Mnemonics;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.toml.TomlTable;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;

/** Turns the descriptions of a view's menus and toolbars into components, and checks their mnemonics. */
final class MenuBuilder {

    private static final System.Logger LOGGER = System.getLogger(MenuBuilder.class.getName());
    private static final List<String> STANDARD_MENUS = List.of("file", "edit", "view", "window", "help");

    private final ViewHost view;
    private final UiServices services;
    private final Map<String, BoundMenu> menus = new HashMap<>();

    MenuBuilder(ViewHost view, UiServices services) {
        this.view = view;
        this.services = services;
    }

    BoundMenu menu(Class<?> owner, String id) {
        return menus.computeIfAbsent(owner.getName() + "#" + id, key -> {
            List<TomlTable> tables = new ArrayList<>();
            if (STANDARD_MENUS.contains(id)) {
                tables.add(section(StandardMenus.class, id));
            }
            tables.add(section(owner == StandardMenus.class ? view.type() : owner, id));
            CommandResources resources = CommandResources.of(tables.toArray(TomlTable[]::new));
            return new BoundMenu(
                    this, id, Mnemonics.parse(resources.string("text").orElse(id)));
        });
    }

    private TomlTable section(Class<?> type, String id) {
        return CommandResources.section(services.resources().table(type), "menus", id);
    }

    JMenu createMenu(MenuSpec spec) {
        JMenu menu = new JMenu();
        Mnemonics text = spec.menu().text();
        menu.setText(text.text());
        if (!services.isMacOS() && text.hasMnemonic()) {
            menu.setMnemonic(text.keyCode());
            menu.setDisplayedMnemonicIndex(text.index());
        }
        MenuContent content = new MenuContent(
                this, menu.getPopupMenu(), "the menu " + spec.menu().id());
        spec.entries().forEach(content::add);
        content.built();
        return menu;
    }

    JMenuBar menuBar(MenuSpec... specs) {
        JMenuBar bar = new JMenuBar();
        for (MenuSpec spec : specs) {
            bar.add(createMenu(spec));
        }
        List<Component> menusOfBar = List.of(bar.getComponents());
        check(menusOfBar, "the menu bar");
        return bar;
    }

    JPopupMenu popupMenu(JComponent invoker, MenuEntry... entries) {
        JPopupMenu popup = new JPopupMenu();
        popup.putClientProperty(CommandAction.INVOKER, invoker);
        MenuContent content = new MenuContent(
                this, popup, "the popup menu of " + invoker.getClass().getName());
        for (MenuEntry entry : entries) {
            content.add(entry);
        }
        content.built();
        invoker.setComponentPopupMenu(popup);
        return popup;
    }

    JToolBar toolBar(ToolBarEntry... entries) {
        JToolBar toolBar = new JToolBar();
        for (ToolBarEntry entry : entries) {
            switch (entry) {
                case BoundCommand command -> {
                    AbstractButton button =
                            command.hasSelectedState() ? command.createToggleButton() : command.createButton();
                    button.setHideActionText(command.hasIcon());
                    button.setFocusable(false);
                    toolBar.add(button);
                }
                case Separator ignored -> toolBar.addSeparator();
            }
        }
        return toolBar;
    }

    /** Creates the component of an entry, or none for a command the macOS application menu shows. */
    List<Component> components(DynamicEntry entry) {
        return switch (entry) {
            case BoundCommand command -> commandItem(command);
            case Separator ignored -> List.of(new JPopupMenu.Separator());
            case MenuSpec spec -> List.of(createMenu(spec));
            case DynamicEntry.Item item -> List.of(item(item));
        };
    }

    List<Component> commandItem(BoundCommand command) {
        if (services.isMacOS() && command.isSystemAction()) {
            return List.of();
        }
        return List.of(command.hasSelectedState() ? command.createCheckBoxMenuItem() : command.createMenuItem());
    }

    private JMenuItem item(DynamicEntry.Item item) {
        Mnemonics text = Mnemonics.parse(item.text());
        JMenuItem menuItem = new JMenuItem(text.text());
        if (!services.isMacOS() && text.hasMnemonic()) {
            menuItem.setMnemonic(text.keyCode());
            menuItem.setDisplayedMnemonicIndex(text.index());
        }
        menuItem.addActionListener(event -> item.action().run());
        return menuItem;
    }

    /** Reports the duplicate mnemonics and shortcuts among the visible items of a menu or a menu bar. */
    void check(List<Component> items, String where) {
        Map<Integer, String> mnemonics = new HashMap<>();
        Map<Object, String> shortcuts = new HashMap<>();
        for (Component component : items) {
            if (!component.isVisible() || !(component instanceof AbstractButton button)) {
                continue;
            }
            String name = button.getText();
            int mnemonic = button.getMnemonic();
            if (mnemonic != 0) {
                String previous = mnemonics.putIfAbsent(mnemonic, name);
                if (previous != null) {
                    services.mode().report(LOGGER, "Duplicate mnemonic in " + where + ": " + previous + " and " + name);
                }
            }
            if (button instanceof JMenuItem item && !(item instanceof JMenu) && item.getAccelerator() != null) {
                String previous = shortcuts.putIfAbsent(item.getAccelerator(), name);
                if (previous != null) {
                    services.mode().report(LOGGER, "Duplicate shortcut in " + where + ": " + previous + " and " + name);
                }
            }
        }
    }

    ViewHost view() {
        return view;
    }

    UiServices services() {
        return services;
    }
}
