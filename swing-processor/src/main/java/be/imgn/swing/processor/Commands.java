package be.imgn.swing.processor;

import be.imgn.gaia.processing.AnnotationReader;
import be.imgn.gaia.processing.ProcessingContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Reads the commands of classes, from sources and class files. */
final class Commands {

    private Commands() {}

    /** Returns the commands a class declares itself, on its methods and its fields, in source order. */
    static List<CommandInfo> declared(ProcessingContext context, TypeElement type) {
        List<CommandInfo> commands = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() == ElementKind.METHOD || member.getKind() == ElementKind.FIELD) {
                context.annotation(member, Names.COMMAND)
                        .ifPresent(annotation -> commands.add(info(context, type, member, annotation)));
            }
        }
        return commands;
    }

    /** Returns the commands of a class and its superclasses, by id: a subclass's command wins over its superclass's. */
    static Map<String, CommandInfo> all(ProcessingContext context, TypeElement type) {
        List<TypeElement> hierarchy = new ArrayList<>();
        Optional<TypeElement> current = Optional.of(type);
        while (current.isPresent()) {
            hierarchy.addFirst(current.get());
            current = superclass(current.get());
        }
        Map<String, CommandInfo> commands = new LinkedHashMap<>();
        for (TypeElement element : hierarchy) {
            declared(context, element).forEach(command -> commands.put(command.id(), command));
        }
        return commands;
    }

    /** Returns the superclass of a class, if it is not {@code Object}. */
    static Optional<TypeElement> superclass(TypeElement type) {
        TypeMirror superclass = type.getSuperclass();
        if (superclass.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        TypeElement element = (TypeElement) ((DeclaredType) superclass).asElement();
        return element.getQualifiedName().contentEquals("java.lang.Object") ? Optional.empty() : Optional.of(element);
    }

    private static CommandInfo info(
            ProcessingContext context, TypeElement type, Element member, AnnotationReader annotation) {
        String id = annotation.string("id");
        boolean task = false;
        boolean takesContext = false;
        if (member instanceof ExecutableElement method) {
            TypeMirror returned = method.getReturnType();
            task = returned.getKind() == TypeKind.DECLARED
                    && ((TypeElement) ((DeclaredType) returned).asElement())
                            .getQualifiedName()
                            .contentEquals(Names.TASK);
            takesContext = method.getParameters().size() == 1
                    && context.isSubtype(method.getParameters().getFirst().asType(), Names.COMMAND_CONTEXT);
        }
        return new CommandInfo(
                id.isEmpty() ? member.getSimpleName().toString() : id,
                member,
                type,
                annotation.string("enabled"),
                annotation.string("selected"),
                annotation.enumConstant("block"),
                task,
                takesContext);
    }

    /** Returns the name of the generated commands class of a class: {@code <Outer><Nested>Commands}. */
    static String generatedName(TypeElement type) {
        return String.join("", Java.simpleNames(type)) + "Commands";
    }
}
