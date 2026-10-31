package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** The scopes the processor orders: {@code @Singleton}, then {@code @WindowScoped}, then {@code @ViewScoped}. */
enum Scopes {
    SINGLETON(Names.SINGLETON, "@Singleton"),
    WINDOW(Names.WINDOW_SCOPED, "@WindowScoped"),
    VIEW(Names.VIEW_SCOPED, "@ViewScoped");

    private final String annotation;
    private final String label;

    Scopes(String annotation, String label) {
        this.annotation = annotation;
        this.label = label;
    }

    String label() {
        return label;
    }

    /** Returns whether this scope is narrower than another: shorter-lived, in the order of the constants. */
    boolean isNarrowerThan(Scopes other) {
        return compareTo(other) > 0;
    }

    /**
     * Returns the scope of a class among the three: the one declared on it, else the nearest one its superclasses and
     * superinterfaces declare.
     */
    static Optional<Scopes> of(ProcessingContext context, TypeElement type) {
        Deque<TypeElement> queue = new ArrayDeque<>(List.of(type));
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            TypeElement current = queue.removeFirst();
            if (!seen.add(current.getQualifiedName().toString())) {
                continue;
            }
            for (Scopes scope : values()) {
                if (context.annotation(current, scope.annotation).isPresent()) {
                    return Optional.of(scope);
                }
            }
            supertype(current.getSuperclass()).ifPresent(queue::addLast);
            current.getInterfaces().forEach(supertype -> supertype(supertype).ifPresent(queue::addLast));
        }
        return Optional.empty();
    }

    private static Optional<TypeElement> supertype(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED && ((DeclaredType) type).asElement() instanceof TypeElement element) {
            return Optional.of(element);
        }
        return Optional.empty();
    }
}
