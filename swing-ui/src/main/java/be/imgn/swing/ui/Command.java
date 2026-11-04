package be.imgn.swing.ui;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a command: its handler is the annotated method, a non-private, non-static method returning {@code void} or
 * {@code Task<?>}, that takes no parameter or a single {@link CommandContext}. It is called on the EDT.
 *
 * <p>On a field, it declares a toggle with no handler: the field is a non-private, non-static
 * {@code Property<Boolean>}, which activating the command flips, and which the command's selected state follows. The id
 * defaults to the field's name, and names the command's method in the generated class. Such a command takes no
 * {@link #selected()}, {@link #block()}, {@link #concurrency()} nor {@link #cancels()}; annotating any other field is a
 * compile error. Generated code reads the field, which needs no {@link Exposed}: {@code be.imgn.swing.ui.Command} joins
 * the annotations that Checkstyle's {@code VisibilityModifier} accepts, as {@code Exposed} does.
 *
 * <p>The processor generates a {@code <Class>Commands} bean for each class that declares commands, with one method per
 * command that returns its {@link BoundCommand}. Text, icon, shortcut and tooltip come from the class's resources,
 * under {@code [commands.<id>]}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Command {

    /**
     * Returns the id of the command, which is also its TOML key and the Swing action command.
     *
     * @return the id; empty for the method name
     */
    String id() default "";

    /**
     * Returns the name of an {@link Exposed} field or a non-private no-argument method of the class whose type is
     * {@code Observable<Boolean>}, which tells whether the command is enabled.
     *
     * @return the name; empty for a command that is always enabled
     */
    String enabled() default "";

    /**
     * Returns the name of an {@link Exposed} field or a non-private no-argument method of the class whose type is
     * {@code Property<Boolean>}, which makes the command a toggle: activating it flips the property, then calls the
     * handler.
     *
     * @return the name; empty for a command that is not a toggle
     */
    String selected() default "";

    /**
     * Returns what is blocked while the task the handler returns runs. Allowed only on a handler that returns a
     * {@code Task}.
     *
     * @return the input blocking
     */
    Block block() default Block.NONE;

    /**
     * Returns what an activation does while a task the command started still runs. A concurrency other than
     * {@link Concurrency#PARALLEL} is allowed only on a handler that returns a {@code Task}, and neither with
     * {@link Block#COMMAND} nor {@link Block#COMPONENT}.
     *
     * @return the concurrency
     */
    Concurrency concurrency() default Concurrency.PARALLEL;

    /**
     * Returns the id of the command this one stops: a command of the same class, declared or inherited, whose handler
     * returns a {@code Task}. The stop command is enabled while that command is {@link BoundCommand#running()}; its
     * activation cancels that command's tasks, then calls its own handler, which returns {@code void} and may do
     * nothing. A stop command has neither {@link #enabled()}, {@link #selected()}, {@link #block()} nor
     * {@link #concurrency()}, and is declared on a method.
     *
     * @return the id of the command to stop; empty for a command that stops none
     */
    String cancels() default "";
}
