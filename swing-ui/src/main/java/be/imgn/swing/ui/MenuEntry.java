package be.imgn.swing.ui;

import be.imgn.swing.Observable;
import java.util.List;
import java.util.Objects;

/** An entry of a menu description: a command, a separator, a submenu or a dynamic section. */
public sealed interface MenuEntry permits BoundCommand, Separator, MenuSpec, MenuEntry.Dynamic {

    /** The separator. */
    Separator SEPARATOR = Separator.INSTANCE;

    /**
     * Returns a section of a menu whose entries come from an observable. When the observable changes, the framework
     * rebuilds that section of every menu created from the description, on the EDT, in the scope the menu was built in.
     *
     * @param entries the entries of the section
     * @return the dynamic section
     */
    static Dynamic dynamic(Observable<? extends List<? extends DynamicEntry>> entries) {
        return new Dynamic(entries);
    }

    /**
     * A section of a menu whose entries come from an observable.
     *
     * @param entries the entries of the section
     */
    record Dynamic(Observable<? extends List<? extends DynamicEntry>> entries) implements MenuEntry {

        /**
         * Creates a dynamic section.
         *
         * @param entries the entries of the section
         */
        public Dynamic {
            Objects.requireNonNull(entries, "entries");
        }
    }
}
