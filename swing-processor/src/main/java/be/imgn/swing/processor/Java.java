package be.imgn.swing.processor;

import be.imgn.gaia.processing.MoreElements;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/** Helpers for the Java source the processor writes. */
final class Java {

    private Java() {}

    /**
     * Returns a Java string literal.
     *
     * @param text the text
     * @return the literal, with its quotes
     */
    static String literal(String text) {
        StringBuilder literal = new StringBuilder("\"");
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            switch (c) {
                case '"' -> literal.append("\\\"");
                case '\\' -> literal.append("\\\\");
                case '\n' -> literal.append("\\n");
                case '\r' -> literal.append("\\r");
                case '\t' -> literal.append("\\t");
                default -> literal.append(c);
            }
        }
        return literal.append('"').toString();
    }

    /** Returns the simple names from the top-level type to a type. */
    static List<String> simpleNames(TypeElement type) {
        List<String> names = new ArrayList<>();
        Element current = type;
        while (current instanceof TypeElement nested) {
            names.addFirst(nested.getSimpleName().toString());
            current = nested.getEnclosingElement();
        }
        return names;
    }

    /** Returns the package of a type. */
    static PackageElement packageOf(Element element) {
        return MoreElements.packageOf(element);
    }

    /** Returns the qualified name of a type generated in the package of another, with a simple name. */
    static String qualified(TypeElement origin, String simpleName) {
        String packageName = packageOf(origin).getQualifiedName().toString();
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    /** Returns the name {@code $Outer$Inner$$Suffix} of a type generated for another. */
    static String dollarName(TypeElement type, String suffix) {
        return "$" + String.join("$", simpleNames(type)) + "$$" + suffix;
    }

    /** Returns whether a name is a Java identifier that is not a keyword. */
    static boolean isIdentifier(String name) {
        return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name);
    }
}
