package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.toml.TomlTable;
import be.imgn.toml.TomlValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/** The {@code @Exposed} fields of a class, which resources and window state reach. */
final class ViewModel {

    private ViewModel() {}

    /**
     * Returns the {@code @Exposed}, non-private, non-static fields of a class and its superclasses that generated code
     * in the class's package can reach, by name: those of the class's package, and the public ones; the class's own
     * first.
     */
    static Map<String, VariableElement> accessibleFields(ProcessingContext context, TypeElement type) {
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        exposedFields(context, type, (field, reachable) -> {
            if (reachable) {
                fields.putIfAbsent(field.getSimpleName().toString(), field);
            }
        });
        return fields;
    }

    /**
     * Returns the {@code @Exposed}, non-static fields of a class and its superclasses that generated code in the
     * class's package cannot reach: the private ones, and those of another package that are not public.
     */
    static List<VariableElement> hiddenFields(ProcessingContext context, TypeElement type) {
        List<VariableElement> fields = new ArrayList<>();
        exposedFields(context, type, (field, reachable) -> {
            if (!reachable) {
                fields.add(field);
            }
        });
        return fields;
    }

    // Gives each @Exposed, non-static field of the class and its superclasses, the class's own first, with whether
    // generated code in the class's package reaches it.
    private static void exposedFields(
            ProcessingContext context, TypeElement type, BiConsumer<VariableElement, Boolean> consumer) {
        String packageName = Java.packageOf(type).getQualifiedName().toString();
        Optional<TypeElement> current = Optional.of(type);
        while (current.isPresent()) {
            boolean samePackage =
                    Java.packageOf(current.get()).getQualifiedName().contentEquals(packageName);
            for (VariableElement field : ElementFilter.fieldsIn(current.get().getEnclosedElements())) {
                if (!field.getModifiers().contains(Modifier.STATIC)
                        && context.annotation(field, Names.EXPOSED).isPresent()) {
                    boolean reachable = !field.getModifiers().contains(Modifier.PRIVATE)
                            && (samePackage || field.getModifiers().contains(Modifier.PUBLIC));
                    consumer.accept(field, reachable);
                }
            }
            current = Commands.superclass(current.get());
        }
    }

    /** Returns the reachable {@code @Exposed} fields whose type is a {@code java.awt.Component}. */
    static Map<String, VariableElement> componentFields(ProcessingContext context, TypeElement type) {
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        accessibleFields(context, type).forEach((name, field) -> {
            if (context.isSubtype(field.asType(), Names.COMPONENT)) {
                fields.put(name, field);
            }
        });
        return fields;
    }

    /**
     * Returns the {@code @Exposed} component field of a component type with a name, declared or inherited: the nearest
     * field of that name, as Java resolves it, if it is an {@code @Exposed}, non-static component field. A
     * package-private field is inherited only through classes of its own package: past a class of another package, it
     * is no field of the type.
     */
    static Optional<VariableElement> componentField(ProcessingContext context, TypeMirror type, String name) {
        if (type.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        Optional<TypeElement> current = Optional.of((TypeElement) ((DeclaredType) type).asElement());
        Set<String> packages = new HashSet<>();
        while (current.isPresent()) {
            String owner = Java.packageOf(current.get()).getQualifiedName().toString();
            for (VariableElement field : ElementFilter.fieldsIn(current.get().getEnclosedElements())) {
                if (field.getSimpleName().contentEquals(name)) {
                    boolean notInherited = Collections.disjoint(
                                    field.getModifiers(), Set.of(Modifier.PUBLIC, Modifier.PROTECTED, Modifier.PRIVATE))
                            && !packages.stream().allMatch(owner::equals);
                    boolean component = !notInherited
                            && !field.getModifiers().contains(Modifier.STATIC)
                            && context.annotation(field, Names.EXPOSED).isPresent()
                            && context.isSubtype(field.asType(), Names.COMPONENT);
                    return component ? Optional.of(field) : Optional.empty();
                }
            }
            packages.add(owner);
            current = Commands.superclass(current.get());
        }
        return Optional.empty();
    }

    /**
     * Returns the nested component fields that the resources of a view class and of its superclasses name, in every
     * file of theirs the processor finds, variants included, by field path such as {@code home.streetLabel}, enclosing
     * paths first. Only the fields that generated code in the view's package reaches are given: the checks report the
     * others. Following only the named paths, not every component field of every component, ends for a type that holds
     * itself.
     */
    static Map<String, VariableElement> nestedFields(ProcessingContext context, ResourceFiles files, TypeElement type) {
        String packageName = Java.packageOf(type).getQualifiedName().toString();
        Map<String, VariableElement> fields = componentFields(context, type);
        Map<String, VariableElement> nested = new LinkedHashMap<>();
        Optional<TypeElement> current = Optional.of(type);
        while (current.isPresent()) {
            String owner = Java.packageOf(current.get()).getQualifiedName().toString();
            for (ResourceFile file :
                    files.of(owner, current.get().getSimpleName().toString())) {
                file.parsed().ifPresent(document -> {
                    TomlTable root = document.root();
                    collectNested(context, packageName, fields, root, nested);
                    if (root.entries().get("variants") instanceof TomlTable variants) {
                        for (TomlValue variant : variants.entries().values()) {
                            if (variant instanceof TomlTable table) {
                                collectNested(context, packageName, fields, table, nested);
                            }
                        }
                    }
                });
            }
            current = Commands.superclass(current.get());
        }
        return nested;
    }

    // The tables of a base or variant table that name the view's component fields, other than its commands, menus and
    // variants, each followed through its nested tables.
    private static void collectNested(
            ProcessingContext context,
            String packageName,
            Map<String, VariableElement> fields,
            TomlTable table,
            Map<String, VariableElement> nested) {
        table.entries().forEach((key, value) -> {
            VariableElement field = fields.get(key);
            if (!Set.of("commands", "menus", "variants").contains(key)
                    && field != null
                    && value instanceof TomlTable properties) {
                collectNested(context, packageName, field.asType(), key, properties, nested);
            }
        });
    }

    private static void collectNested(
            ProcessingContext context,
            String packageName,
            TypeMirror type,
            String path,
            TomlTable properties,
            Map<String, VariableElement> nested) {
        properties.entries().forEach((key, value) -> {
            if (value instanceof TomlTable table) {
                componentField(context, type, key)
                        .filter(field -> reachable(field, packageName))
                        .ifPresent(field -> {
                            String fieldPath = path + "." + key;
                            nested.putIfAbsent(fieldPath, field);
                            collectNested(context, packageName, field.asType(), fieldPath, table, nested);
                        });
            }
        });
    }

    /** Returns whether generated code in a package reaches a field: not private, and public or in that package. */
    static boolean reachable(VariableElement field, String packageName) {
        return !field.getModifiers().contains(Modifier.PRIVATE)
                && (field.getModifiers().contains(Modifier.PUBLIC)
                        || Java.packageOf(field).getQualifiedName().contentEquals(packageName));
    }

    /** Returns whether a field's type is one whose state is saved: a split pane, a tabbed pane or a table. */
    static boolean hasState(ProcessingContext context, VariableElement field) {
        return context.isSubtype(field.asType(), Names.SPLIT_PANE)
                || context.isSubtype(field.asType(), Names.TABBED_PANE)
                || context.isSubtype(field.asType(), Names.TABLE);
    }
}
