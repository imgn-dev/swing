package be.imgn.swing.processor;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/**
 * Checks the classes with subscribers: each has a scope, so that its subscriptions belong to one, and a singleton among
 * them is {@code @Eager}, so that it subscribes before startup. It generates nothing: gaia-processor generates the
 * subscribers' code and checks the methods. Subscribers carry gaia's annotations, so the step gets every root element.
 */
final class SubscribersStep implements Step {

    private final Shared shared;
    private final ProcessingContext context;

    SubscribersStep(Shared shared) {
        this.shared = shared;
        this.context = shared.context();
    }

    @Override
    public Set<String> annotations() {
        return Set.of();
    }

    @Override
    public boolean processesRootElements() {
        return true;
    }

    @Override
    public Set<Element> process(AnnotatedElements elements) {
        List<TypeElement> types = new ArrayList<>();
        for (TypeElement root : ElementFilter.typesIn(elements.rootElements())) {
            collect(root, types);
        }
        context.forEach(types, this::check);
        return Set.of();
    }

    private static void collect(TypeElement type, List<TypeElement> types) {
        types.add(type);
        ElementFilter.typesIn(type.getEnclosedElements()).forEach(nested -> collect(nested, types));
    }

    private void check(TypeElement type) {
        if (!type.getKind().isClass() || !hasSubscribers(type)) {
            return;
        }
        Optional<Scopes> scope = Scopes.of(context, type);
        if (scope.isEmpty()) {
            shared.reporter()
                    .error(
                            type,
                            type.getQualifiedName() + " declares subscribers but has no scope: annotate it with"
                                    + " @Singleton, @WindowScoped or @ViewScoped, so that its subscriptions belong to"
                                    + " the scope it is cached in");
        } else if (scope.get() == Scopes.SINGLETON
                && context.annotation(type, Names.EAGER).isEmpty()) {
            shared.reporter()
                    .error(
                            type,
                            type.getQualifiedName() + " is a @Singleton that declares subscribers but is not @Eager:"
                                    + " annotate it with @Eager, so that it is built with the root injector and"
                                    + " subscribes before startup, even if nothing injects it");
        }
    }

    private boolean hasSubscribers(TypeElement type) {
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (context.annotation(method, Names.SUBSCRIBE).isPresent()
                    || !context.annotationsAnnotatedWith(method, Names.SUBSCRIBING_ANNOTATION)
                            .isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
