package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

/** The fields of a class that resources and window state can reach. */
final class ViewModel {

    private ViewModel() {}

    /**
     * Returns the non-private, non-static fields of a class and its superclasses that generated code in the class's
     * package can reach, by name: those of the class's package, and the public ones; the class's own first.
     */
    static Map<String, VariableElement> accessibleFields(TypeElement type) {
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        instanceFields(type, (field, reachable) -> {
            if (reachable) {
                fields.putIfAbsent(field.getSimpleName().toString(), field);
            }
        });
        return fields;
    }

    /**
     * Returns the non-static fields of a class and its superclasses that generated code in the class's package cannot
     * reach: the private ones, and those of another package that are not public.
     */
    static List<VariableElement> hiddenFields(TypeElement type) {
        List<VariableElement> fields = new ArrayList<>();
        instanceFields(type, (field, reachable) -> {
            if (!reachable) {
                fields.add(field);
            }
        });
        return fields;
    }

    // Gives each non-static field of the class and its superclasses, the class's own first, with whether generated
    // code in the class's package reaches it.
    private static void instanceFields(TypeElement type, BiConsumer<VariableElement, Boolean> consumer) {
        String packageName = Java.packageOf(type).getQualifiedName().toString();
        Optional<TypeElement> current = Optional.of(type);
        while (current.isPresent()) {
            boolean samePackage =
                    Java.packageOf(current.get()).getQualifiedName().contentEquals(packageName);
            for (VariableElement field : ElementFilter.fieldsIn(current.get().getEnclosedElements())) {
                if (!field.getModifiers().contains(Modifier.STATIC)) {
                    boolean reachable = !field.getModifiers().contains(Modifier.PRIVATE)
                            && (samePackage || field.getModifiers().contains(Modifier.PUBLIC));
                    consumer.accept(field, reachable);
                }
            }
            current = Commands.superclass(current.get());
        }
    }

    /** Returns the reachable fields whose type is a {@code java.awt.Component}. */
    static Map<String, VariableElement> componentFields(ProcessingContext context, TypeElement type) {
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        accessibleFields(type).forEach((name, field) -> {
            if (context.isSubtype(field.asType(), Names.COMPONENT)) {
                fields.put(name, field);
            }
        });
        return fields;
    }

    /** Returns whether a field's type is one whose state is saved: a split pane, a tabbed pane or a table. */
    static boolean hasState(ProcessingContext context, VariableElement field) {
        return context.isSubtype(field.asType(), Names.SPLIT_PANE)
                || context.isSubtype(field.asType(), Names.TABBED_PANE)
                || context.isSubtype(field.asType(), Names.TABLE);
    }
}
