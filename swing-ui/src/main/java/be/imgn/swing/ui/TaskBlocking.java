package be.imgn.swing.ui;

import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import java.awt.Component;
import java.util.List;
import javax.swing.JRootPane;
import org.jspecify.annotations.Nullable;

/** Applies the input blocking of a running task. */
final class TaskBlocking {

    private TaskBlocking() {}

    /**
     * Blocks the input for a task.
     *
     * @param services the services
     * @param view the view the task runs in
     * @param task the running task
     * @param block what to block
     * @param command the command that started the task, or {@code null} outside a command
     * @param context how the command was activated, or {@code null} outside a command
     * @return unblocks the input
     */
    static Runnable block(
            UiServices services,
            ViewHost view,
            RunningTask task,
            Block block,
            @Nullable BoundCommand command,
            @Nullable CommandContext context) {
        return switch (block) {
            case NONE -> () -> {};
            case COMMAND -> requireCommand(command).blockItself();
            case COMPONENT -> {
                Component component = context == null ? null : componentOf(context.trigger());
                yield component == null
                        ? requireCommand(command).blockItself()
                        : services.blocker().disable(component);
            }
            case WINDOW ->
                services.blocker().cover(List.of(view.window().window().rootPane()), task);
            case APPLICATION -> services.blocker().cover(rootPanes(services), task);
        };
    }

    private static BoundCommand requireCommand(@Nullable BoundCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("COMMAND and COMPONENT apply only to a task a command starts");
        }
        return command;
    }

    private static List<JRootPane> rootPanes(UiServices services) {
        return services.registry().windows().stream()
                .map(WindowHost::window)
                .map(Window::rootPane)
                .toList();
    }

    private static @Nullable Component componentOf(Trigger trigger) {
        return switch (trigger) {
            case Trigger.ButtonClick click -> click.button();
            case Trigger.PopupMenuSelection selection -> selection.invoker();
            case Trigger.Shortcut shortcut -> shortcut.focusOwner();
            case Trigger.MenuSelection ignored -> null;
            case Trigger.OperatingSystem ignored -> null;
            case Trigger.Programmatic ignored -> null;
        };
    }
}
