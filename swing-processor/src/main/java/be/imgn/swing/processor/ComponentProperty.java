package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import java.util.Arrays;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/** A property that resources write to a component field, with the types it applies to and the code that sets it. */
enum ComponentProperty {
    TEXT("text", Kind.STRING, "%s.setText(value)"),
    TOOL_TIP_TEXT("tool-tip-text", Kind.STRING, "%s.setToolTipText(value)"),
    ICON("icon", Kind.ICON, "%s.setIcon(value)"),
    FONT("font", Kind.FONT, "%s.setFont(value)"),
    FOREGROUND("foreground", Kind.COLOR, "%s.setForeground(value)"),
    BACKGROUND("background", Kind.COLOR, "%s.setBackground(value)"),
    ENABLED("enabled", Kind.BOOLEAN, "%s.setEnabled(value)"),
    VISIBLE("visible", Kind.BOOLEAN, "%s.setVisible(value)"),
    ACCESSIBLE_NAME("accessible-name", Kind.STRING, "@.setAccessibleName(%s, value)"),
    ACCESSIBLE_DESCRIPTION("accessible-description", Kind.STRING, "@.setAccessibleDescription(%s, value)"),
    COLUMNS("columns", Kind.INTEGER, "%s.setColumns(value)"),
    ROWS("rows", Kind.INTEGER, "%s.setRows(value)"),
    TITLE("title", Kind.STRING, "@.setTitle(%s, value)"),
    TAB_TITLE("tab-title", Kind.STRING, "@.setTabTitle(%s, value)");

    private final String key;
    private final Kind kind;
    // The code that applies the value: %s is the field, @ the ComponentResources class.
    private final String setter;

    ComponentProperty(String key, Kind kind, String setter) {
        this.key = key;
        this.kind = kind;
        this.setter = setter;
    }

    String key() {
        return key;
    }

    Kind kind() {
        return kind;
    }

    static Optional<ComponentProperty> of(String key) {
        return Arrays.stream(values())
                .filter(property -> property.key.equals(key))
                .findFirst();
    }

    /** Returns whether the property applies to a component type. */
    boolean appliesTo(ProcessingContext context, TypeMirror type) {
        return switch (this) {
            case TEXT -> hasSetter(context, type, "setText", "java.lang.String");
            case TOOL_TIP_TEXT, TITLE -> context.isSubtype(type, Names.JCOMPONENT);
            case ICON -> hasSetter(context, type, "setIcon", Names.ICON);
            case ACCESSIBLE_NAME, ACCESSIBLE_DESCRIPTION -> context.isSubtype(type, Names.ACCESSIBLE);
            case COLUMNS -> hasSetter(context, type, "setColumns", "int");
            case ROWS -> hasSetter(context, type, "setRows", "int");
            case FONT, FOREGROUND, BACKGROUND, ENABLED, VISIBLE, TAB_TITLE -> true;
        };
    }

    /** Returns the statement that writes the property to a field of the view, read with {@code resources}. */
    String statement(ProcessingContext context, TypeMirror type, String field, String componentResources) {
        String read = "resources.%s(%s, %s)".formatted(kind.reader, Java.literal(field), Java.literal(key));
        String target = "view." + field;
        boolean mnemonics = this == TEXT
                && (context.isSubtype(type, Names.ABSTRACT_BUTTON) || context.isSubtype(type, Names.JLABEL));
        String apply = mnemonics
                ? "resources.setText(" + target + ", value)"
                : setter.formatted(target).replace("@", componentResources);
        return "        " + read + ".ifPresent(value -> " + apply + ");";
    }

    private static boolean hasSetter(ProcessingContext context, TypeMirror type, String name, String parameter) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        for (ExecutableElement method :
                ElementFilter.methodsIn(context.elements().getAllMembers(element))) {
            if (method.getSimpleName().contentEquals(name)
                    && method.getModifiers().contains(Modifier.PUBLIC)
                    && method.getParameters().size() == 1
                    && method.getParameters().getFirst().asType().toString().equals(parameter)) {
                return true;
            }
        }
        return false;
    }

    /** The kind of value of a property. */
    enum Kind {
        STRING("string"),
        BOOLEAN("bool"),
        INTEGER("integer"),
        COLOR("color"),
        FONT("font"),
        ICON("icon");

        private final String reader;

        Kind(String reader) {
            this.reader = reader;
        }
    }
}
