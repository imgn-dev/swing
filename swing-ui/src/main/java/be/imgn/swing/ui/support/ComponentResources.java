package be.imgn.swing.ui.support;

import be.imgn.swing.ui.internal.Mnemonics;
import be.imgn.toml.TomlBoolean;
import be.imgn.toml.TomlInteger;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/** The resources of a view class, read by field and property, as the generated injectors write them. */
public final class ComponentResources {

    private static final int RGB = 7;
    private static final int RGBA = 9;
    private static final int HEX = 16;
    private static final int ALPHA_SHIFT = 24;

    private final TomlTable table;
    private final Function<String, Icon> icons;
    private final boolean mnemonics;

    /**
     * Creates the resources of a view class.
     *
     * @param table the merged resources of the class
     * @param icons loads an icon by its path, relative to the package of the class, or of the superclass whose file
     *     names it
     * @param mnemonics whether mnemonics are set; not on macOS
     */
    public ComponentResources(TomlTable table, Function<String, Icon> icons, boolean mnemonics) {
        this.table = table;
        this.icons = icons;
        this.mnemonics = mnemonics;
    }

    private Optional<TomlValue> value(String field, String key) {
        return table.entries().get(field) instanceof TomlTable properties
                ? Optional.ofNullable(properties.entries().get(key))
                : Optional.empty();
    }

    /**
     * Returns a string property.
     *
     * @param field the field
     * @param key the property
     * @return the value, if given
     */
    public Optional<String> string(String field, String key) {
        return value(field, key).map(value -> ((TomlString) value).value());
    }

    /**
     * Returns an integer property.
     *
     * @param field the field
     * @param key the property
     * @return the value, if given
     */
    public Optional<Integer> integer(String field, String key) {
        return value(field, key).map(value -> Math.toIntExact(((TomlInteger) value).value()));
    }

    /**
     * Returns a boolean property.
     *
     * @param field the field
     * @param key the property
     * @return the value, if given
     */
    public Optional<Boolean> bool(String field, String key) {
        return value(field, key).map(value -> ((TomlBoolean) value).value());
    }

    /**
     * Returns a color property: {@code "#rrggbb"} or {@code "#rrggbbaa"}.
     *
     * @param field the field
     * @param key the property
     * @return the value, if given
     */
    public Optional<Color> color(String field, String key) {
        return string(field, key).map(ComponentResources::parseColor);
    }

    /**
     * Returns a font property, in {@link Font#decode(String)}'s syntax.
     *
     * @param field the field
     * @param key the property
     * @return the value, if given
     */
    public Optional<Font> font(String field, String key) {
        return string(field, key).map(Font::decode);
    }

    /**
     * Returns an icon property: a path relative to the package of the class whose file names it, resolved against the
     * class's package first, then its superclasses'.
     *
     * @param field the field
     * @param key the property
     * @return the icon, if given
     */
    public Optional<Icon> icon(String field, String key) {
        return string(field, key).map(icons);
    }

    /**
     * Parses a color.
     *
     * @param text {@code "#rrggbb"} or {@code "#rrggbbaa"}
     * @return the color
     * @throws IllegalArgumentException if the text is not a color
     */
    public static Color parseColor(String text) {
        if (!text.startsWith("#") || (text.length() != RGB && text.length() != RGBA)) {
            throw new IllegalArgumentException("Not a color: " + text);
        }
        try {
            int rgb = Integer.parseUnsignedInt(text.substring(1, RGB), HEX);
            if (text.length() == RGB) {
                return new Color(rgb);
            }
            int alpha = Integer.parseUnsignedInt(text.substring(RGB), HEX);
            return new Color((alpha << ALPHA_SHIFT) | rgb, true);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a color: " + text, e);
        }
    }

    /**
     * Sets the text of a component: for a button or a label, {@code &} marks the mnemonic, except on macOS.
     *
     * @param component the component
     * @param text the text
     * @throws IllegalArgumentException if the component is neither a button nor a label
     */
    public void setText(Component component, String text) {
        Mnemonics parsed = Mnemonics.parse(text);
        switch (component) {
            case AbstractButton button -> {
                button.setText(parsed.text());
                if (mnemonics && parsed.hasMnemonic()) {
                    button.setMnemonic(parsed.keyCode());
                    button.setDisplayedMnemonicIndex(parsed.index());
                }
            }
            case JLabel label -> {
                label.setText(parsed.text());
                if (mnemonics && parsed.hasMnemonic()) {
                    label.setDisplayedMnemonic(parsed.keyCode());
                    label.setDisplayedMnemonicIndex(parsed.index());
                }
            }
            default ->
                throw new IllegalArgumentException(
                        "No text property on " + component.getClass().getName());
        }
    }

    /**
     * Sets the accessible name of a component.
     *
     * @param component the component
     * @param name the name
     */
    public static void setAccessibleName(Component component, String name) {
        component.getAccessibleContext().setAccessibleName(name);
    }

    /**
     * Sets the accessible description of a component.
     *
     * @param component the component
     * @param description the description
     */
    public static void setAccessibleDescription(Component component, String description) {
        component.getAccessibleContext().setAccessibleDescription(description);
    }

    /**
     * Sets the title of a component's {@link TitledBorder}, creating the border when the component has none.
     *
     * @param component the component
     * @param title the title
     * @throws IllegalStateException if the component has another kind of border
     */
    public static void setTitle(JComponent component, String title) {
        Border border = component.getBorder();
        if (border == null) {
            component.setBorder(BorderFactory.createTitledBorder(title));
        } else if (border instanceof TitledBorder titled) {
            titled.setTitle(title);
            component.repaint();
        } else {
            throw new IllegalStateException(
                    "Cannot set the title of a " + component.getClass().getName() + ": its border is a "
                            + border.getClass().getName() + ", not a TitledBorder");
        }
    }

    /**
     * Sets the title of a component's tab, in the {@link JTabbedPane} that holds it.
     *
     * @param component the component
     * @param title the title
     * @throws IllegalStateException if the component is not a tab
     */
    public static void setTabTitle(Component component, String title) {
        Container parent = component.getParent();
        int index = parent instanceof JTabbedPane tabs ? tabs.indexOfComponent(component) : -1;
        if (index < 0) {
            throw new IllegalStateException(
                    "Cannot set the tab title of a " + component.getClass().getName() + ": it is not a tab");
        }
        ((JTabbedPane) parent).setTitleAt(index, title);
    }
}
