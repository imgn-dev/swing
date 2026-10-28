package be.imgn.swing.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A command of a class, declared on it or inherited.
 *
 * @param id the id
 * @param handler the handler method
 * @param declaring the class that declares the handler
 * @param enabled the name of the {@code enabled} target, empty for none
 * @param selected the name of the {@code selected} target, empty for none
 * @param block the {@code Block} constant
 * @param returnsTask whether the handler returns a {@code Task}
 * @param takesContext whether the handler takes a {@code CommandContext}
 */
record CommandInfo(
        String id,
        ExecutableElement handler,
        TypeElement declaring,
        String enabled,
        String selected,
        String block,
        boolean returnsTask,
        boolean takesContext) {

    String handlerName() {
        return handler.getSimpleName().toString();
    }

    boolean isStandard() {
        return Names.STANDARD_COMMANDS_IDS.contains(id);
    }
}
