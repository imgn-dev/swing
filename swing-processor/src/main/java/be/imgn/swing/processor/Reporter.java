package be.imgn.swing.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.toml.TomlPosition;
import javax.lang.model.element.Element;

/**
 * Reports diagnostics about resources: attached to a Java element, with the file, line and column in the message, as
 * {@code CounterApp_fr.toml:3:1: ...}.
 */
final class Reporter {

    private final ProcessingContext context;

    Reporter(ProcessingContext context) {
        this.context = context;
    }

    void error(Element element, ResourceFile file, TomlPosition position, String message) {
        context.diagnostics().on(element).error(located(file, position, message));
    }

    void error(Element element, String message) {
        context.diagnostics().on(element).error(message);
    }

    void warning(Element element, ResourceFile file, TomlPosition position, String message) {
        context.diagnostics().on(element).warning(located(file, position, message));
    }

    void warning(Element element, String message) {
        context.diagnostics().on(element).warning(message);
    }

    static String located(ResourceFile file, TomlPosition position, String message) {
        return file.name() + ":" + position.line() + ":" + position.column() + ": " + message;
    }
}
