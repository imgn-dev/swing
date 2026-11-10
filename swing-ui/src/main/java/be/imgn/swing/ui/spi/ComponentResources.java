package be.imgn.swing.ui.spi;

import be.imgn.swing.internal.Guarded;
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
import java.util.Objects;
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
import org.jspecify.annotations.Nullable;

/**
 * The resources of a view class, read by field path and property, as the generated injectors write them. A field path
 * is the name of a field of the view, or names joined by {@code .}, such as {@code home.streetLabel}, which reaches the
 * nested table of a component field of the component that the view's field {@code home} holds.
 */
public final class ComponentResources {

    private static final int RGB = 7;
    private static final int RGBA = 9;
    private static final int HEX = 16;
    private static final int ALPHA_SHIFT = 24;

    private static final System.Logger LOGGER = System.getLogger(ComponentResources.class.getName());

    private final TomlTable table;
    private final Function<String, Icon> icons;
    private final boolean mnemonics;
    // The view class, when a refresh applies the resources again; null at the end of a view's build.
    private final @Nullable Class<?> refreshed;

    /**
     * Creates the resources of a view class.
     *
     * @param table the merged resources of the class
     * @param icons loads an icon by its path, relative to the package of the class, or of the superclass whose file
     *     names it
     * @param mnemonics whether mnemonics are set; not on macOS
     */
    public ComponentResources(TomlTable table, Function<String, Icon> icons, boolean mnemonics) {
        this(table, icons, mnemonics, null);
    }

    private ComponentResources(
            TomlTable table, Function<String, Icon> icons, boolean mnemonics, @Nullable Class<?> refreshed) {
        this.table = table;
        this.icons = icons;
        this.mnemonics = mnemonics;
        this.refreshed = refreshed;
    }

    /**
     * Creates the resources of a view class that a refresh applies again. They give no {@code enabled} or
     * {@code visible}, which the view's code owns once it is built; and a value that can't be read, such as one of the
     * wrong type in a reloaded file, is logged as a warning and given as absent, so that the refresh goes on.
     *
     * @param table the merged resources of the class
     * @param icons loads an icon by its path, as for {@link #ComponentResources(TomlTable, Function, boolean)}
     * @param mnemonics whether mnemonics are set; not on macOS
     * @param view the view class, which warnings name
     * @return the resources
     * @throws NullPointerException if an argument is {@code null}
     */
    public static ComponentResources refreshing(
            TomlTable table, Function<String, Icon> icons, boolean mnemonics, Class<?> view) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(icons, "icons");
        return new ComponentResources(table, icons, mnemonics, Objects.requireNonNull(view, "view"));
    }

    // Reads and converts a property; at a refresh, a value that can't be converted is a warning.
    private <T> Optional<T> read(String field, String key, Function<TomlValue, T> convert) {
        Class<?> view = refreshed;
        if (view == null) {
            return value(field, key).map(convert);
        }
        if ("enabled".equals(key) || "visible".equals(key)) {
            return Optional.empty();
        }
        try {
            return value(field, key).map(convert);
        } catch (ClassCastException | IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            skipped(view, field, key, e);
            return Optional.empty();
        }
    }

    private static void skipped(Class<?> view, String field, String key, Exception e) {
        LOGGER.log(
                System.Logger.Level.WARNING,
                "Skipped the property " + key + " of the field " + field + " of " + view.getName() + ": " + e);
    }

    /**
     * Applies a property, as the generated injectors do for each one: at the end of a view's build, an exception
     * propagates; at a refresh, it is logged as a warning, and the property is skipped, so that the refresh goes on.
     *
     * @param field the field path: a field name, or names joined by {@code .}, such as {@code home.streetLabel}
     * @param key the property
     * @param setter sets the property on the component
     * @throws NullPointerException if an argument is {@code null}
     */
    public void apply(String field, String key, Runnable setter) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(setter, "setter");
        Class<?> view = refreshed;
        if (view == null) {
            setter.run();
            return;
        }
        Exception thrown = Guarded.runCatchingExceptions(setter);
        if (thrown != null) {
            skipped(view, field, key, thrown);
        }
    }

    // Field names are Java identifiers, so a '.' always separates two of them.
    private Optional<TomlValue> value(String field, String key) {
        TomlTable properties = table;
        for (String name : field.split("\\.", -1)) {
            if (!(properties.entries().get(name) instanceof TomlTable nested)) {
                return Optional.empty();
            }
            properties = nested;
        }
        return Optional.ofNullable(properties.entries().get(key));
    }

    /**
     * Returns a string property.
     *
     * @param field the field path: a field name, or names joined by {@code .}, such as {@code home.streetLabel}
     * @param key the property
     * @return the value, if given
     */
    public Optional<String> string(String field, String key) {
        return read(field, key, value -> ((TomlString) value).value());
    }

    /**
     * Returns an integer property.
     *
     * @param field the field path: a field name, or names joined by {@code .}, such as {@code home.streetLabel}
     * @param key the property
     * @return the value, if given
     */
    public Optional<Integer> integer(String field, String key) {
        return read(field, key, value -> Math.toIntExact(((TomlInteger) value).value()));
    }

    /**
     * Returns a boolean property.
     *
     * @param field the field path: a field name, or names joined by {@code .}, such as {@code home.streetLabel}
     * @param key the property
     * @return the value, if given
     */
    public Optional<Boolean> bool(String field, String key) {
        return read(field, key, value -> ((TomlBoolean) value).value());
    }

    /**
     * Returns a color property: {@code "#rrggbb"} or {@code "#rrggbbaa"}.
     *
     * @param field the field path: a field name, or names joined by {@code .}, such as {@code home.streetLabel}
     * @param key the property
     * @return the value, if given
     */
    public Optional<Color> color(String field, String key) {
        return read(field, key, value -> parseColor(((TomlString) value).value()));
    }

    /**
     * Returns a font property, in {@link Font#decode(String)}'s syntax.
     *
     * @param field the field path: a field name, or names joined by {@code .}, such as {@code home.streetLabel}
     * @param key the property
     * @return the value, if given
     */
    public Optional<Font> font(String field, String key) {
        return read(field, key, value -> Font.decode(((TomlString) value).value()));
    }

    /**
     * Returns an icon property: a path relative to the package of the class whose file names it, resolved against the
     * class's package first, then its superclasses'.
     *
     * @param field the field path: a field name, or names joined by {@code .}, such as {@code home.streetLabel}
     * @param key the property
     * @return the icon, if given
     */
    public Optional<Icon> icon(String field, String key) {
        return read(field, key, value -> icons.apply(((TomlString) value).value()));
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
     * Returns whether a component is a tab of a {@link JTabbedPane}, whose title {@link #setTabTitle} can set. The
     * generated injectors skip the {@code tab-title} of a component that is not a tab yet, so that a tab can be added
     * after the view is built.
     *
     * @param component the component
     * @return {@code true} if its parent is a {@link JTabbedPane} that holds it as a tab
     * @throws NullPointerException if {@code component} is {@code null}
     */
    public static boolean isTab(Component component) {
        Objects.requireNonNull(component, "component");
        return component.getParent() instanceof JTabbedPane tabs && tabs.indexOfComponent(component) >= 0;
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
