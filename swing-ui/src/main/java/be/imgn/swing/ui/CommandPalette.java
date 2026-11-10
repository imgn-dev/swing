package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.swing.Scope;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.swing.Action;
import javax.swing.KeyStroke;
import org.jspecify.annotations.Nullable;

/**
 * The command palette of a view's window: it finds a command by typing part of its text, and runs it.
 *
 * <p>It lists the commands that the views of the window created, enabled at the time, but {@code commandPalette()}
 * itself; on macOS, the system actions too, since the palette is not a menu. A query matches a command whose text holds
 * the query's characters in order, ignoring case and accents; the matches are ranked by how they match: the text starts
 * with the query, then a word of it does, then the text contains it, then its characters match only in order; then by
 * the commands run from the palette most recently, then by text in the display locale.
 */
@ViewScoped
public final class CommandPalette {

    private final ViewHost view;
    private final UiServices services;
    private final Dialogs dialogs;
    private final PaletteHistory history;

    @Inject
    CommandPalette(Scope scope, UiServices services, Dialogs dialogs, PaletteHistory history) {
        this.view = services.registry().viewOf(scope);
        this.services = services;
        this.dialogs = dialogs;
        this.history = history;
    }

    /**
     * Returns the entries of the palette: the commands of the views of the window, enabled now, the commands run from
     * the palette most recently first, then by text.
     *
     * @return the entries
     */
    public List<PaletteEntry> entries() {
        List<BoundCommand> commands = new ArrayList<>();
        for (ViewHost host : view.window().views()) {
            for (BoundCommand command :
                    host.injector().getInstance(CommandRegistry.class).commands()) {
                if (!isPalette(command)
                        && command.isEnabledNow()
                        && commands.stream()
                                .noneMatch(listed -> listed.identity().equals(command.identity()))) {
                    commands.add(command);
                }
            }
        }
        Collator collator = Collator.getInstance(services.resources().locale());
        return commands.stream()
                .sorted(Comparator.comparingInt(history::place)
                        .thenComparing(command -> command.text().text(), collator))
                .map(command -> new PaletteEntry(command, command.text().text(), shortcut(command)))
                .toList();
    }

    /**
     * Returns the entries that a query matches, ranked as the palette ranks them.
     *
     * @param query the query
     * @return the matching entries, best first
     * @throws NullPointerException if {@code query} is {@code null}
     */
    public List<PaletteEntry> search(String query) {
        return search(entries(), query);
    }

    /**
     * Returns the entries of a list that a query matches, ranked by how they match, then in the list's order: what the
     * palette's dialog shows as the user types.
     *
     * @param entries the entries, in the order of {@link #entries()}
     * @param query the query
     * @return the matching entries, best first; every entry for an empty query
     * @throws NullPointerException if an argument is {@code null}
     */
    public static List<PaletteEntry> search(List<PaletteEntry> entries, String query) {
        Objects.requireNonNull(entries, "entries");
        String normalized = CommandSearch.normalize(Objects.requireNonNull(query, "query"));
        if (normalized.isEmpty()) {
            return List.copyOf(entries);
        }
        List<PaletteEntry> matches = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        for (PaletteEntry entry : entries) {
            int rank = CommandSearch.rank(CommandSearch.normalize(entry.text()), normalized);
            if (rank != CommandSearch.NO_MATCH) {
                int at = 0;
                while (at < ranks.size() && ranks.get(at) <= rank) {
                    at++;
                }
                matches.add(at, entry);
                ranks.add(at, rank);
            }
        }
        return List.copyOf(matches);
    }

    /**
     * Shows the palette over the view's window, with {@link Dialogs#chooseCommand(List)}; a chosen entry's command runs
     * once the palette has closed and the focus has returned to the component that had it, in a later turn of the EDT,
     * with a {@link Trigger.Palette} trigger and no modifier.
     */
    public void show() {
        Component focused =
                KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        dialogs.chooseCommand(entries()).ifPresent(chosen -> {
            history.ran(chosen.command());
            Runnable run = () -> services.executor()
                    .execute(() -> chosen.command().execute(CommandContext.of(new Trigger.Palette())));
            whenFocused(focused, run);
        });
    }

    // The focus comes back to the component that had it through window events that arrive after the palette closed:
    // the command runs once it has, so that copy() copies from that component. A component no longer shown, or none,
    // doesn't wait.
    private static void whenFocused(@Nullable Component focused, Runnable run) {
        if (focused == null || !focused.isShowing() || focused.isFocusOwner()) {
            run.run();
            return;
        }
        focused.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                focused.removeFocusListener(this);
                run.run();
            }
        });
        focused.requestFocusInWindow();
    }

    // The shortcut the command has in the window: its effective one, unless another command took it there.
    private static @Nullable KeyStroke shortcut(BoundCommand command) {
        return command.asAction().getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke keyStroke ? keyStroke : null;
    }

    private static boolean isPalette(BoundCommand command) {
        return command.owner() == StandardCommands.class && command.id().equals(Standard.COMMAND_PALETTE.id());
    }
}
