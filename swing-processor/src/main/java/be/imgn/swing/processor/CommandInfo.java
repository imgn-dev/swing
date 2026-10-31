package be.imgn.swing.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A command of a class, declared on it or inherited: on a handler method, or on a {@code Property<Boolean>} field,
 * which makes it a toggle with no handler.
 *
 * @param id the id
 * @param element the handler method, or the field
 * @param declaring the class that declares the handler or the field
 * @param enabled the name of the {@code enabled} target, empty for none
 * @param selected the name of the {@code selected} target, empty for none
 * @param block the {@code Block} constant
 * @param returnsTask whether the handler returns a {@code Task}
 * @param takesContext whether the handler takes a {@code CommandContext}
 */
record CommandInfo(
        String id,
        Element element,
        TypeElement declaring,
        String enabled,
        String selected,
        String block,
        boolean returnsTask,
        boolean takesContext) {

    /** Returns whether the command is declared on a field, as a toggle with no handler. */
    boolean isField() {
        return element.getKind() == ElementKind.FIELD;
    }

    /** Returns the handler method; only for a command declared on a method. */
    ExecutableElement handler() {
        return (ExecutableElement) element;
    }

    /**
     * Returns the name of the command's method in the generated class: the handler's name, or the id of a command
     * declared on a field.
     */
    String handlerName() {
        return isField() ? id : element.getSimpleName().toString();
    }

    boolean isStandard() {
        return Names.STANDARD_COMMANDS_IDS.contains(id);
    }
}
