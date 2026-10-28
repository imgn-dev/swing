package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ExitVeto;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The exit vetoes of the window and view scopes, in the order they are asked. */
@Singleton
public final class ExitVetoes {

    private final Map<DisposalScope, Entry> scopes = new LinkedHashMap<>();
    private long sequence;

    @Inject
    ExitVetoes() {}

    /**
     * Registers a veto of a scope, until the scope closes. Registering an instance again does nothing.
     *
     * @param scope the scope the veto's instance belongs to
     * @param veto the veto
     */
    public synchronized void register(DisposalScope scope, ExitVeto veto) {
        Entry entry = scopes.get(scope);
        if (entry == null) {
            entry = new Entry(sequence++, scope.depth());
            scopes.put(scope, entry);
            scope.onClose(() -> withdraw(scope));
        }
        if (!entry.vetoes.contains(veto)) {
            entry.vetoes.add(veto);
        }
    }

    private synchronized void withdraw(DisposalScope scope) {
        scopes.remove(scope);
    }

    /**
     * Returns every veto, in the order they are asked: the scopes from innermost to outermost, each in construction
     * order, then the singletons sorted by class name. An instance is listed once.
     *
     * @param singletons the {@code @IntoSet(ExitVeto.class)} singletons
     * @return the vetoes
     */
    public synchronized List<ExitVeto> ordered(Collection<ExitVeto> singletons) {
        Set<ExitVeto> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ExitVeto> ordered = new ArrayList<>();
        scopes.values().stream()
                .sorted(Comparator.comparingInt((Entry entry) -> entry.depth)
                        .reversed()
                        .thenComparing(Comparator.comparingLong((Entry entry) -> entry.sequence)
                                .reversed()))
                .forEach(entry -> entry.vetoes.stream().filter(seen::add).forEach(ordered::add));
        singletons.stream()
                .sorted(Comparator.comparing(veto -> veto.getClass().getName()))
                .filter(seen::add)
                .forEach(ordered::add);
        return ordered;
    }

    private static final class Entry {
        private final long sequence;
        private final int depth;
        private final List<ExitVeto> vetoes = new ArrayList<>();

        Entry(long sequence, int depth) {
            this.sequence = sequence;
            this.depth = depth;
        }
    }
}
